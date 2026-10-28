package com.direitoria.questoes.admin;

import com.direitoria.questoes.TestcontainersConfiguration;
import com.direitoria.questoes.auth.JwtService;
import com.direitoria.questoes.domain.Agency;
import com.direitoria.questoes.domain.ExamBoard;
import com.direitoria.questoes.domain.Subject;
import com.direitoria.questoes.repository.AgencyRepository;
import com.direitoria.questoes.repository.ExamBoardRepository;
import com.direitoria.questoes.repository.SubjectRepository;
import java.util.LinkedHashMap;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AdminQuestionEditTest {

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
        subject = subjects.save(new Subject("Disciplina E"));
        board = boards.save(new ExamBoard("Banca E"));
        agency = agencies.save(new Agency("Órgão E"));
    }

    private Map<String, Object> mc(String statement) {
        return AdminFixtures.mc(statement, subject.getId(), board.getId(), agency.getId());
    }

    private String admin() {
        return AdminFixtures.bearer(jwt, "ADMIN");
    }

    private UUID create(Map<String, Object> body) throws Exception {
        String res = mvc.perform(multipart("/api/admin/questions").file(AdminFixtures.json(json, body))
                        .header("Authorization", admin()))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return UUID.fromString(json.readTree(res).get("id").asText());
    }

    private ResultActions put(UUID id, Map<String, Object> body, String role) throws Exception {
        return mvc.perform(multipart(HttpMethod.PUT, "/api/admin/questions/{id}", id)
                .file(AdminFixtures.json(json, body))
                .header("Authorization", AdminFixtures.bearer(jwt, role)));
    }

    private String sid(UUID id) {
        return AdminFixtures.sourceIdOf(jdbc, id);
    }

    private Boolean verdict(UUID student, String sourceId) {
        return jdbc.queryForObject("SELECT is_correct FROM question_attempt WHERE user_id = ? AND question_source_id = ?",
                Boolean.class, student, sourceId);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> options(Map<String, Object> body) {
        return (Map<String, Object>) body.get("options");
    }

    @Test
    void withoutTentativasEverythingMayChangeIncludingTheType() throws Exception {
        UUID id = create(mc("Enunciado E1"));
        Map<String, Object> ce = AdminFixtures.ce("Enunciado E1", subject.getId(), board.getId(), agency.getId());
        ce.put("answer", "ERRADO");

        put(id, ce, "ADMIN").andExpect(status().isOk()).andExpect(jsonPath("$.rejudged").value(0))
                .andExpect(jsonPath("$.id").value(id.toString()));

        Map<String, Object> row = jdbc.queryForMap("SELECT tipo::text AS tipo, opcao_a, gabarito FROM questao WHERE id = ?", id);
        assertThat(row.get("tipo")).isEqualTo("CERTO_ERRADO");
        assertThat(row.get("opcao_a")).isNull();
        assertThat(row.get("gabarito")).isEqualTo("ERRADO");
    }

    @Test
    void getReportsTentativasStudentsAndLocks() throws Exception {
        UUID id = create(mc("Enunciado E2"));
        UUID s1 = AdminFixtures.student(jdbc);
        UUID s2 = AdminFixtures.student(jdbc);
        AdminFixtures.attempt(jdbc, s1, sid(id), "A", false);
        AdminFixtures.attempt(jdbc, s1, sid(id), "C", true);
        AdminFixtures.attempt(jdbc, s2, sid(id), "B", false);

        mvc.perform(get("/api/admin/questions/{id}", id).header("Authorization", admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.attemptCount").value(3))
                .andExpect(jsonPath("$.studentCount").value(2))
                .andExpect(jsonPath("$.locked.type").value(true))
                .andExpect(jsonPath("$.locked.optionCount").value(true));
    }

    @Test
    void onceAnsweredTheTypeIsLocked() throws Exception {
        UUID id = create(mc("Enunciado E3"));
        AdminFixtures.attempt(jdbc, AdminFixtures.student(jdbc), sid(id), "C", true);

        put(id, AdminFixtures.ce("Enunciado E3", subject.getId(), board.getId(), agency.getId()), "ADMIN")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.type").exists());
        assertThat(jdbc.queryForObject("SELECT tipo::text FROM questao WHERE id = ?", String.class, id))
                .isEqualTo("MULTIPLA_ESCOLHA");
    }

    @Test
    void onceAnsweredAnAlternativeCannotBeAdded() throws Exception {
        UUID id = create(mc("Enunciado E4"));
        AdminFixtures.attempt(jdbc, AdminFixtures.student(jdbc), sid(id), "C", true);
        Map<String, Object> body = mc("Enunciado E4");
        options(body).put("E", "Uma quinta alternativa");

        put(id, body, "ADMIN")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors['options.E']").value(containsString("adicionar")));
    }

    @Test
    void onceAnsweredAlternativeTextMayStillChange() throws Exception {
        UUID id = create(mc("Enunciado E5"));
        UUID s = AdminFixtures.student(jdbc);
        AdminFixtures.attempt(jdbc, s, sid(id), "B", false);
        Map<String, Object> body = mc("Enunciado E5");
        options(body).put("B", "Texto novo da B");

        put(id, body, "ADMIN").andExpect(status().isOk()).andExpect(jsonPath("$.rejudged").value(0));
        assertThat(jdbc.queryForObject("SELECT opcao_b FROM questao WHERE id = ?", String.class, id))
                .isEqualTo("Texto novo da B");
        assertThat(verdict(s, sid(id))).isFalse();
    }

    @Test
    void aGabaritoChangeWithoutConfirmationIs409AndChangesNothing() throws Exception {
        UUID id = create(mc("Enunciado E6")); // gabarito C
        UUID right = AdminFixtures.student(jdbc);
        UUID wrong = AdminFixtures.student(jdbc);
        AdminFixtures.attempt(jdbc, right, sid(id), "C", true);
        AdminFixtures.attempt(jdbc, wrong, sid(id), "A", false);
        Map<String, Object> body = mc("Enunciado E6 revisto");
        body.put("answer", "A");

        put(id, body, "ADMIN")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REJUDGE_CONFIRMATION_REQUIRED"))
                .andExpect(jsonPath("$.attemptCount").value(2))
                .andExpect(jsonPath("$.studentCount").value(2))
                .andExpect(jsonPath("$.message").value("Isso vai recalcular 2 tentativas de 2 alunos. Continuar?"));

        Map<String, Object> row = jdbc.queryForMap("SELECT gabarito, enunciado FROM questao WHERE id = ?", id);
        assertThat(row.get("gabarito")).isEqualTo("C");
        assertThat(row.get("enunciado")).isEqualTo("Enunciado E6");
        assertThat(verdict(right, sid(id))).isTrue();
        assertThat(verdict(wrong, sid(id))).isFalse();
    }

    @Test
    void aConfirmedGabaritoChangeRejudgesEveryTentativa() throws Exception {
        UUID id = create(mc("Enunciado E7"));
        UUID wasRight = AdminFixtures.student(jdbc);
        UUID wasWrong = AdminFixtures.student(jdbc);
        AdminFixtures.attempt(jdbc, wasRight, sid(id), "C", true);
        AdminFixtures.attempt(jdbc, wasWrong, sid(id), "A", false);
        Map<String, Object> body = mc("Enunciado E7");
        body.put("answer", "A");
        body.put("confirmRejudge", true);

        put(id, body, "ADMIN").andExpect(status().isOk()).andExpect(jsonPath("$.rejudged").value(2));

        assertThat(jdbc.queryForObject("SELECT gabarito FROM questao WHERE id = ?", String.class, id)).isEqualTo("A");
        assertThat(verdict(wasRight, sid(id))).isFalse();
        assertThat(verdict(wasWrong, sid(id))).isTrue();
    }

    @Test
    void rejudgedCountsOnlyVerdictsThatFlipped() throws Exception {
        UUID id = create(mc("Enunciado E8")); // gabarito C
        UUID onC = AdminFixtures.student(jdbc);
        UUID onA = AdminFixtures.student(jdbc);
        UUID onB = AdminFixtures.student(jdbc);
        AdminFixtures.attempt(jdbc, onC, sid(id), "C", true);
        AdminFixtures.attempt(jdbc, onA, sid(id), "A", false);
        AdminFixtures.attempt(jdbc, onB, sid(id), "B", false);
        Map<String, Object> body = mc("Enunciado E8");
        body.put("answer", "A");
        body.put("confirmRejudge", true);

        // C: right → wrong, A: wrong → right, B: wrong → still wrong.
        put(id, body, "ADMIN").andExpect(status().isOk()).andExpect(jsonPath("$.rejudged").value(2));
        assertThat(verdict(onB, sid(id))).isFalse();
    }

    @Test
    void keepingTheGabaritoNeedsNoConfirmation() throws Exception {
        UUID id = create(mc("Enunciado E9"));
        AdminFixtures.attempt(jdbc, AdminFixtures.student(jdbc), sid(id), "C", true);
        Map<String, Object> body = mc("Enunciado E9");
        body.put("commentary", "Comentário melhorado.");

        put(id, body, "ADMIN").andExpect(status().isOk()).andExpect(jsonPath("$.rejudged").value(0));
    }

    @Test
    void theAnswerEndpointAgreesWithTheRejudgedVerdict() throws Exception {
        UUID id = create(mc("Enunciado E10"));
        UUID s = AdminFixtures.student(jdbc);
        AdminFixtures.attempt(jdbc, s, sid(id), "A", false);
        Map<String, Object> body = mc("Enunciado E10");
        body.put("answer", "A");
        body.put("confirmRejudge", true);
        put(id, body, "ADMIN").andExpect(status().isOk());

        // Same letter, judged live by /answer, must get the verdict re-judging stored.
        // (Its own recording fails silently here — AttemptService runs REQUIRES_NEW and
        // cannot see this test's uncommitted questão — but the verdict is what matters.)
        mvc.perform(post("/api/questions/{id}/answer", id)
                        .header("Authorization", AdminFixtures.bearer(jwt, "USER"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"chosenAnswer\":\"a\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.correct").value(true));
        assertThat(verdict(s, sid(id))).isTrue();
    }

    @Test
    void anEditIdenticalToAnotherQuestionIs409WithItsId() throws Exception {
        UUID first = create(mc("Enunciado E11a"));
        UUID second = create(mc("Enunciado E11b"));

        put(second, mc("Enunciado E11a"), "ADMIN")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE"))
                .andExpect(jsonPath("$.existingId").value(first.toString()));
    }

    @Test
    void savingAQuestionUnchangedIsNotADuplicateOfItself() throws Exception {
        UUID id = create(mc("Enunciado E12"));
        put(id, mc("Enunciado E12"), "ADMIN").andExpect(status().isOk());
    }

    @Test
    void anUnknownIdIs404() throws Exception {
        put(UUID.randomUUID(), mc("Enunciado E13"), "ADMIN").andExpect(status().isNotFound());
    }

    @Test
    void anAnsweredLegacyRowWithIrregularLettersStaysEditable() throws Exception {
        // Review Focus 1: 11 catalog rows look like this (e.g. only A, D and E).
        UUID id = AdminFixtures.seedMc(jdbc, "legacy_e14", "Enunciado legado E14", "D",
                "Alfa", null, null, "Delta", "Épsilon");
        AdminFixtures.attempt(jdbc, AdminFixtures.student(jdbc), "legacy_e14", "D", true);
        Map<String, Object> body = mc("Enunciado legado E14 corrigido");
        Map<String, Object> opts = new LinkedHashMap<>();
        opts.put("A", "Alfa");
        opts.put("B", null);
        opts.put("C", null);
        opts.put("D", "Delta");
        opts.put("E", "Épsilon");
        body.put("options", opts);
        body.put("answer", "D");

        put(id, body, "ADMIN").andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT enunciado FROM questao WHERE id = ?", String.class, id))
                .isEqualTo("Enunciado legado E14 corrigido");

        opts.put("B", "Beta");
        put(id, body, "ADMIN")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors['options.B']").value(containsString("adicionar")));
    }

    @Test
    void anUnansweredLegacyRowMustBeCompletedToAD() throws Exception {
        UUID id = AdminFixtures.seedMc(jdbc, "legacy_e15", "Enunciado legado E15", "D",
                "Alfa", null, null, "Delta", "Épsilon");
        Map<String, Object> body = mc("Enunciado legado E15");
        Map<String, Object> opts = new LinkedHashMap<>();
        opts.put("A", "Alfa");
        opts.put("B", null);
        opts.put("C", null);
        opts.put("D", "Delta");
        opts.put("E", "Épsilon");
        body.put("options", opts);
        body.put("answer", "D");

        put(id, body, "ADMIN")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors['options.B']").exists())
                .andExpect(jsonPath("$.fieldErrors['options.C']").exists());

        opts.put("B", "Beta");
        opts.put("C", "Gama");
        put(id, body, "ADMIN").andExpect(status().isOk());
    }

    @Test
    void rawColumnsFollowTheEditButAssuntoStays() throws Exception {
        UUID id = AdminFixtures.seedMc(jdbc, "legacy_e16", "Enunciado legado E16", "A",
                "Alfa", "Beta", "Gama", "Delta", null);
        Map<String, Object> body = mc("Enunciado legado E16");
        body.put("answer", "A");

        put(id, body, "ADMIN").andExpect(status().isOk());
        Map<String, Object> row = jdbc.queryForMap(
                "SELECT banca, orgao, materia, assunto, banca_id, orgao_id FROM questao WHERE id = ?", id);
        assertThat(row.get("banca")).isEqualTo("Banca E");
        assertThat(row.get("orgao")).isEqualTo("Órgão E");
        assertThat(row.get("materia")).isEqualTo("Disciplina E");
        assertThat(row.get("assunto")).isEqualTo("Assunto legado");
        assertThat(row.get("banca_id")).isEqualTo(board.getId());
        assertThat(row.get("orgao_id")).isEqualTo(agency.getId());
    }

    @Test
    void nonAdminsCannotEdit() throws Exception {
        UUID id = create(mc("Enunciado E17"));
        put(id, mc("Enunciado E17"), "USER").andExpect(status().isForbidden());
    }
}
