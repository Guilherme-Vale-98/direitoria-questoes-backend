package com.direitoria.questoes.admin;

import com.direitoria.questoes.TestcontainersConfiguration;
import com.direitoria.questoes.auth.JwtService;
import com.direitoria.questoes.domain.Agency;
import com.direitoria.questoes.domain.ExamBoard;
import com.direitoria.questoes.domain.Subject;
import com.direitoria.questoes.repository.AgencyRepository;
import com.direitoria.questoes.repository.ExamBoardRepository;
import com.direitoria.questoes.repository.SubjectRepository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AdminImportCommitTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JwtService jwt;
    @Autowired JdbcTemplate jdbc;
    @Autowired SubjectRepository subjects;
    @Autowired ExamBoardRepository boards;
    @Autowired AgencyRepository agencies;

    Subject subject;
    ExamBoard board;
    Agency agency;

    @BeforeEach
    void canonicals() {
        subject = subjects.save(new Subject("Disciplina CM"));
        board = boards.save(new ExamBoard("Banca CM"));
        agency = agencies.save(new Agency("Órgão CM"));
    }

    private String admin() {
        return AdminFixtures.bearer(jwt, "ADMIN");
    }

    @SafeVarargs
    private static List<Map<String, Object>> list(Map<String, Object>... qs) {
        return new ArrayList<>(List.of(qs));
    }

    private UUID createImport(Map<String, Object> result, MockMultipartFile... figures) throws Exception {
        MockMultipartHttpServletRequestBuilder req = multipart("/api/admin/imports")
                .file(ImportFixtures.pdfPart(ImportFixtures.pdf()))
                .file(ImportFixtures.json(json, result));
        for (MockMultipartFile f : figures) {
            req.file(f);
        }
        String body = mvc.perform(req.header("Authorization", admin())).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(json.readTree(body).get("id").asText());
    }

    /** Banca/órgão by id, 2024, cargo, section 0, questões 1–3 in the test's disciplina. */
    private Map<String, Object> completeFields() {
        Map<String, Object> f = new HashMap<>();
        f.put("examBoard", Map.of("id", board.getId()));
        f.put("agency", Map.of("id", agency.getId()));
        f.put("year", 2024);
        f.put("role", "Analista");
        f.put("gabaritoSections", List.of(0));
        f.put("subjectRanges", List.of(Map.of("from", 1, "to", 3, "subject", Map.of("id", subject.getId()), "hint", "X")));
        return f;
    }

    private void putFields(UUID id, Map<String, Object> fields) throws Exception {
        mvc.perform(put("/api/admin/imports/{id}", id).header("Authorization", admin())
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(fields))).andExpect(status().isOk());
    }

    private void exclude(UUID id, int n) throws Exception {
        mvc.perform(put("/api/admin/imports/{id}/questions/{n}/included", id, n).header("Authorization", admin())
                .contentType(MediaType.APPLICATION_JSON).content("{\"included\":false}")).andExpect(status().isNoContent());
    }

    private ResultActions commit(UUID id) throws Exception {
        return mvc.perform(post("/api/admin/imports/{id}/commit", id).header("Authorization", admin()));
    }

    private long pdfRows() {
        return jdbc.queryForObject("SELECT count(*) FROM questao WHERE source_id LIKE 'pdf:%'", Long.class);
    }

    private Map<String, Object> threeQuestoes(Map<String, String> answers) {
        Map<String, Object> r = ImportFixtures.result("MULTIPLA_ESCOLHA", list(
                ImportFixtures.mc(1, "Enunciado K1"), ImportFixtures.mc(2, "Enunciado K2"), ImportFixtures.mc(3, "Enunciado K3")));
        r.put("gabarito", ImportFixtures.gabarito(answers));
        return r;
    }

    @Test
    void commitCreatesTheIncludedQuestoesThroughThreeBsPathAndDeletesTheImport() throws Exception {
        Map<String, Object> r = threeQuestoes(Map.of("1", "C", "2", "A", "3", "B"));
        @SuppressWarnings("unchecked")
        Map<String, Object> q1 = ((List<Map<String, Object>>) r.get("questions")).get(0);
        q1.put("figures", List.of(Map.of("part", 0, "page", 2)));
        byte[] figure = AdminFixtures.png(72);
        UUID id = createImport(r, ImportFixtures.figure(figure));
        putFields(id, completeFields());
        exclude(id, 2);
        long before = pdfRows();

        commit(id).andExpect(status().isCreated()).andExpect(jsonPath("$.created").value(2));

        assertThat(pdfRows()).isEqualTo(before + 2);
        Map<String, Object> k1 = jdbc.queryForMap("SELECT source_id, gabarito, banca, orgao, materia, cargo, ano, banca_id, orgao_id"
                + " FROM questao WHERE enunciado = 'Enunciado K1'");
        assertThat((String) k1.get("source_id")).startsWith("pdf:");
        assertThat(k1).containsEntry("gabarito", "C").containsEntry("banca", "Banca CM").containsEntry("orgao", "Órgão CM")
                .containsEntry("materia", "Disciplina CM").containsEntry("cargo", "Analista");
        assertThat(((Number) k1.get("ano")).intValue()).isEqualTo(2024);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM questao WHERE enunciado = 'Enunciado K2'", Long.class)).isZero();
        Map<String, Object> asset = jdbc.queryForMap("SELECT a.origem_url, a.bytes FROM questao_asset a"
                + " JOIN questao q ON q.source_id = a.source_id WHERE q.enunciado = 'Enunciado K1'");
        assertThat(asset).containsEntry("origem_url", "pdf:prova.pdf#p2");
        assertThat((byte[]) asset.get("bytes")).isEqualTo(figure);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM importacao WHERE id = ?", Long.class, id)).isZero();
        mvc.perform(get("/api/admin/imports/{id}", id).header("Authorization", admin())).andExpect(status().isNotFound());
    }

    @Test
    void aCanonicalCreatedByNameIsCreatedOnceForEveryQuestao() throws Exception {
        UUID id = createImport(threeQuestoes(Map.of("1", "C", "2", "A", "3", "B")));
        Map<String, Object> f = completeFields();
        f.put("examBoard", Map.of("name", "Banca Única CM"));
        putFields(id, f);

        commit(id).andExpect(status().isCreated()).andExpect(jsonPath("$.created").value(3));

        assertThat(jdbc.queryForObject("SELECT count(*) FROM banca WHERE nome = 'Banca Única CM'", Long.class)).isEqualTo(1L);
        assertThat(jdbc.queryForObject("SELECT count(DISTINCT banca_id) FROM questao WHERE enunciado LIKE 'Enunciado K_'",
                Long.class)).isEqualTo(1L);
    }

    @Test
    void anyProblemBlocksTheWholeCommitAndEveryProblemIsListed() throws Exception {
        Map<String, Object> r = ImportFixtures.result("MULTIPLA_ESCOLHA", list(
                ImportFixtures.mc(1, "Enunciado B1"),
                ImportFixtures.mc(2, "Texto com " + ImportRules.UNREADABLE),
                ImportFixtures.mc(3, "Enunciado B3")));
        r.put("gabarito", ImportFixtures.gabarito(Map.of("1", "A", "2", "B")));
        UUID id = createImport(r);
        Map<String, Object> f = completeFields();
        f.put("year", null);
        f.put("role", "   "); // blank: stored as no cargo
        putFields(id, f);
        long before = pdfRows();

        commit(id).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("COMMIT_BLOCKED"))
                .andExpect(jsonPath("$.problems[?(@.number == null && @.field == 'year')]").exists())
                .andExpect(jsonPath("$.problems[?(@.number == null && @.field == 'role' && @.message == 'Informe o cargo.')]").exists())
                .andExpect(jsonPath("$.problems[?(@.number == 2 && @.field == 'statement')]").exists())
                .andExpect(jsonPath("$.problems[?(@.number == 3 && @.field == 'answer')]").exists());

        assertThat(pdfRows()).isEqualTo(before);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM importacao WHERE id = ?", Long.class, id)).isEqualTo(1L);
    }

    @Test
    void aQuestaoThatReachedTheCatalogAfterReviewBlocksTheCommit() throws Exception {
        UUID id = createImport(threeQuestoes(Map.of("1", "C", "2", "A", "3", "B")));
        putFields(id, completeFields());
        AdminFixtures.seedMc(jdbc, "manual:" + UUID.randomUUID(), "Enunciado K2", "A",
                "Alternativa A de Enunciado K2", "Alternativa B de Enunciado K2", "Alternativa C de Enunciado K2",
                "Alternativa D de Enunciado K2", "Alternativa E de Enunciado K2");
        long before = pdfRows();

        commit(id).andExpect(status().isConflict())
                .andExpect(jsonPath("$.problems[?(@.number == 2 && @.field == 'statement')]").exists());
        assertThat(pdfRows()).isEqualTo(before);
    }

    @Test
    void aSecondCommitFindsTheImportGoneAndCreatesNothingTwice() throws Exception {
        UUID id = createImport(threeQuestoes(Map.of("1", "C", "2", "A", "3", "B")));
        putFields(id, completeFields());
        long before = pdfRows();
        commit(id).andExpect(status().isCreated());
        commit(id).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("IMPORT_NOT_FOUND"));
        assertThat(pdfRows()).isEqualTo(before + 3);
    }

    @Test
    void anImportWithNothingIncludedCannotBeCommitted() throws Exception {
        UUID id = createImport(threeQuestoes(Map.of("1", "C", "2", "A", "3", "B")));
        putFields(id, completeFields());
        exclude(id, 1);
        exclude(id, 2);
        exclude(id, 3);
        commit(id).andExpect(status().isConflict())
                .andExpect(jsonPath("$.problems[?(@.number == null && @.field == 'included')]").exists());
    }

    @Test
    void theCommitIsAdminOnly() throws Exception {
        mvc.perform(post("/api/admin/imports/{id}/commit", UUID.randomUUID())
                .header("Authorization", AdminFixtures.bearer(jwt, "USER"))).andExpect(status().isForbidden());
    }
}
