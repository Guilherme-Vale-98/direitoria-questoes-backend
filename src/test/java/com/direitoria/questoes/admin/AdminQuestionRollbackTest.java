package com.direitoria.questoes.admin;

import com.direitoria.questoes.TestcontainersConfiguration;
import com.direitoria.questoes.auth.JwtService;
import com.direitoria.questoes.domain.ExamBoard;
import com.direitoria.questoes.repository.ExamBoardRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class AdminQuestionRollbackTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JwtService jwt;
    @Autowired JdbcTemplate jdbc;
    @Autowired ExamBoardRepository boards;

    private final String agencyName = "Órgão Rollback " + UUID.randomUUID();
    private final String boardName = "Banca Rollback " + UUID.randomUUID();

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM orgao WHERE nome = ?", agencyName);
        jdbc.update("DELETE FROM banca WHERE nome = ?", boardName);
    }

    @Test
    void aNewOrgaoIsRolledBackWhenALaterStepFails() throws Exception {
        ExamBoard board = boards.save(new ExamBoard(boardName)); // committed: this class has no test transaction
        Map<String, Object> body = AdminFixtures.mc("Enunciado rollback " + UUID.randomUUID(), 0, board.getId(), 0);
        body.put("agency", AdminFixtures.named(agencyName));   // resolved — and inserted — first…
        body.put("subjects", List.of(AdminFixtures.ref(-1)));  // …then this fails
        long manualBefore = jdbc.queryForObject(
                "SELECT count(*) FROM questao WHERE source_id LIKE 'manual:%'", Long.class);

        mvc.perform(multipart("/api/admin/questions")
                        .file(AdminFixtures.json(json, body))
                        .header("Authorization", AdminFixtures.bearer(jwt, "ADMIN")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.subjects").exists());

        assertThat(jdbc.queryForObject("SELECT count(*) FROM orgao WHERE nome = ?", Long.class, agencyName)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM questao WHERE source_id LIKE 'manual:%'", Long.class))
                .isEqualTo(manualBefore);
    }
}
