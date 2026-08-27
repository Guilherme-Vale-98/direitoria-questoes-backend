package com.direitoria.questoes;

import tools.jackson.databind.ObjectMapper;
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

/** NOT @Transactional — see AttemptHistoryEndpointTest for why. Cleanup by exact id. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class HistoryFilterTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;

    private final List<String> createdSourceIds = new ArrayList<>();
    private final List<UUID> createdUserIds = new ArrayList<>();
    private final String banca = "HF" + UUID.randomUUID().toString().substring(0, 8);

    @AfterEach
    void cleanUp() {
        for (String src : createdSourceIds) jdbc.update("DELETE FROM questao WHERE source_id = ?", src);
        for (UUID id : createdUserIds) jdbc.update("DELETE FROM users WHERE id = ?", id);
    }

    private String register() throws Exception {
        String email = "hf_" + UUID.randomUUID() + "@x.com";
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"firstName\":\"A\",\"lastName\":\"B\",\"email\":\"" + email
                        + "\",\"password\":\"secret\"}")).andExpect(status().isCreated());
        createdUserIds.add(jdbc.queryForObject("SELECT id FROM users WHERE email = ?", UUID.class, email));
        String res = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"secret\"}"))
                .andReturn().getResponse().getContentAsString();
        return json.readTree(res).get("accessToken").asText();
    }

    /** All fixtures share a unique `banca` string so every assertion is scoped to this test's data. */
    private UUID insert(String gabarito) {
        UUID id = UUID.randomUUID();
        String src = "hf_" + UUID.randomUUID();
        jdbc.update("INSERT INTO questao (id, source_id, tipo, enunciado, opcao_a, opcao_b, gabarito,"
                + " banca, orgao, cargo, ano, materia, assunto) VALUES"
                + " (?, ?, 'MULTIPLA_ESCOLHA'::tipo_questao, ?, 'A', 'B', ?, ?, 'O', 'C', 2024, 'M', 'A')",
                id, src, "Enunciado " + src, gabarito, banca);
        createdSourceIds.add(src);
        return id;
    }

    private void answer(String access, UUID q, String choice) throws Exception {
        mockMvc.perform(post("/api/questions/{id}/answer", q)
                .header("Authorization", "Bearer " + access)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"chosenAnswer\":\"" + choice + "\"}")).andExpect(status().isOk());
    }

    @Test
    void historyStatusRequiresAuth() throws Exception {
        mockMvc.perform(get("/api/questions").param("historyStatus", "wrong"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void withoutHistoryStatusTheEndpointStaysPublic() throws Exception {
        insert("A");
        mockMvc.perform(get("/api/questions").param("search", "Enunciado hf_"))
                .andExpect(status().isOk());
    }

    @Test
    void wrongReturnsOnlyQuestoesWhoseUltimaTentativaFailed() throws Exception {
        String access = register();
        UUID errada = insert("A");
        UUID certa = insert("A");
        insert("A"); // nunca respondida
        answer(access, errada, "B");
        answer(access, certa, "A");

        mockMvc.perform(get("/api/questions")
                        .param("historyStatus", "wrong").param("search", "Enunciado hf_")
                        .header("Authorization", "Bearer " + access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(errada.toString()));
    }

    @Test
    void aQuestaoRedoneCorrectlyLeavesTheWrongBucket() throws Exception {
        String access = register();
        UUID q = insert("A");
        answer(access, q, "B"); // errou
        answer(access, q, "A"); // refez e acertou -> última tentativa correta

        mockMvc.perform(get("/api/questions")
                        .param("historyStatus", "wrong").param("search", "Enunciado hf_")
                        .header("Authorization", "Bearer " + access))
                .andExpect(jsonPath("$.totalElements").value(0));
        mockMvc.perform(get("/api/questions")
                        .param("historyStatus", "correct").param("search", "Enunciado hf_")
                        .header("Authorization", "Bearer " + access))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void unansweredExcludesEverythingTheStudentTouched() throws Exception {
        String access = register();
        UUID respondida = insert("A");
        UUID intocada = insert("A");
        answer(access, respondida, "A");

        mockMvc.perform(get("/api/questions")
                        .param("historyStatus", "unanswered").param("search", "Enunciado hf_")
                        .header("Authorization", "Bearer " + access))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(intocada.toString()));
    }

    @Test
    void composesWithContentFilters() throws Exception {
        String access = register();
        UUID q = insert("A");
        answer(access, q, "B");

        // mesmo filtro de histórico, mas um ano que não existe -> nada
        mockMvc.perform(get("/api/questions")
                        .param("historyStatus", "wrong").param("search", "Enunciado hf_")
                        .param("year", "1999")
                        .header("Authorization", "Bearer " + access))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void oneStudentsHistoryNeverFiltersAnothers() throws Exception {
        String aluno = register();
        String outro = register();
        UUID q = insert("A");
        answer(aluno, q, "B");

        mockMvc.perform(get("/api/questions")
                        .param("historyStatus", "wrong").param("search", "Enunciado hf_")
                        .header("Authorization", "Bearer " + outro))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    /**
     * Regression guard for the empty-list branches of matchesHistory. A brand-new
     * student has answered nothing, so `answeredSourceIds` and
     * `sourceIdsByLatestOutcome` are both EMPTY lists — not null. "unanswered" must
     * still show the whole catalog (empty excludes-list -> conjunction, i.e. no
     * exclusion); "correct"/"wrong" must show nothing (empty non-excludes list ->
     * disjunction, i.e. nothing matches). If `historyExcludes ? conjunction() :
     * disjunction()` were ever flipped, "unanswered" would wrongly go empty for
     * every brand-new student — the single most-used entry into the feature.
     */
    @Test
    void umAlunoSemTentativasVeTudoEmNaoRespondidasENadaNasOutras() throws Exception {
        String access = register();
        insert("A");
        insert("A");
        insert("A");

        mockMvc.perform(get("/api/questions")
                        .param("historyStatus", "unanswered").param("search", "Enunciado hf_")
                        .header("Authorization", "Bearer " + access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3));
        mockMvc.perform(get("/api/questions")
                        .param("historyStatus", "correct").param("search", "Enunciado hf_")
                        .header("Authorization", "Bearer " + access))
                .andExpect(jsonPath("$.totalElements").value(0));
        mockMvc.perform(get("/api/questions")
                        .param("historyStatus", "wrong").param("search", "Enunciado hf_")
                        .header("Authorization", "Bearer " + access))
                .andExpect(jsonPath("$.totalElements").value(0));
    }
}
