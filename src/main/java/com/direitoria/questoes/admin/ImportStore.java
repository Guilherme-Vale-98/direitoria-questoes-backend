package com.direitoria.questoes.admin;

import com.direitoria.questoes.admin.dto.LookupRef;
import com.direitoria.questoes.domain.QuestionType;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * JDBC over importacao, importacao_questao and importacao_imagem (migration 0009). No JPA:
 * the rows are transient drafts shaped like JSON, and ddl-auto=validate never sees them.
 * Runs inside the caller's @Transactional — the JPA transaction manager shares its
 * connection with JDBC, so a commit's questões and the importação's deletion are atomic.
 */
@Component
class ImportStore {

    record ImportRow(UUID id, OffsetDateTime createdAt, String fileName, QuestionType type, int pageCount,
                     ImportJson.Gabarito gabarito, List<Integer> sections, LookupRef examBoard, LookupRef agency,
                     Integer year, String role, List<ImportJson.Range> ranges) {
    }

    record QuestionRow(int number, boolean included, ImportJson.Draft draft, List<ImportJson.Crop> crop,
                       List<String> flags, UUID duplicateOfId, Integer duplicateOfNumero) {

        boolean duplicate() {
            return duplicateOfId != null || duplicateOfNumero != null;
        }
    }

    record StoredImage(int ordem, String contentType, byte[] bytes, String origem) {
    }

    record PdfFile(String fileName, byte[] bytes) {
    }

    private static final TypeReference<List<Integer>> INTS = new TypeReference<>() {
    };
    private static final TypeReference<List<ImportJson.Range>> RANGES = new TypeReference<>() {
    };
    private static final TypeReference<List<ImportJson.Crop>> CROPS = new TypeReference<>() {
    };
    private static final TypeReference<List<String>> STRINGS = new TypeReference<>() {
    };

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper json;

    ImportStore(NamedParameterJdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    UUID insertImport(String fileName, QuestionType type, int pageCount, byte[] pdf, ImportJson.Gabarito gabarito,
                      List<Integer> sections, List<ImportJson.Range> ranges) {
        return jdbc.queryForObject("""
                INSERT INTO importacao (file_name, tipo, page_count, pdf, gabarito, gabarito_sections, subject_ranges)
                VALUES (:fileName, CAST(:tipo AS tipo_questao), :pageCount, :pdf, CAST(:gabarito AS jsonb),
                        CAST(:sections AS jsonb), CAST(:ranges AS jsonb))
                RETURNING id
                """, new MapSqlParameterSource()
                .addValue("fileName", fileName)
                .addValue("tipo", type.name())
                .addValue("pageCount", pageCount)
                .addValue("pdf", pdf, Types.BINARY)
                .addValue("gabarito", toJson(gabarito), Types.VARCHAR)
                .addValue("sections", toJson(sections))
                .addValue("ranges", toJson(ranges)), UUID.class);
    }

    void insertQuestion(UUID id, int number, ImportJson.Draft draft, List<ImportJson.Crop> crop, List<String> flags) {
        jdbc.update("""
                INSERT INTO importacao_questao (importacao_id, numero, draft, crop, flags)
                VALUES (:id, :numero, CAST(:draft AS jsonb), CAST(:crop AS jsonb), CAST(:flags AS jsonb))
                """, new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("numero", number)
                .addValue("draft", toJson(draft))
                .addValue("crop", toJson(crop))
                .addValue("flags", toJson(flags)));
    }

    void insertImage(UUID id, int number, int ordem, String contentType, byte[] bytes, String origem) {
        jdbc.update("""
                INSERT INTO importacao_imagem (importacao_id, numero, ordem, content_type, bytes, origem)
                VALUES (:id, :numero, :ordem, :contentType, :bytes, :origem)
                """, new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("numero", number)
                .addValue("ordem", ordem)
                .addValue("contentType", contentType)
                .addValue("bytes", bytes, Types.BINARY)
                .addValue("origem", origem));
    }

    List<ImportViews.Summary> summaries() {
        return jdbc.query("""
                SELECT i.id, i.file_name, i.tipo::text AS tipo, i.created_at,
                       count(q.numero) AS found, count(q.numero) FILTER (WHERE q.included) AS included
                FROM importacao i LEFT JOIN importacao_questao q ON q.importacao_id = i.id
                GROUP BY i.id, i.file_name, i.tipo, i.created_at
                ORDER BY i.created_at DESC
                """, (rs, n) -> new ImportViews.Summary(rs.getObject("id", UUID.class), rs.getString("file_name"),
                rs.getString("tipo"), rs.getObject("created_at", OffsetDateTime.class), rs.getInt("found"),
                rs.getInt("included")));
    }

    Optional<ImportRow> find(UUID id) {
        return jdbc.query("""
                SELECT id, created_at, file_name, tipo::text AS tipo, page_count, gabarito::text AS gabarito,
                       gabarito_sections::text AS sections, exam_board::text AS exam_board, agency::text AS agency,
                       year, role, subject_ranges::text AS ranges
                FROM importacao WHERE id = :id
                """, Map.of("id", id), (rs, n) -> new ImportRow(rs.getObject("id", UUID.class),
                rs.getObject("created_at", OffsetDateTime.class), rs.getString("file_name"),
                QuestionType.valueOf(rs.getString("tipo")), rs.getInt("page_count"),
                read(rs.getString("gabarito"), ImportJson.Gabarito.class), read(rs.getString("sections"), INTS),
                read(rs.getString("exam_board"), LookupRef.class), read(rs.getString("agency"), LookupRef.class),
                rs.getObject("year", Integer.class), rs.getString("role"), read(rs.getString("ranges"), RANGES)))
                .stream().findFirst();
    }

    /** Row-locks the importação until the transaction ends. False when it does not exist. */
    boolean lock(UUID id) {
        return !jdbc.query("SELECT id FROM importacao WHERE id = :id FOR UPDATE", Map.of("id", id), (rs, n) -> 1)
                .isEmpty();
    }

    List<QuestionRow> questions(UUID id) {
        return jdbc.query("""
                SELECT numero, included, draft::text AS draft, crop::text AS crop, flags::text AS flags,
                       duplicate_of_id, duplicate_of_numero
                FROM importacao_questao WHERE importacao_id = :id ORDER BY numero
                """, Map.of("id", id), (rs, n) -> question(rs));
    }

    Optional<QuestionRow> question(UUID id, int number) {
        return jdbc.query("""
                SELECT numero, included, draft::text AS draft, crop::text AS crop, flags::text AS flags,
                       duplicate_of_id, duplicate_of_numero
                FROM importacao_questao WHERE importacao_id = :id AND numero = :numero
                """, Map.of("id", id, "numero", number), (rs, n) -> question(rs)).stream().findFirst();
    }

    void updateFields(UUID id, ImportJson.FieldsRequest f) {
        jdbc.update("""
                UPDATE importacao SET exam_board = CAST(:examBoard AS jsonb), agency = CAST(:agency AS jsonb),
                       year = CAST(:year AS smallint), role = :role, gabarito_sections = CAST(:sections AS jsonb),
                       subject_ranges = CAST(:ranges AS jsonb), updated_at = now()
                WHERE id = :id
                """, new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("examBoard", toJson(f.examBoard()), Types.VARCHAR)
                .addValue("agency", toJson(f.agency()), Types.VARCHAR)
                .addValue("year", f.year(), Types.INTEGER)
                .addValue("role", f.role() == null || f.role().isBlank() ? null : f.role().strip(), Types.VARCHAR)
                .addValue("sections", toJson(ImportValidator.orEmpty(f.gabaritoSections())))
                .addValue("ranges", toJson(ImportValidator.orEmpty(f.subjectRanges()))));
    }

    void updateDraft(UUID id, int number, ImportJson.Draft draft) {
        jdbc.update("""
                UPDATE importacao_questao SET draft = CAST(:draft AS jsonb), updated_at = now()
                WHERE importacao_id = :id AND numero = :numero
                """, new MapSqlParameterSource().addValue("id", id).addValue("numero", number)
                .addValue("draft", toJson(draft)));
        touch(id);
    }

    void setIncluded(UUID id, int number, boolean included) {
        jdbc.update("""
                UPDATE importacao_questao SET included = :included, updated_at = now()
                WHERE importacao_id = :id AND numero = :numero
                """, Map.of("id", id, "numero", number, "included", included));
    }

    void setIncluded(UUID id, Collection<Integer> numbers, boolean included) {
        if (numbers.isEmpty()) {
            return;
        }
        jdbc.update("""
                UPDATE importacao_questao SET included = :included, updated_at = now()
                WHERE importacao_id = :id AND numero IN (:numbers)
                """, Map.of("id", id, "numbers", numbers, "included", included));
    }

    void setDuplicate(UUID id, int number, UUID catalogId, Integer duplicateNumber) {
        jdbc.update("""
                UPDATE importacao_questao SET duplicate_of_id = :catalogId, duplicate_of_numero = CAST(:num AS smallint)
                WHERE importacao_id = :id AND numero = :numero
                """, new MapSqlParameterSource().addValue("id", id).addValue("numero", number)
                .addValue("catalogId", catalogId, Types.OTHER).addValue("num", duplicateNumber, Types.INTEGER));
    }

    /** Each draft's content key, computed by the database's own q_content_key — the catalog's duplicate rule. */
    Map<Integer, String> contentKeys(UUID id) {
        Map<Integer, String> out = new LinkedHashMap<>();
        jdbc.query("""
                SELECT numero, q_content_key(draft->>'statement', draft->'options'->>'A', draft->'options'->>'B',
                                             draft->'options'->>'C', draft->'options'->>'D', draft->'options'->>'E') AS k
                FROM importacao_questao WHERE importacao_id = :id ORDER BY numero
                """, Map.of("id", id), (ResultSet rs) -> {
            out.put(rs.getInt("numero"), rs.getString("k"));
        });
        return out;
    }

    Optional<PdfFile> pdf(UUID id) {
        return jdbc.query("SELECT file_name, pdf FROM importacao WHERE id = :id", Map.of("id", id),
                (rs, n) -> new PdfFile(rs.getString("file_name"), rs.getBytes("pdf"))).stream().findFirst();
    }

    List<Integer> imageOrdens(UUID id, int number) {
        return jdbc.queryForList("""
                SELECT CAST(ordem AS integer) FROM importacao_imagem
                WHERE importacao_id = :id AND numero = :numero ORDER BY ordem
                """, Map.of("id", id, "numero", number), Integer.class);
    }

    List<StoredImage> images(UUID id, int number) {
        return jdbc.query("""
                SELECT ordem, content_type, bytes, origem FROM importacao_imagem
                WHERE importacao_id = :id AND numero = :numero ORDER BY ordem
                """, Map.of("id", id, "numero", number), (rs, n) -> new StoredImage(rs.getInt("ordem"),
                rs.getString("content_type"), rs.getBytes("bytes"), rs.getString("origem")));
    }

    Optional<StoredImage> image(UUID id, int number, int ordem) {
        return jdbc.query("""
                SELECT ordem, content_type, bytes, origem FROM importacao_imagem
                WHERE importacao_id = :id AND numero = :numero AND ordem = :ordem
                """, Map.of("id", id, "numero", number, "ordem", ordem), (rs, n) -> new StoredImage(rs.getInt("ordem"),
                rs.getString("content_type"), rs.getBytes("bytes"), rs.getString("origem"))).stream().findFirst();
    }

    /**
     * Writes a draft's final image order (3B's slots). Kept rows are parked on negative
     * ordens first: uq_importacao_imagem_ordem is checked per statement, as in 3B.
     */
    void applyImages(UUID id, int number, List<QuestionImages.Slot> slots) {
        Set<Integer> kept = new HashSet<>();
        for (QuestionImages.Slot s : slots) {
            if (s.keep() != null) {
                kept.add(s.keep());
            }
        }
        Map<String, Object> key = Map.of("id", id, "numero", number);
        for (Integer ordem : imageOrdens(id, number)) {
            if (!kept.contains(ordem)) {
                jdbc.update("DELETE FROM importacao_imagem WHERE importacao_id = :id AND numero = :numero AND ordem = :ordem",
                        Map.of("id", id, "numero", number, "ordem", ordem));
            }
        }
        jdbc.update("UPDATE importacao_imagem SET ordem = -1 - ordem WHERE importacao_id = :id AND numero = :numero AND ordem >= 0",
                key);
        for (int pos = 0; pos < slots.size(); pos++) {
            QuestionImages.Slot s = slots.get(pos);
            if (s.keep() != null) {
                jdbc.update("""
                        UPDATE importacao_imagem SET ordem = :to
                        WHERE importacao_id = :id AND numero = :numero AND ordem = :parked
                        """, Map.of("id", id, "numero", number, "to", pos, "parked", -1 - s.keep()));
            } else {
                insertImage(id, number, pos, s.upload().contentType(), s.upload().bytes(), s.upload().origin());
            }
        }
        touch(id);
    }

    void touch(UUID id) {
        jdbc.update("UPDATE importacao SET updated_at = now() WHERE id = :id", Map.of("id", id));
    }

    /** Cascades to the questões and images. */
    int delete(UUID id) {
        return jdbc.update("DELETE FROM importacao WHERE id = :id", Map.of("id", id));
    }

    private QuestionRow question(ResultSet rs) throws SQLException {
        return new QuestionRow(rs.getInt("numero"), rs.getBoolean("included"),
                read(rs.getString("draft"), ImportJson.Draft.class), read(rs.getString("crop"), CROPS),
                read(rs.getString("flags"), STRINGS), rs.getObject("duplicate_of_id", UUID.class),
                rs.getObject("duplicate_of_numero", Integer.class));
    }

    private String toJson(Object value) {
        return value == null ? null : json.writeValueAsString(value);
    }

    private <T> T read(String s, Class<T> type) {
        return s == null ? null : json.readValue(s, type);
    }

    private <T> T read(String s, TypeReference<T> type) {
        return s == null ? null : json.readValue(s, type);
    }
}
