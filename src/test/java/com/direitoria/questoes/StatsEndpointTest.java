package com.direitoria.questoes;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * NOT @Transactional, deliberately, matching AnswerRecordingTest: every test here
 * answers questões through the real HTTP endpoint, and AttemptService.record()
 * commits each tentativa in its own REQUIRES_NEW transaction. If this class were
 * @Transactional, the questão/user rows this test inserts would sit uncommitted
 * in the outer (test-managed) transaction on the test's own connection, while
 * REQUIRES_NEW opens a SEPARATE connection to record the tentativa — which would
 * then fail its foreign keys against rows it cannot see, get silently swallowed
 * by QuestionService.answer's try/catch, and leave every stats assertion here
 * looking at zeroes. A rolled-back outer transaction cannot undo a REQUIRES_NEW
 * commit either way, so there is no transactional shortcut available here: this
 * class must insert real, permanent rows into the shared Testcontainers Postgres
 * and clean up after itself explicitly, exactly like AnswerRecordingTest does —
 * by exact id, not by a LIKE pattern (see that class's Javadoc for why).
 * @AfterEach deletes by the exact ids recorded during each test: questao (whose
 * delete cascades question_attempt and questao_disciplina), disciplina (deleted
 * after the questao deletes, so questao_disciplina's cascade has already cleared
 * it), and users (whose delete cascades question_attempt too).
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class StatsEndpointTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;

    private final List<String> createdSourceIds = new ArrayList<>();
    private final List<Integer> createdDisciplinaIds = new ArrayList<>();
    private final List<UUID> createdUserIds = new ArrayList<>();

    @AfterEach
    void cleanUpSharedDatabase() {
        for (String src : createdSourceIds) {
            jdbc.update("DELETE FROM questao WHERE source_id = ?", src);
        }
        for (Integer d : createdDisciplinaIds) {
            jdbc.update("DELETE FROM disciplina WHERE id = ?", d);
        }
        for (UUID id : createdUserIds) {
            jdbc.update("DELETE FROM users WHERE id = ?", id);
        }
    }

    private String register() throws Exception {
        String email = "st_" + UUID.randomUUID() + "@x.com";
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"firstName\":\"A\",\"lastName\":\"B\",\"email\":\"" + email
                        + "\",\"password\":\"secret\"}")).andExpect(status().isCreated());
        String res = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"secret\"}"))
                .andReturn().getResponse().getContentAsString();
        JsonNode node = json.readTree(res);
        createdUserIds.add(jdbc.queryForObject("SELECT id FROM users WHERE email = ?", UUID.class, email));
        return node.get("accessToken").asText();
    }

    private UUID insertMcWithDisciplina(String gabarito, String disciplinaName) {
        UUID id = UUID.randomUUID();
        String src = "stq_" + UUID.randomUUID();
        jdbc.update("INSERT INTO questao (id, source_id, tipo, enunciado, opcao_a, opcao_b, gabarito,"
                + " banca, orgao, cargo, ano, materia, assunto) VALUES"
                + " (?, ?, 'MULTIPLA_ESCOLHA'::tipo_questao, ?, 'A', 'B', ?, 'FGV', 'O', 'C', 2024, 'M', 'A')",
                id, src, "Enunciado " + src, gabarito);
        createdSourceIds.add(src);
        Integer d = jdbc.queryForObject(
                "INSERT INTO disciplina (nome) VALUES (?) ON CONFLICT (nome)"
                        + " DO UPDATE SET nome = EXCLUDED.nome RETURNING id", Integer.class, disciplinaName);
        createdDisciplinaIds.add(d);
        jdbc.update("INSERT INTO questao_disciplina (source_id, disciplina_id) VALUES (?, ?)", src, d);
        return id;
    }

    @Test
    void statsRequireAuth() throws Exception {
        mockMvc.perform(get("/api/me/stats")).andExpect(status().isUnauthorized());
    }

    @Test
    void aFreshStudentGetsZeroesAndAnEmptyBreakdown() throws Exception {
        String access = register();
        mockMvc.perform(get("/api/me/stats").header("Authorization", "Bearer " + access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.overall.answered").value(0))
                .andExpect(jsonPath("$.overall.accuracy").value(0.0))
                .andExpect(jsonPath("$.activity.currentStreakDays").value(0))
                .andExpect(jsonPath("$.activity.lastActiveDate").doesNotExist())
                .andExpect(jsonPath("$.bySubject").isEmpty());
    }

    @Test
    void theUltimaTentativaDecidesTheFigures() throws Exception {
        String access = register();
        String name = "Direito Penal " + UUID.randomUUID();
        UUID q = insertMcWithDisciplina("A", name);

        // erra, depois acerta: uma respondida, um acerto, 100%
        for (String choice : new String[] {"B", "A"}) {
            mockMvc.perform(post("/api/questions/{id}/answer", q)
                    .header("Authorization", "Bearer " + access)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"chosenAnswer\":\"" + choice + "\"}")).andExpect(status().isOk());
        }

        mockMvc.perform(get("/api/me/stats").header("Authorization", "Bearer " + access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.overall.answered").value(1))
                .andExpect(jsonPath("$.overall.correct").value(1))
                .andExpect(jsonPath("$.overall.incorrect").value(0))
                .andExpect(jsonPath("$.overall.accuracy").value(100.0))
                .andExpect(jsonPath("$.activity.currentStreakDays").value(1))
                .andExpect(jsonPath("$.activity.answeredLast7Days").value(1))
                .andExpect(jsonPath("$.activity.lastActiveDate")
                        .value(LocalDate.now(ZoneId.of("America/Sao_Paulo")).toString()))
                .andExpect(jsonPath("$.bySubject[0].subjectName").value(name))
                .andExpect(jsonPath("$.bySubject[0].answered").value(1))
                .andExpect(jsonPath("$.bySubject[0].accuracy").value(100.0));
    }

    @Test
    void accuracyIsRoundedToOneDecimal() throws Exception {
        String access = register();
        UUID q1 = insertMcWithDisciplina("A", "D1 " + UUID.randomUUID());
        UUID q2 = insertMcWithDisciplina("A", "D2 " + UUID.randomUUID());
        UUID q3 = insertMcWithDisciplina("A", "D3 " + UUID.randomUUID());

        mockMvc.perform(post("/api/questions/{id}/answer", q1).header("Authorization", "Bearer " + access)
                .contentType(MediaType.APPLICATION_JSON).content("{\"chosenAnswer\":\"A\"}"));
        mockMvc.perform(post("/api/questions/{id}/answer", q2).header("Authorization", "Bearer " + access)
                .contentType(MediaType.APPLICATION_JSON).content("{\"chosenAnswer\":\"B\"}"));
        mockMvc.perform(post("/api/questions/{id}/answer", q3).header("Authorization", "Bearer " + access)
                .contentType(MediaType.APPLICATION_JSON).content("{\"chosenAnswer\":\"B\"}"));

        // 1/3 -> 33.3
        mockMvc.perform(get("/api/me/stats").header("Authorization", "Bearer " + access))
                .andExpect(jsonPath("$.overall.accuracy").value(33.3));
    }

    /**
     * Every accidental ordering (insertion order, alphabetical nome, disciplina.id)
     * must disagree with the correct one, or this test could pass for the wrong
     * reason. "small" gets the lower disciplina.id (inserted first) AND sorts
     * first alphabetically ("Aaa" < "Zzz"), yet has fewer respondidas — so only a
     * genuine ORDER BY answered DESC puts "big" at index 0.
     */
    @Test
    void theBreakdownIsSortedByRespondidasDescending() throws Exception {
        String access = register();
        String small = "Aaa " + UUID.randomUUID();
        String big = "Zzz " + UUID.randomUUID();
        UUID s1 = insertMcWithDisciplina("A", small);
        UUID b1 = insertMcWithDisciplina("A", big);
        UUID b2 = insertMcWithDisciplina("A", big);

        for (UUID q : new UUID[] {s1, b1, b2}) {
            mockMvc.perform(post("/api/questions/{id}/answer", q)
                    .header("Authorization", "Bearer " + access)
                    .contentType(MediaType.APPLICATION_JSON).content("{\"chosenAnswer\":\"A\"}"));
        }

        mockMvc.perform(get("/api/me/stats").header("Authorization", "Bearer " + access))
                .andExpect(jsonPath("$.bySubject[0].subjectName").value(big))
                .andExpect(jsonPath("$.bySubject[0].answered").value(2))
                .andExpect(jsonPath("$.bySubject[1].subjectName").value(small));
    }
}
