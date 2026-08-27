package com.direitoria.questoes;

import com.direitoria.questoes.catalog.QuestionService;
import com.direitoria.questoes.dto.AnswerResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * NOT @Transactional on purpose: AttemptService.record() runs REQUIRES_NEW and
 * commits independently, so a test-managed rollback would not undo it anyway.
 * Every assertion is scoped to the user created by that test.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class AnswerRecordingTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired QuestionService questionService;

    /**
     * This class is deliberately not @Transactional (see class Javadoc), so every
     * row it inserts (questões via insertMc, users via register) is REAL and
     * permanent in the Testcontainers Postgres — which Spring's test context cache
     * shares with every other @SpringBootTest class in the same JVM run. Left
     * uncleaned, these rows leak into unrelated tests that assert exact counts
     * (e.g. QuestionControllerTest#listReturnsPagedQuestions asserting
     * totalElements).
     *
     * Deleting by exact id, NOT by a LIKE 'rec_%' pattern: in SQL LIKE, '_' is a
     * single-character wildcard, so that pattern would also match an unrelated
     * future fixture like "recX_..." or "rec2_..." and delete it mid-suite. Each
     * helper below records precisely the ids it created; @AfterEach deletes only
     * those. questao's delete cascades question_attempt (ON DELETE CASCADE); the
     * users delete cascades question_attempt, refresh_tokens and
     * user_role_junction (all ON DELETE CASCADE) too.
     */
    private final List<String> createdSourceIds = new ArrayList<>();
    private final List<UUID> createdUserIds = new ArrayList<>();

    @AfterEach
    void cleanUpSharedDatabase() {
        for (String src : createdSourceIds) {
            jdbc.update("DELETE FROM questao WHERE source_id = ?", src);
        }
        for (UUID id : createdUserIds) {
            jdbc.update("DELETE FROM users WHERE id = ?", id);
        }
    }

    private record Student(String email, String access) {}

    private Student register() throws Exception {
        String email = "s_" + UUID.randomUUID() + "@x.com";
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"firstName\":\"Ana\",\"lastName\":\"S\",\"email\":\"" + email
                        + "\",\"password\":\"secret\"}")).andExpect(status().isCreated());
        String res = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"secret\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode node = json.readTree(res);
        createdUserIds.add(userId(email));
        return new Student(email, node.get("accessToken").asText());
    }

    private UUID userId(String email) {
        return jdbc.queryForObject("SELECT id FROM users WHERE email = ?", UUID.class, email);
    }

    private UUID insertMc(String gabarito) {
        UUID id = UUID.randomUUID();
        String src = "rec_" + UUID.randomUUID();
        jdbc.update("INSERT INTO questao (id, source_id, tipo, enunciado, opcao_a, opcao_b, gabarito,"
                + " banca, orgao, cargo, ano, materia, assunto) VALUES"
                + " (?, ?, 'MULTIPLA_ESCOLHA'::tipo_questao, ?, 'A', 'B', ?, 'FGV', 'O', 'C', 2024, 'M', 'A')",
                id, src, "Enunciado " + src, gabarito);
        createdSourceIds.add(src);
        return id;
    }

    private long attemptsFor(UUID user) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM question_attempt WHERE user_id = ?", Long.class, user);
    }

    @Test
    void answeringCorrectlyRecordsATentativaMarkedCorrect() throws Exception {
        Student s = register();
        UUID q = insertMc("A");

        mockMvc.perform(post("/api/questions/{id}/answer", q)
                        .header("Authorization", "Bearer " + s.access())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"chosenAnswer\":\"A\"}"))
                .andExpect(status().isOk());

        UUID u = userId(s.email());
        assertThat(attemptsFor(u)).isEqualTo(1);
        Boolean correct = jdbc.queryForObject(
                "SELECT is_correct FROM question_attempt WHERE user_id = ?", Boolean.class, u);
        assertThat(correct).isTrue();
    }

    @Test
    void answeringWronglyRecordsATentativaMarkedIncorrect() throws Exception {
        Student s = register();
        UUID q = insertMc("A");

        mockMvc.perform(post("/api/questions/{id}/answer", q)
                        .header("Authorization", "Bearer " + s.access())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"chosenAnswer\":\"B\"}"))
                .andExpect(status().isOk());

        UUID u = userId(s.email());
        Boolean correct = jdbc.queryForObject(
                "SELECT is_correct FROM question_attempt WHERE user_id = ?", Boolean.class, u);
        assertThat(correct).isFalse();
    }

    @Test
    void theStoredChosenAnswerIsNormalized() throws Exception {
        Student s = register();
        UUID q = insertMc("A");

        mockMvc.perform(post("/api/questions/{id}/answer", q)
                        .header("Authorization", "Bearer " + s.access())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"chosenAnswer\":\"  b \"}"))
                .andExpect(status().isOk());

        String stored = jdbc.queryForObject(
                "SELECT chosen_answer FROM question_attempt WHERE user_id = ?",
                String.class, userId(s.email()));
        assertThat(stored).isEqualTo("B");
    }

    @Test
    void refazerAppendsASecondTentativaRatherThanOverwriting() throws Exception {
        Student s = register();
        UUID q = insertMc("A");

        for (String choice : new String[] {"B", "A"}) {
            mockMvc.perform(post("/api/questions/{id}/answer", q)
                            .header("Authorization", "Bearer " + s.access())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"chosenAnswer\":\"" + choice + "\"}"))
                    .andExpect(status().isOk());
        }

        assertThat(attemptsFor(userId(s.email()))).isEqualTo(2);
    }

    @Test
    void answeringWithoutAuthIs401AndRecordsNothing() throws Exception {
        UUID q = insertMc("A");

        mockMvc.perform(post("/api/questions/{id}/answer", q)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"chosenAnswer\":\"A\"}"))
                .andExpect(status().isUnauthorized());

        Long n = jdbc.queryForObject(
                "SELECT count(*) FROM question_attempt WHERE question_source_id IN"
                        + " (SELECT source_id FROM questao WHERE id = ?)", Long.class, q);
        assertThat(n).isZero();
    }

    @Test
    void anInvalidAlternativeIs400AndRecordsNothing() throws Exception {
        Student s = register();
        UUID q = insertMc("A");

        mockMvc.perform(post("/api/questions/{id}/answer", q)
                        .header("Authorization", "Bearer " + s.access())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"chosenAnswer\":\"Z\"}"))
                .andExpect(status().isBadRequest());

        assertThat(attemptsFor(userId(s.email()))).isZero();
    }

    /**
     * The whole reason AttemptService.record() runs in its own REQUIRES_NEW
     * transaction inside a try/catch is so that a failed insert can never cost the
     * student their verdict (docs/adr/0003). This is the only test that exercises
     * that failure path directly: a "ghost" user id that was never inserted into
     * users trips the question_attempt_user_id_users_id_fk foreign key exactly the
     * way an unexpected DB failure would. Calling QuestionService.answer directly
     * (bypassing the controller's JWT check) is what lets us use an id the auth
     * layer would never hand us. If the try/catch in QuestionService.answer were
     * removed, this test fails: the DataIntegrityViolationException would propagate
     * out of answer() (readOnly, but the exception surfaces on commit of the
     * REQUIRES_NEW transaction, inside the try) and GlobalExceptionHandler has no
     * mapping for it, so it would turn into a 500 instead of a judged verdict.
     */
    @Test
    void aFailedRecordingStillReturnsTheVerdict(CapturedOutput output) {
        UUID q = insertMc("A");
        UUID ghostUser = UUID.randomUUID(); // deliberately never inserted into users

        Optional<AnswerResult> result = questionService.answer(q, "A", ghostUser);

        assertThat(result).isPresent();
        assertThat(result.get().correct()).isTrue();
        assertThat(result.get().correctAnswer()).isEqualTo("A");
        assertThat(output).contains("Failed to record tentativa");
    }
}
