package com.direitoria.questoes.admin;

import com.direitoria.questoes.TestcontainersConfiguration;
import com.direitoria.questoes.auth.JwtService;
import com.direitoria.questoes.domain.Agency;
import com.direitoria.questoes.domain.ExamBoard;
import com.direitoria.questoes.domain.Subject;
import com.direitoria.questoes.repository.AgencyRepository;
import com.direitoria.questoes.repository.ExamBoardRepository;
import com.direitoria.questoes.repository.SubjectRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AdminQuestionDeleteTest {

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
        subject = subjects.save(new Subject("Disciplina D"));
        board = boards.save(new ExamBoard("Banca D"));
        agency = agencies.save(new Agency("Órgão D"));
    }

    /** Creates a questão through the real endpoint, with one image. */
    private UUID create(String statement) throws Exception {
        Map<String, Object> body = AdminFixtures.mc(statement, subject.getId(), board.getId(), agency.getId());
        body.put("images", List.of(Map.of("upload", 0)));
        String res = mvc.perform(multipart("/api/admin/questions")
                        .file(AdminFixtures.json(json, body))
                        .file(AdminFixtures.image("fig.png", "image/png", AdminFixtures.png(64)))
                        .header("Authorization", AdminFixtures.bearer(jwt, "ADMIN")))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return UUID.fromString(json.readTree(res).get("id").asText());
    }

    private ResultActions deleteAs(UUID id, String expectedAttempts, String role) throws Exception {
        var req = delete("/api/admin/questions/{id}", id);
        if (expectedAttempts != null) {
            req = req.param("expectedAttempts", expectedAttempts);
        }
        if (role != null) {
            req = req.header("Authorization", AdminFixtures.bearer(jwt, role));
        }
        return mvc.perform(req);
    }

    private long count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Long.class, args);
    }

    @Test
    void anUnansweredQuestionIsDeletedWithItsLinksAndImages() throws Exception {
        UUID id = create("Enunciado D1");
        String sid = AdminFixtures.sourceIdOf(jdbc, id);
        assertThat(count("SELECT count(*) FROM questao_disciplina WHERE source_id = ?", sid)).isEqualTo(1L);
        assertThat(count("SELECT count(*) FROM questao_asset WHERE source_id = ?", sid)).isEqualTo(1L);

        deleteAs(id, "0", "ADMIN").andExpect(status().isNoContent());

        assertThat(count("SELECT count(*) FROM questao WHERE source_id = ?", sid)).isZero();
        assertThat(count("SELECT count(*) FROM questao_disciplina WHERE source_id = ?", sid)).isZero();
        assertThat(count("SELECT count(*) FROM questao_asset WHERE source_id = ?", sid)).isZero();
    }

    @Test
    void anAnsweredQuestionIsDeletedWithItsTentativasWhenTheCountMatches() throws Exception {
        UUID id = create("Enunciado D2");
        String sid = AdminFixtures.sourceIdOf(jdbc, id);
        UUID s1 = AdminFixtures.student(jdbc);
        UUID s2 = AdminFixtures.student(jdbc);
        AdminFixtures.attempt(jdbc, s1, sid, "A", false);
        AdminFixtures.attempt(jdbc, s1, sid, "C", true);
        AdminFixtures.attempt(jdbc, s2, sid, "B", false);
        UUID other = create("Enunciado D2 outra");
        String otherSid = AdminFixtures.sourceIdOf(jdbc, other);
        AdminFixtures.attempt(jdbc, s1, otherSid, "C", true);

        deleteAs(id, "3", "ADMIN").andExpect(status().isNoContent());

        assertThat(count("SELECT count(*) FROM question_attempt WHERE question_source_id = ?", sid)).isZero();
        assertThat(count("SELECT count(*) FROM question_attempt WHERE question_source_id = ?", otherSid)).isEqualTo(1L);
    }

    @Test
    void aStaleCountIs409WithTheRealCountsAndDeletesNothing() throws Exception {
        UUID id = create("Enunciado D3");
        String sid = AdminFixtures.sourceIdOf(jdbc, id);
        AdminFixtures.attempt(jdbc, AdminFixtures.student(jdbc), sid, "A", false);
        AdminFixtures.attempt(jdbc, AdminFixtures.student(jdbc), sid, "C", true);

        deleteAs(id, "1", "ADMIN")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DELETE_CONFIRMATION_REQUIRED"))
                .andExpect(jsonPath("$.attemptCount").value(2))
                .andExpect(jsonPath("$.studentCount").value(2))
                .andExpect(jsonPath("$.message").value("Esta questão tem 2 tentativas de 2 alunos. Confirme de novo para excluir."));

        assertThat(count("SELECT count(*) FROM questao WHERE source_id = ?", sid)).isEqualTo(1L);
        assertThat(count("SELECT count(*) FROM question_attempt WHERE question_source_id = ?", sid)).isEqualTo(2L);
    }

    @Test
    void theStaleMessageIsSingularForOne() throws Exception {
        UUID id = create("Enunciado D4");
        AdminFixtures.attempt(jdbc, AdminFixtures.student(jdbc), AdminFixtures.sourceIdOf(jdbc, id), "A", false);

        deleteAs(id, "0", "ADMIN")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Esta questão tem 1 tentativa de 1 aluno. Confirme de novo para excluir."));
    }

    @Test
    void anUnknownQuestionIs404() throws Exception {
        deleteAs(UUID.randomUUID(), "0", "ADMIN").andExpect(status().isNotFound());
    }

    @Test
    void theExpectedCountIsRequired() throws Exception {
        UUID id = create("Enunciado D6");
        deleteAs(id, null, "ADMIN").andExpect(status().isBadRequest());
        deleteAs(id, "muitas", "ADMIN").andExpect(status().isBadRequest());
        deleteAs(id, "-1", "ADMIN").andExpect(status().isBadRequest());
        assertThat(count("SELECT count(*) FROM questao WHERE id = ?", id)).isEqualTo(1L);
    }

    @Test
    void nonAdminsAreRefused() throws Exception {
        UUID id = create("Enunciado D7");
        deleteAs(id, "0", "USER").andExpect(status().isForbidden());
        deleteAs(id, "0", null).andExpect(status().isUnauthorized());
        assertThat(count("SELECT count(*) FROM questao WHERE id = ?", id)).isEqualTo(1L);
    }

    @Test
    void canonicalsSurviveTheDelete() throws Exception {
        UUID id = create("Enunciado D8");
        deleteAs(id, "0", "ADMIN").andExpect(status().isNoContent());

        assertThat(subjects.existsById(subject.getId())).isTrue();
        assertThat(boards.existsById(board.getId())).isTrue();
        assertThat(agencies.existsById(agency.getId())).isTrue();
    }

    @Test
    void aDeletedQuestionIsGoneFromThePublicEndpoints() throws Exception {
        // Review Focus 5: a student's bookmark and the image URL answer 404 cleanly.
        UUID id = create("Enunciado D9");
        mvc.perform(get("/api/questions/{id}/assets/0", id)).andExpect(status().isOk());

        deleteAs(id, "0", "ADMIN").andExpect(status().isNoContent());

        mvc.perform(get("/api/questions/{id}", id)).andExpect(status().isNotFound());
        mvc.perform(get("/api/questions/{id}/assets/0", id)).andExpect(status().isNotFound());
    }
}
