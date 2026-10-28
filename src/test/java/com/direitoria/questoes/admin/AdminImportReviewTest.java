package com.direitoria.questoes.admin;

import com.direitoria.questoes.TestcontainersConfiguration;
import com.direitoria.questoes.auth.JwtService;
import com.direitoria.questoes.domain.Subject;
import com.direitoria.questoes.repository.SubjectRepository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AdminImportReviewTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JwtService jwt;
    @Autowired JdbcTemplate jdbc;
    @Autowired SubjectRepository subjects;

    Subject subject;

    @BeforeEach
    void canonicals() {
        subject = subjects.save(new Subject("Disciplina IR"));
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

    /** Two sections (keys 0 and 1) with the given answers; candidates [0] (suggested) and [1]. */
    private static Map<String, Object> twoSections(Map<String, String> first, Map<String, String> second) {
        Map<String, Object> g = new LinkedHashMap<>();
        g.put("fileName", "gabarito.pdf");
        g.put("sections", List.of(
                Map.of("key", 0, "title", "Cargo 1: A", "answers", first),
                Map.of("key", 1, "title", "Cargo 2: B", "answers", second)));
        g.put("candidates", List.of(Map.of("keys", List.of(0), "score", 20), Map.of("keys", List.of(1), "score", 10)));
        g.put("suggested", 0);
        return g;
    }

    private JsonNode overview(UUID id) throws Exception {
        return json.readTree(mvc.perform(get("/api/admin/imports/{id}", id).header("Authorization", admin()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private JsonNode row(JsonNode overview, int number) {
        for (JsonNode r : overview.get("questions")) {
            if (r.get("number").asInt() == number) {
                return r;
            }
        }
        throw new AssertionError("no row " + number);
    }

    private ResultActions putFields(UUID id, Map<String, Object> body) throws Exception {
        return mvc.perform(put("/api/admin/imports/{id}", id).header("Authorization", admin())
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)));
    }

    private static Map<String, Object> fields(List<Integer> sections, List<Map<String, Object>> ranges) {
        Map<String, Object> f = new HashMap<>();
        f.put("examBoard", null);
        f.put("agency", null);
        f.put("year", null);
        f.put("role", null);
        f.put("gabaritoSections", sections);
        f.put("subjectRanges", ranges);
        return f;
    }

    private ResultActions saveDraft(UUID id, int n, Map<String, Object> draft, MockMultipartFile... files) throws Exception {
        MockMultipartHttpServletRequestBuilder req = multipart(HttpMethod.PUT, "/api/admin/imports/{id}/questions/{n}", id, n);
        req.file(new MockMultipartFile("questao", "", MediaType.APPLICATION_JSON_VALUE, json.writeValueAsBytes(draft)));
        for (MockMultipartFile f : files) {
            req.file(f);
        }
        return mvc.perform(req.header("Authorization", admin()));
    }

    /** The draft of ImportFixtures.mc(n, statement), as the review form would send it. */
    private static Map<String, Object> draft(String statement, String answer, List<Map<String, Object>> subjects) {
        Map<String, Object> options = new LinkedHashMap<>();
        for (String l : List.of("A", "B", "C", "D", "E")) {
            options.put(l, "Alternativa " + l + " de " + statement);
        }
        Map<String, Object> d = new HashMap<>();
        d.put("referenceText", null);
        d.put("statement", statement);
        d.put("options", options);
        d.put("answer", answer);
        d.put("commentary", null);
        d.put("subjects", subjects);
        d.put("difficulty", null);
        d.put("images", null);
        return d;
    }

    private ResultActions include(UUID id, int n, boolean included) throws Exception {
        return mvc.perform(put("/api/admin/imports/{id}/questions/{n}/included", id, n).header("Authorization", admin())
                .contentType(MediaType.APPLICATION_JSON).content("{\"included\":" + included + "}"));
    }

    @Test
    void theOverviewShowsEffectiveAnswersDisciplinasAndPendencias() throws Exception {
        Map<String, Object> r = ImportFixtures.result("MULTIPLA_ESCOLHA", list(
                ImportFixtures.mc(1, "Enunciado O1"), ImportFixtures.mc(2, "Enunciado O2"), ImportFixtures.mc(3, "Enunciado O3")));
        r.put("gabarito", ImportFixtures.gabarito(Map.of("1", "C", "2", "ANULADA", "3", "B")));
        r.put("subjectRanges", List.of(Map.of("from", 1, "to", 2, "subject", Map.of("id", subject.getId()), "hint", "LP")));
        JsonNode o = overview(createImport(r));

        JsonNode q1 = row(o, 1);
        assertThat(q1.get("answer").asText()).isEqualTo("C");
        assertThat(q1.get("answerSource").asText()).isEqualTo("GABARITO");
        assertThat(q1.get("subjects").get(0).get("id").asInt()).isEqualTo(subject.getId());
        assertThat(q1.get("pendencias")).isEmpty();
        assertThat(q1.get("statementPreview").asText()).isEqualTo("Enunciado O1");

        JsonNode q2 = row(o, 2);
        assertThat(q2.get("anulada").asBoolean()).isTrue();
        assertThat(q2.get("included").asBoolean()).isFalse();
        assertThat(q2.get("answer").isNull()).isTrue();
        assertThat(q2.get("pendencias").toString()).contains("SEM_GABARITO");

        assertThat(row(o, 3).get("pendencias").toString()).contains("SEM_DISCIPLINA");
        assertThat(o.get("notices").get("anuladas").asInt()).isEqualTo(1);
        assertThat(o.get("missingFields").toString()).isEqualTo("[\"examBoard\",\"agency\",\"year\",\"role\"]");
        assertThat(o.get("sections").get(0).get("answers").asInt()).isEqualTo(3);
        assertThat(o.get("candidates").get(0).get("suggested").asBoolean()).isTrue();
        assertThat(o.get("gabaritoSections").toString()).isEqualTo("[0]");
        assertThat(o.get("gabaritoFileName").asText()).isEqualTo("gabarito.pdf");
    }

    @Test
    void aNullOverrideFollowsTheSectionWhenItChangesAndAnOverrideStays() throws Exception {
        Map<String, Object> r = ImportFixtures.result("MULTIPLA_ESCOLHA",
                list(ImportFixtures.mc(1, "Enunciado N1"), ImportFixtures.mc(2, "Enunciado N2")));
        r.put("gabarito", twoSections(Map.of("1", "A", "2", "B"), Map.of("1", "C", "2", "D")));
        UUID id = createImport(r);

        // opened and saved unchanged: the form sends answer null (plan Ruling 4)
        saveDraft(id, 1, draft("Enunciado N1", null, null)).andExpect(status().isOk());
        saveDraft(id, 2, draft("Enunciado N2", "E", null)).andExpect(status().isOk());
        putFields(id, fields(List.of(1), List.of())).andExpect(status().isOk());

        JsonNode o = overview(id);
        assertThat(row(o, 1).get("answer").asText()).isEqualTo("C");
        assertThat(row(o, 1).get("answerSource").asText()).isEqualTo("GABARITO");
        assertThat(row(o, 2).get("answer").asText()).isEqualTo("E");
        assertThat(row(o, 2).get("answerSource").asText()).isEqualTo("ADMIN");
    }

    @Test
    void switchingSectionsExcludesOnlyTheQuestoesThatBecomeAnuladas() throws Exception {
        Map<String, Object> r = ImportFixtures.result("MULTIPLA_ESCOLHA", list(
                ImportFixtures.mc(1, "Enunciado S1"), ImportFixtures.mc(2, "Enunciado S2"), ImportFixtures.mc(3, "Enunciado S3")));
        r.put("gabarito", twoSections(Map.of("1", "ANULADA", "2", "A", "3", "B"), Map.of("1", "ANULADA", "2", "ANULADA", "3", "B")));
        UUID id = createImport(r);
        include(id, 1, true).andExpect(status().isNoContent()); // the admin opts the anulada in

        putFields(id, fields(List.of(1), List.of())).andExpect(status().isOk());
        JsonNode o = overview(id);
        assertThat(row(o, 1).get("included").asBoolean()).isTrue(); // was already anulada: the toggle stands
        assertThat(row(o, 2).get("included").asBoolean()).isFalse(); // became anulada: excluded
        assertThat(row(o, 3).get("included").asBoolean()).isTrue();
    }

    @Test
    void switchingBackReIncludesWhatASwitchExcludedAsAnuladaButNeverADuplicate() throws Exception {
        Map<String, Object> r = ImportFixtures.result("MULTIPLA_ESCOLHA", list(
                ImportFixtures.mc(1, "Enunciado W1"), ImportFixtures.mc(2, "Enunciado W2"), ImportFixtures.mc(3, "Enunciado W3"),
                ImportFixtures.mc(4, "Enunciado W3"))); // 4 repeats 3: excluded from the start
        r.put("gabarito", twoSections(Map.of("1", "A", "2", "B", "3", "C", "4", "C"),
                Map.of("1", "ANULADA", "2", "ANULADA", "3", "D", "4", "ANULADA")));
        UUID id = createImport(r);
        include(id, 3, false).andExpect(status().isNoContent()); // the admin's own exclusion

        putFields(id, fields(List.of(1), List.of())).andExpect(status().isOk());
        JsonNode o = overview(id);
        assertThat(row(o, 1).get("included").asBoolean()).isFalse(); // became anulada: excluded
        assertThat(row(o, 2).get("included").asBoolean()).isFalse();
        assertThat(row(o, 3).get("included").asBoolean()).isFalse();
        assertThat(row(o, 4).get("included").asBoolean()).isFalse();

        putFields(id, fields(List.of(0), List.of())).andExpect(status().isOk()); // back to the right section
        o = overview(id);
        assertThat(row(o, 1).get("included").asBoolean()).isTrue(); // no longer anulada: back in
        assertThat(row(o, 2).get("included").asBoolean()).isTrue();
        assertThat(row(o, 3).get("included").asBoolean()).isFalse(); // never anulada: the admin's toggle stands
        assertThat(row(o, 4).get("included").asBoolean()).isFalse(); // a duplicate stays excluded
        assertThat(o.get("notices").get("anuladas").asInt()).isZero();
    }

    @Test
    void noticesFlagAGabaritoPastTheLastQuestaoBeforeTheFirstOrUnreadable() throws Exception {
        Map<String, Object> missed = ImportFixtures.result("MULTIPLA_ESCOLHA",
                list(ImportFixtures.mc(1, "Enunciado M1"), ImportFixtures.mc(2, "Enunciado M2")));
        missed.put("gabarito", ImportFixtures.gabarito(Map.of("1", "A", "2", "B", "3", "C", "4", "D", "5", "E")));
        assertThat(overview(createImport(missed)).get("notices").get("gabaritoLast").asInt()).isEqualTo(5);

        Map<String, Object> partial = ImportFixtures.result("MULTIPLA_ESCOLHA",
                list(ImportFixtures.mc(3, "Enunciado P3"), ImportFixtures.mc(4, "Enunciado P4")));
        partial.put("gabarito", ImportFixtures.gabarito(Map.of("1", "A", "2", "B", "3", "C", "4", "D")));
        JsonNode n = overview(createImport(partial)).get("notices");
        assertThat(n.get("gabaritoFirst").asInt()).isEqualTo(1);
        assertThat(n.get("gabaritoLast").isNull()).isTrue();

        Map<String, Object> unreadable = ImportFixtures.result("MULTIPLA_ESCOLHA", list(ImportFixtures.mc(1, "Enunciado U1")));
        unreadable.put("gabarito", Map.of("fileName", "g.pdf", "sections", List.of(), "candidates", List.of()));
        assertThat(overview(createImport(unreadable)).get("notices").get("gabaritoUnreadable").asBoolean()).isTrue();
    }

    @Test
    void updateFieldsValidatesThenStoresTheImportLevelFields() throws Exception {
        Map<String, Object> r = ImportFixtures.result("MULTIPLA_ESCOLHA", list(ImportFixtures.mc(1, "Enunciado F1")));
        r.put("gabarito", ImportFixtures.gabarito(Map.of("1", "A")));
        UUID id = createImport(r);

        putFields(id, fields(List.of(9), List.of())).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.gabaritoSections").exists());
        putFields(id, fields(List.of(0), List.of(Map.of("from", 1, "to", 5, "hint", "A"), Map.of("from", 5, "to", 9, "hint", "B"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.subjectRanges").exists());

        Map<String, Object> ok = fields(List.of(0), List.of(Map.of("from", 1, "to", 1, "subject", Map.of("id", subject.getId()), "hint", "X")));
        ok.put("examBoard", Map.of("name", "Banca Nova IR"));
        ok.put("agency", Map.of("name", "Órgão Novo IR"));
        ok.put("year", 2024);
        ok.put("role", "  Analista  ");
        putFields(id, ok).andExpect(status().isOk())
                .andExpect(jsonPath("$.examBoard.name").value("Banca Nova IR"))
                .andExpect(jsonPath("$.year").value(2024))
                .andExpect(jsonPath("$.role").value("Analista"))
                .andExpect(jsonPath("$.missingFields").isEmpty())
                .andExpect(jsonPath("$.questions[0].pendencias").isEmpty());
    }

    @Test
    void savingADraftStoresItsTextOverridesAndImages() throws Exception {
        Map<String, Object> q1 = ImportFixtures.mc(1, "Enunciado D1");
        q1.put("figures", List.of(Map.of("part", 0, "page", 1)));
        UUID id = createImport(ImportFixtures.result("MULTIPLA_ESCOLHA", list(q1)), ImportFixtures.figure(AdminFixtures.png(40)));

        Map<String, Object> d = draft("Enunciado D1 editado", "b", List.of(Map.of("id", subject.getId())));
        d.put("images", List.of(Map.of("keep", 0), Map.of("upload", 0)));
        byte[] upload = AdminFixtures.jpeg(50);
        saveDraft(id, 1, d, AdminFixtures.image("nova.jpg", "image/jpeg", upload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statement").value("Enunciado D1 editado"))
                .andExpect(jsonPath("$.answer").value("B"))
                .andExpect(jsonPath("$.effectiveAnswer").value("B"))
                .andExpect(jsonPath("$.subjects[0].id").value(subject.getId()))
                .andExpect(jsonPath("$.images.length()").value(2))
                .andExpect(jsonPath("$.images[1].url").value("/api/admin/imports/" + id + "/questions/1/images/1"));

        mvc.perform(get("/api/admin/imports/{id}/questions/1/images/1", id).header("Authorization", admin()))
                .andExpect(status().isOk())
                .andExpect(content().bytes(upload))
                .andExpect(content().contentType("image/jpeg"));
        assertThat(jdbc.queryForObject("SELECT origem FROM importacao_imagem WHERE importacao_id = ? AND numero = 1 AND ordem = 1",
                String.class, id)).isEqualTo("upload:nova.jpg");
    }

    @Test
    void aDraftEditThatCopiesTheCatalogIsExcludedAndUnflaggedWhenMadeUnique() throws Exception {
        UUID existing = AdminFixtures.seedMc(jdbc, "manual:" + UUID.randomUUID(), "Enunciado Catálogo IR", "A",
                "Alternativa A de Enunciado Catálogo IR", "Alternativa B de Enunciado Catálogo IR",
                "Alternativa C de Enunciado Catálogo IR", "Alternativa D de Enunciado Catálogo IR",
                "Alternativa E de Enunciado Catálogo IR");
        UUID id = createImport(ImportFixtures.result("MULTIPLA_ESCOLHA", list(ImportFixtures.mc(1, "Enunciado Único IR"))));

        saveDraft(id, 1, draft("Enunciado Catálogo IR", null, null)).andExpect(status().isOk())
                .andExpect(jsonPath("$.duplicateOf.catalogId").value(existing.toString()))
                .andExpect(jsonPath("$.included").value(false));
        saveDraft(id, 1, draft("Enunciado Único IR de novo", null, null)).andExpect(status().isOk())
                .andExpect(jsonPath("$.duplicateOf").isEmpty())
                .andExpect(jsonPath("$.included").value(false)); // the admin decides again
    }

    @Test
    void includeTogglesButNeverForADuplicate() throws Exception {
        UUID id = createImport(ImportFixtures.result("MULTIPLA_ESCOLHA",
                list(ImportFixtures.mc(1, "Enunciado T"), ImportFixtures.mc(2, "Enunciado T"))));
        include(id, 1, false).andExpect(status().isNoContent());
        assertThat(row(overview(id), 1).get("included").asBoolean()).isFalse();
        include(id, 2, true).andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.included").exists());
    }

    @Test
    void theQuestionViewNavigatesAndReportsPendencias() throws Exception {
        Map<String, Object> q2 = ImportFixtures.mc(2, "Texto com " + ImportRules.UNREADABLE);
        UUID id = createImport(ImportFixtures.result("MULTIPLA_ESCOLHA",
                list(ImportFixtures.mc(1, "Enunciado V1"), q2, ImportFixtures.mc(3, "Enunciado V3"))));
        mvc.perform(get("/api/admin/imports/{id}/questions/2", id).header("Authorization", admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.previous").value(1))
                .andExpect(jsonPath("$.next").value(3))
                .andExpect(jsonPath("$.type").value("MULTIPLA_ESCOLHA"))
                .andExpect(jsonPath("$.crop[0].page").value(1))
                .andExpect(jsonPath("$.range").isEmpty())
                .andExpect(jsonPath("$.examBoard").isEmpty())
                .andExpect(jsonPath("$.pendencias[?(@.code == 'ILEGIVEL')]").exists());
        mvc.perform(get("/api/admin/imports/{id}/questions/1", id).header("Authorization", admin()))
                .andExpect(jsonPath("$.previous").isEmpty());
    }

    @Test
    void anUnknownImportOrQuestaoIs404() throws Exception {
        UUID missing = UUID.randomUUID();
        mvc.perform(get("/api/admin/imports/{id}", missing).header("Authorization", admin()))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("IMPORT_NOT_FOUND"));
        UUID id = createImport(ImportFixtures.result("MULTIPLA_ESCOLHA", list(ImportFixtures.mc(1, "Enunciado 404"))));
        mvc.perform(get("/api/admin/imports/{id}/questions/99", id).header("Authorization", admin())).andExpect(status().isNotFound());
        include(id, 99, true).andExpect(status().isNotFound());
        putFields(missing, fields(List.of(), List.of())).andExpect(status().isNotFound());
    }

    @Test
    void theReviewEndpointsAreAdminOnly() throws Exception {
        String user = AdminFixtures.bearer(jwt, "USER");
        UUID any = UUID.randomUUID();
        mvc.perform(get("/api/admin/imports/{id}", any).header("Authorization", user)).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/imports/{id}/questions/1", any).header("Authorization", user)).andExpect(status().isForbidden());
        mvc.perform(put("/api/admin/imports/{id}", any).header("Authorization", user)
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isForbidden());
        mvc.perform(put("/api/admin/imports/{id}/questions/1/included", any).header("Authorization", user)
                .contentType(MediaType.APPLICATION_JSON).content("{\"included\":true}")).andExpect(status().isForbidden());
    }
}
