package com.direitoria.questoes;

import tools.jackson.databind.JsonNode;
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

/**
 * NOT @Transactional: AttemptService.record() commits in its own REQUIRES_NEW
 * transaction, so a rolled-back test could not undo it. Cleanup is by exact id.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class AttemptHistoryEndpointTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;

    private final List<String> createdSourceIds = new ArrayList<>();
    private final List<UUID> createdUserIds = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        for (String src : createdSourceIds) jdbc.update("DELETE FROM questao WHERE source_id = ?", src);
        for (UUID id : createdUserIds) jdbc.update("DELETE FROM users WHERE id = ?", id);
    }

    private String register() throws Exception {
        String email = "ah_" + UUID.randomUUID() + "@x.com";
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"firstName\":\"A\",\"lastName\":\"B\",\"email\":\"" + email
                        + "\",\"password\":\"secret\"}")).andExpect(status().isCreated());
        createdUserIds.add(jdbc.queryForObject("SELECT id FROM users WHERE email = ?", UUID.class, email));
        String res = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"secret\"}"))
                .andReturn().getResponse().getContentAsString();
        return json.readTree(res).get("accessToken").asText();
    }

    private UUID insertMc(String gabarito) {
        UUID id = UUID.randomUUID();
        String src = "ah_" + UUID.randomUUID();
        jdbc.update("INSERT INTO questao (id, source_id, tipo, enunciado, opcao_a, opcao_b, gabarito,"
                + " banca, orgao, cargo, ano, materia, assunto) VALUES"
                + " (?, ?, 'MULTIPLA_ESCOLHA'::tipo_questao, ?, 'A', 'B', ?, 'FGV', 'O', 'C', 2024, 'M', 'A')",
                id, src, "Enunciado " + src, gabarito);
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
    void requiresAuth() throws Exception {
        mockMvc.perform(get("/api/me/attempts").param("questionIds", UUID.randomUUID().toString()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void returnsHistoryKeyedByQuestionIdNewestFirst() throws Exception {
        String access = register();
        UUID q = insertMc("A");
        answer(access, q, "B"); // erro
        answer(access, q, "A"); // acerto, mais recente

        mockMvc.perform(get("/api/me/attempts").param("questionIds", q.toString())
                        .header("Authorization", "Bearer " + access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$['" + q + "'].length()").value(2))
                .andExpect(jsonPath("$['" + q + "'][0].correct").value(true))   // mais recente primeiro
                .andExpect(jsonPath("$['" + q + "'][0].chosenAnswer").value("A"))
                .andExpect(jsonPath("$['" + q + "'][1].correct").value(false))
                .andExpect(jsonPath("$['" + q + "'][1].chosenAnswer").value("B"));
    }

    @Test
    void omitsQuestoesWithoutTentativas() throws Exception {
        String access = register();
        UUID respondida = insertMc("A");
        UUID intocada = insertMc("A");
        answer(access, respondida, "A");

        mockMvc.perform(get("/api/me/attempts")
                        .param("questionIds", respondida + "," + intocada)
                        .header("Authorization", "Bearer " + access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$['" + respondida + "']").exists())
                .andExpect(jsonPath("$['" + intocada + "']").doesNotExist());
    }

    @Test
    void neverLeaksAnotherStudentsHistory() throws Exception {
        String aluno = register();
        String outro = register();
        UUID q = insertMc("A");
        answer(aluno, q, "A");

        mockMvc.perform(get("/api/me/attempts").param("questionIds", q.toString())
                        .header("Authorization", "Bearer " + outro))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$['" + q + "']").doesNotExist());
    }

    @Test
    void emptyQuestionIdsReturnsEmptyObject() throws Exception {
        String access = register();
        mockMvc.perform(get("/api/me/attempts").param("questionIds", "")
                        .header("Authorization", "Bearer " + access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }
}
