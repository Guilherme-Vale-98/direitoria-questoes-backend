package com.direitoria.questoes.admin;

import com.direitoria.questoes.TestcontainersConfiguration;
import com.direitoria.questoes.auth.JwtService;
import com.direitoria.questoes.domain.Agency;
import com.direitoria.questoes.domain.ExamBoard;
import com.direitoria.questoes.domain.Subject;
import com.direitoria.questoes.repository.AgencyRepository;
import com.direitoria.questoes.repository.ExamBoardRepository;
import com.direitoria.questoes.repository.SubjectRepository;
import java.time.Year;
import java.time.ZoneId;
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
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AdminQuestionCreateTest {

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
        subject = subjects.save(new Subject("Disciplina C"));
        board = boards.save(new ExamBoard("Banca C"));
        agency = agencies.save(new Agency("Órgão C"));
    }

    private Map<String, Object> mc(String statement) {
        return AdminFixtures.mc(statement, subject.getId(), board.getId(), agency.getId());
    }

    private ResultActions post(Map<String, Object> body, String role) throws Exception {
        return mvc.perform(multipart("/api/admin/questions")
                .file(AdminFixtures.json(json, body))
                .header("Authorization", AdminFixtures.bearer(jwt, role)));
    }

    private UUID idOf(ResultActions r) throws Exception {
        return UUID.fromString(json.readTree(r.andReturn().getResponse().getContentAsString()).get("id").asText());
    }

    private long manualRows() {
        return jdbc.queryForObject("SELECT count(*) FROM questao WHERE source_id LIKE 'manual:%'", Long.class);
    }

    @Test
    void createsAMultiplaEscolhaAndReadsItBack() throws Exception {
        UUID id = idOf(post(mc("Enunciado C1"), "ADMIN").andExpect(status().isCreated()));

        Map<String, Object> row = jdbc.queryForMap(
                "SELECT source_id, gabarito, opcao_e, banca, orgao, materia, assunto, cargo FROM questao WHERE id = ?", id);
        assertThat((String) row.get("source_id")).startsWith("manual:");
        assertThat(row.get("gabarito")).isEqualTo("C");
        assertThat(row.get("opcao_e")).isNull();
        assertThat(row.get("banca")).isEqualTo("Banca C");
        assertThat(row.get("orgao")).isEqualTo("Órgão C");
        assertThat(row.get("materia")).isEqualTo("Disciplina C");
        assertThat(row.get("assunto")).isEqualTo("");
        assertThat(row.get("cargo")).isEqualTo("Analista");

        mvc.perform(get("/api/admin/questions/{id}", id).header("Authorization", AdminFixtures.bearer(jwt, "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.type").value("MULTIPLA_ESCOLHA"))
                .andExpect(jsonPath("$.answer").value("C"))
                .andExpect(jsonPath("$.options.A").value("Alternativa A de Enunciado C1"))
                .andExpect(jsonPath("$.options.E").value(nullValue()))
                .andExpect(jsonPath("$.subjects[0].name").value("Disciplina C"))
                .andExpect(jsonPath("$.examBoard.name").value("Banca C"))
                .andExpect(jsonPath("$.agency.name").value("Órgão C"))
                .andExpect(jsonPath("$.year").value(2024))
                .andExpect(jsonPath("$.role").value("Analista"))
                .andExpect(jsonPath("$.difficulty").value("MODERADO"))
                .andExpect(jsonPath("$.commentary").value("Porque sim."))
                .andExpect(jsonPath("$.attemptCount").value(0))
                .andExpect(jsonPath("$.studentCount").value(0))
                .andExpect(jsonPath("$.locked.type").value(false))
                .andExpect(jsonPath("$.locked.optionCount").value(false))
                .andExpect(jsonPath("$.images").isEmpty());
    }

    @Test
    void createsACertoErradoAndIgnoresAnyAlternativesSent() throws Exception {
        Map<String, Object> body = AdminFixtures.ce("Julgue C2.", subject.getId(), board.getId(), agency.getId());
        body.put("options", Map.of("A", "lixo"));
        UUID id = idOf(post(body, "ADMIN").andExpect(status().isCreated()));

        Map<String, Object> row = jdbc.queryForMap(
                "SELECT tipo::text AS tipo, opcao_a, gabarito FROM questao WHERE id = ?", id);
        assertThat(row.get("tipo")).isEqualTo("CERTO_ERRADO");
        assertThat(row.get("opcao_a")).isNull();
        assertThat(row.get("gabarito")).isEqualTo("CERTO");
    }

    @Test
    void aDuplicateIs409WithTheExistingIdAndWritesNothing() throws Exception {
        // Same enunciado and alternativas after the database's normalisation: case,
        // spacing and alternative order differ, content_key does not.
        UUID existing = AdminFixtures.seedMc(jdbc, "seed_dup_c3", "  enunciado   C3 ", "A",
                "alternativa b de enunciado c3", "Alternativa A de Enunciado C3",
                "Alternativa D de Enunciado C3", "Alternativa C de Enunciado C3", null);
        long before = manualRows();

        post(mc("Enunciado C3"), "ADMIN")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE"))
                .andExpect(jsonPath("$.existingId").value(existing.toString()));
        assertThat(manualRows()).isEqualTo(before);
    }

    @Test
    void aTypedNameCreatesACanonicalOnceAndItsVariantReusesIt() throws Exception {
        Map<String, Object> first = mc("Enunciado C4a");
        first.put("agency", AdminFixtures.named("Tribunal de Contas C4"));
        Map<String, Object> second = mc("Enunciado C4b");
        second.put("agency", AdminFixtures.named("  tribunal de   contas c4 "));

        UUID a = idOf(post(first, "ADMIN").andExpect(status().isCreated()));
        UUID b = idOf(post(second, "ADMIN").andExpect(status().isCreated()));

        assertThat(jdbc.queryForObject("SELECT count(*) FROM orgao WHERE nome = 'Tribunal de Contas C4'", Long.class))
                .isEqualTo(1L);
        assertThat(jdbc.queryForObject("SELECT orgao_id FROM questao WHERE id = ?", Integer.class, a))
                .isEqualTo(jdbc.queryForObject("SELECT orgao_id FROM questao WHERE id = ?", Integer.class, b));
    }

    @Test
    void theSameDisciplinaTwiceBecomesOneLink() throws Exception {
        // Review Focus 4.
        Map<String, Object> body = mc("Enunciado C5");
        body.put("subjects", List.of(AdminFixtures.ref(subject.getId()), AdminFixtures.ref(subject.getId()),
                AdminFixtures.named("DISCIPLINA c")));
        UUID id = idOf(post(body, "ADMIN").andExpect(status().isCreated()));

        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM questao_disciplina WHERE source_id = ?", Long.class,
                AdminFixtures.sourceIdOf(jdbc, id))).isEqualTo(1L);
    }

    @Test
    void aFieldErrorIs400WithItsKey() throws Exception {
        Map<String, Object> body = mc("Enunciado C6");
        @SuppressWarnings("unchecked")
        Map<String, Object> options = (Map<String, Object>) body.get("options");
        options.put("C", "   ");

        post(body, "ADMIN")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION"))
                .andExpect(jsonPath("$.fieldErrors['options.C']").exists());
    }

    @Test
    void anUnknownDisciplinaIdIs400() throws Exception {
        Map<String, Object> body = mc("Enunciado C7");
        body.put("subjects", List.of(AdminFixtures.ref(-1)));
        post(body, "ADMIN")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.subjects").exists());
    }

    @Test
    void theCurrentYearInBrasiliaIsAcceptedAndTheNextIsNot() throws Exception {
        int thisYear = Year.now(ZoneId.of("America/Sao_Paulo")).getValue();
        Map<String, Object> ok = mc("Enunciado C8a");
        ok.put("year", thisYear);
        post(ok, "ADMIN").andExpect(status().isCreated());

        Map<String, Object> tooLate = mc("Enunciado C8b");
        tooLate.put("year", thisYear + 1);
        post(tooLate, "ADMIN")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.year").exists());
    }

    @Test
    void anUnknownQuestionIs404() throws Exception {
        mvc.perform(get("/api/admin/questions/{id}", UUID.randomUUID())
                        .header("Authorization", AdminFixtures.bearer(jwt, "ADMIN")))
                .andExpect(status().isNotFound());
    }

    @Test
    void responsesNeverCarryTheSourceId() throws Exception {
        UUID id = idOf(post(mc("Enunciado C10"), "ADMIN").andExpect(status().isCreated()));
        String body = mvc.perform(get("/api/admin/questions/{id}", id)
                        .header("Authorization", AdminFixtures.bearer(jwt, "ADMIN")))
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("manual:").doesNotContain("sourceId");
    }

    @Test
    void nonAdminsAreRefused() throws Exception {
        post(mc("Enunciado C11"), "USER").andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/questions/{id}", UUID.randomUUID())
                        .header("Authorization", AdminFixtures.bearer(jwt, "USER")))
                .andExpect(status().isForbidden());
        mvc.perform(multipart("/api/admin/questions").file(AdminFixtures.json(json, mc("Enunciado C11b"))))
                .andExpect(status().isUnauthorized());
    }
}
