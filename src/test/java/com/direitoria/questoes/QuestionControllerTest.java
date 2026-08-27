package com.direitoria.questoes;

import com.direitoria.questoes.domain.ExamBoard;
import com.direitoria.questoes.repository.ExamBoardRepository;
import tools.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class QuestionControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ExamBoardRepository examBoards;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;

    private String accessToken() throws Exception {
        String email = "qc_" + UUID.randomUUID() + "@x.com";
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"firstName\":\"A\",\"lastName\":\"B\",\"email\":\"" + email
                        + "\",\"password\":\"secret\"}")).andExpect(status().isCreated());
        String res = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"secret\"}"))
                .andReturn().getResponse().getContentAsString();
        return json.readTree(res).get("accessToken").asText();
    }

    private UUID insert(String src, String tipo, Integer bancaId) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO questao (id, source_id, tipo, enunciado, opcao_a, gabarito, banca, orgao,"
                        + " cargo, ano, materia, assunto, banca_id) VALUES (?, ?, ?::tipo_questao, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id, src, tipo, "Enunciado " + src, "Opção A", "A", "FGV", "Org", "Cargo", (short) 2024, "M", "A", bancaId);
        return id;
    }

    @Test
    void listReturnsPagedQuestions() throws Exception {
        ExamBoard fgv = examBoards.save(new ExamBoard("FGV"));
        insert("q1", "MULTIPLA_ESCOLHA", fgv.getId());
        insert("q2", "CERTO_ERRADO", fgv.getId());

        mockMvc.perform(get("/api/questions").param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.content[0].statement").isString())
                .andExpect(jsonPath("$.content[0].id").isString());
    }

    @Test
    void filtersByType() throws Exception {
        ExamBoard fgv = examBoards.save(new ExamBoard("FGV"));
        insert("q1", "MULTIPLA_ESCOLHA", fgv.getId());
        insert("q2", "CERTO_ERRADO", fgv.getId());

        mockMvc.perform(get("/api/questions").param("type", "CERTO_ERRADO"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].type").value("CERTO_ERRADO"));
    }

    @Test
    void detailReturnsQuestionByPublicId() throws Exception {
        ExamBoard fgv = examBoards.save(new ExamBoard("FGV"));
        UUID id = insert("q1", "MULTIPLA_ESCOLHA", fgv.getId());

        mockMvc.perform(get("/api/questions/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.type").value("MULTIPLA_ESCOLHA"));
    }

    @Test
    void detailReturns404ForUnknownId() throws Exception {
        mockMvc.perform(get("/api/questions/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").exists());
    }

    // ---- POST /{id}/answer (server-side judging; gabarito no longer sent in list/detail) ----

    private UUID insertMc(String src, String gabarito, String comentario) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO questao (id, source_id, tipo, enunciado, opcao_a, opcao_b, gabarito, comentario,"
                        + " banca, orgao, cargo, ano, materia, assunto) VALUES"
                        + " (?, ?, 'MULTIPLA_ESCOLHA'::tipo_questao, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id, src, "Enunciado " + src, "Opção A", "Opção B", gabarito, comentario,
                "FGV", "Org", "Cargo", (short) 2024, "M", "A");
        return id;
    }

    private UUID insertCe(String src, String gabarito) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO questao (id, source_id, tipo, enunciado, gabarito,"
                        + " banca, orgao, cargo, ano, materia, assunto) VALUES"
                        + " (?, ?, 'CERTO_ERRADO'::tipo_questao, ?, ?, ?, ?, ?, ?, ?, ?)",
                id, src, "Enunciado " + src, gabarito, "FGV", "Org", "Cargo", (short) 2024, "M", "A");
        return id;
    }

    @Test
    void answerMultiplaEscolhaCorrectRevealsGabaritoAndComentario() throws Exception {
        UUID id = insertMc("m1", "A", "Porque A é a correta");
        mockMvc.perform(post("/api/questions/{id}/answer", id)
                        .header("Authorization", "Bearer " + accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"chosenAnswer\":\"A\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.correct").value(true))
                .andExpect(jsonPath("$.correctAnswer").value("A"))
                .andExpect(jsonPath("$.commentary").value("Porque A é a correta"));
    }

    @Test
    void answerMultiplaEscolhaWrong() throws Exception {
        UUID id = insertMc("m2", "A", null);
        mockMvc.perform(post("/api/questions/{id}/answer", id)
                        .header("Authorization", "Bearer " + accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"chosenAnswer\":\"B\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.correct").value(false))
                .andExpect(jsonPath("$.correctAnswer").value("A"));
    }

    @Test
    void answerCertoErrado() throws Exception {
        UUID id = insertCe("c1", "CERTO");
        mockMvc.perform(post("/api/questions/{id}/answer", id)
                        .header("Authorization", "Bearer " + accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"chosenAnswer\":\"Certo\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.correct").value(true))
                .andExpect(jsonPath("$.correctAnswer").value("CERTO"));
    }

    @Test
    void answerRejectsInvalidAlternativeWith400() throws Exception {
        UUID id = insertMc("m3", "A", null);
        mockMvc.perform(post("/api/questions/{id}/answer", id)
                        .header("Authorization", "Bearer " + accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"chosenAnswer\":\"Z\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void answerReturns404ForUnknownQuestion() throws Exception {
        mockMvc.perform(post("/api/questions/{id}/answer", UUID.randomUUID())
                        .header("Authorization", "Bearer " + accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"chosenAnswer\":\"A\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void listDoesNotExposeGabaritoOrCommentary() throws Exception {
        ExamBoard fgv = examBoards.save(new ExamBoard("FGV"));
        insert("q1", "MULTIPLA_ESCOLHA", fgv.getId());
        mockMvc.perform(get("/api/questions").param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].answer").doesNotExist())
                .andExpect(jsonPath("$.content[0].commentary").doesNotExist())
                .andExpect(jsonPath("$.content[0].sourceId").doesNotExist());
    }
}
