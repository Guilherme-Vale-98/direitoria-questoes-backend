package com.direitoria.questoes.admin;

import com.direitoria.questoes.auth.JwtService;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import tools.jackson.databind.ObjectMapper;

/** Request bodies, tokens and seed rows shared by the admin endpoint tests. */
final class AdminFixtures {

    private AdminFixtures() {
    }

    /** A token for a user that need not exist: the admin endpoints never look the caller up. */
    static String bearer(JwtService jwt, String role) {
        return "Bearer " + jwt.issueAccessToken(UUID.randomUUID(), List.of(role));
    }

    static Map<String, Object> ref(int id) {
        return Map.of("id", id);
    }

    static Map<String, Object> named(String name) {
        return Map.of("name", name);
    }

    /** A valid múltipla escolha A–D, gabarito C. Alternative texts derive from the statement, so two statements never collide. */
    static Map<String, Object> mc(String statement, int subjectId, int examBoardId, int agencyId) {
        Map<String, Object> options = new LinkedHashMap<>();
        for (String l : List.of("A", "B", "C", "D")) {
            options.put(l, "Alternativa " + l + " de " + statement);
        }
        options.put("E", null);
        Map<String, Object> m = base(statement, subjectId, examBoardId, agencyId);
        m.put("type", "MULTIPLA_ESCOLHA");
        m.put("options", options);
        m.put("answer", "C");
        return m;
    }

    static Map<String, Object> ce(String statement, int subjectId, int examBoardId, int agencyId) {
        Map<String, Object> m = base(statement, subjectId, examBoardId, agencyId);
        m.put("type", "CERTO_ERRADO");
        m.put("options", Map.of());
        m.put("answer", "CERTO");
        return m;
    }

    private static Map<String, Object> base(String statement, int subjectId, int examBoardId, int agencyId) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("referenceText", null);
        m.put("statement", statement);
        m.put("commentary", "Porque sim.");
        m.put("subjects", List.of(ref(subjectId)));
        m.put("examBoard", ref(examBoardId));
        m.put("agency", ref(agencyId));
        m.put("year", 2024);
        m.put("role", "Analista");
        m.put("difficulty", "MODERADO");
        m.put("images", List.of());
        m.put("confirmRejudge", false);
        return m;
    }

    /** The `questao` part, typed application/json as Spring's @RequestPart needs. */
    static MockMultipartFile json(ObjectMapper mapper, Map<String, Object> body) {
        return new MockMultipartFile("questao", "", MediaType.APPLICATION_JSON_VALUE, mapper.writeValueAsBytes(body));
    }

    /** A pipeline-style row (raw columns only, no canonicals). Returns its public id. */
    static UUID seedMc(JdbcTemplate jdbc, String sourceId, String statement, String gabarito,
                       String a, String b, String c, String d, String e) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO questao (id, source_id, tipo, enunciado, opcao_a, opcao_b, opcao_c, opcao_d,"
                        + " opcao_e, gabarito, banca, orgao, cargo, ano, materia, assunto) VALUES"
                        + " (?, ?, 'MULTIPLA_ESCOLHA'::tipo_questao, ?, ?, ?, ?, ?, ?, ?,"
                        + " 'Banca legada', 'Órgão legado', 'Cargo', 2020, 'Matéria legada', 'Assunto legado')",
                id, sourceId, statement, a, b, c, d, e, gabarito);
        return id;
    }

    static UUID student(JdbcTemplate jdbc) {
        String email = "st_" + UUID.randomUUID() + "@x.com";
        jdbc.update("INSERT INTO users (first_name, last_name, email, password) VALUES ('T', 'U', ?, 'x')", email);
        return jdbc.queryForObject("SELECT id FROM users WHERE email = ?", UUID.class, email);
    }

    static void attempt(JdbcTemplate jdbc, UUID userId, String sourceId, String chosen, boolean correct) {
        jdbc.update("INSERT INTO question_attempt (user_id, question_source_id, chosen_answer, is_correct)"
                + " VALUES (?, ?, ?, ?)", userId, sourceId, chosen, correct);
    }

    static String sourceIdOf(JdbcTemplate jdbc, UUID publicId) {
        return jdbc.queryForObject("SELECT source_id FROM questao WHERE id = ?", String.class, publicId);
    }

    /** A file of `size` bytes starting with a PNG signature. Different sizes give different bytes. */
    static byte[] png(int size) {
        return withMagic(size, new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A});
    }

    static byte[] jpeg(int size) {
        return withMagic(size, new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0});
    }

    static byte[] webp(int size) {
        byte[] b = withMagic(size, "RIFF".getBytes(StandardCharsets.US_ASCII));
        System.arraycopy("WEBP".getBytes(StandardCharsets.US_ASCII), 0, b, 8, 4);
        return b;
    }

    static byte[] svg() {
        return "<svg xmlns=\"http://www.w3.org/2000/svg\" onload=\"alert(1)\"></svg>".getBytes(StandardCharsets.UTF_8);
    }

    static MockMultipartFile image(String fileName, String declaredType, byte[] bytes) {
        return new MockMultipartFile("imagens", fileName, declaredType, bytes);
    }

    private static byte[] withMagic(int size, byte[] magic) {
        byte[] b = new byte[Math.max(size, 16)];
        System.arraycopy(magic, 0, b, 0, magic.length);
        b[b.length - 1] = (byte) size; // so equal-type files of different sizes differ
        return b;
    }
}
