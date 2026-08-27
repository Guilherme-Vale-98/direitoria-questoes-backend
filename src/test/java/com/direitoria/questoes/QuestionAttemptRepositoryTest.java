package com.direitoria.questoes;

import com.direitoria.questoes.domain.QuestionAttempt;
import com.direitoria.questoes.repository.QuestionAttemptRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class QuestionAttemptRepositoryTest {

    @Autowired QuestionAttemptRepository attempts;
    @Autowired JdbcTemplate jdbc;

    private UUID newUser() {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO users (id, first_name, last_name, email, password)"
                + " VALUES (?, 'A', 'B', ?, 'x')", id, "u_" + id + "@x.com");
        return id;
    }

    private String newQuestao(String src) {
        jdbc.update("INSERT INTO questao (id, source_id, tipo, enunciado, opcao_a, opcao_b, gabarito,"
                + " banca, orgao, cargo, ano, materia, assunto) VALUES"
                + " (?, ?, 'MULTIPLA_ESCOLHA'::tipo_questao, ?, 'A', 'B', 'A', 'FGV', 'O', 'C', 2024, 'M', 'A')",
                UUID.randomUUID(), src, "Enunciado " + src);
        return src;
    }

    private int newDisciplina(String nome) {
        return jdbc.queryForObject(
                "INSERT INTO disciplina (nome) VALUES (?) ON CONFLICT (nome) DO UPDATE SET nome = EXCLUDED.nome"
                        + " RETURNING id", Integer.class, nome);
    }

    /** Insert directly so the test controls answered_at, which the entity does not set. */
    private void attempt(UUID user, String src, boolean correct, int daysAgo) {
        jdbc.update("INSERT INTO question_attempt (user_id, question_source_id, chosen_answer, is_correct, answered_at)"
                + " VALUES (?, ?, 'A', ?, now() - make_interval(days => ?))", user, src, correct, daysAgo);
    }

    @Test
    void overallCountsDistinctQuestoesAndTheUltimaTentativaWins() {
        UUID u = newUser();
        String q1 = newQuestao("qa1_" + UUID.randomUUID());
        String q2 = newQuestao("qa2_" + UUID.randomUUID());

        // q1: errou ontem, acertou hoje -> a última vale, conta 1 acerto
        attempt(u, q1, false, 1);
        attempt(u, q1, true, 0);
        // q2: uma única tentativa, errada
        attempt(u, q2, false, 0);

        var overall = attempts.overall(u);
        assertThat(overall.getAnswered()).isEqualTo(2); // duas questões distintas, não três tentativas
        assertThat(overall.getCorrect()).isEqualTo(1);
    }

    @Test
    void bySubjectCountsAMultiDisciplinaQuestaoInEachDisciplina() {
        UUID u = newUser();
        String q = newQuestao("qa3_" + UUID.randomUUID());
        String penalName = "Direito Penal " + UUID.randomUUID();
        String civilName = "Direito Civil " + UUID.randomUUID();
        int penal = newDisciplina(penalName);
        int civil = newDisciplina(civilName);
        jdbc.update("INSERT INTO questao_disciplina (source_id, disciplina_id) VALUES (?, ?), (?, ?)",
                q, penal, q, civil);
        attempt(u, q, true, 0);

        List<QuestionAttemptRepository.SubjectStatProjection> rows = attempts.bySubject(u);
        assertThat(rows).hasSize(2);
        assertThat(rows).allSatisfy(row -> {
            assertThat(row.getSubjectId()).isNotNull();
            assertThat(row.getSubjectName()).isNotNull();
            assertThat(row.getAnswered()).isEqualTo(1);
        });
        assertThat(rows).extracting(QuestionAttemptRepository.SubjectStatProjection::getSubjectId)
                .containsExactlyInAnyOrder(penal, civil);
        assertThat(rows).extracting(QuestionAttemptRepository.SubjectStatProjection::getSubjectName)
                .containsExactlyInAnyOrder(penalName, civilName);
    }

    @Test
    void activeDatesAreDistinctAndNewestFirst() {
        UUID u = newUser();
        String q = newQuestao("qa4_" + UUID.randomUUID());
        attempt(u, q, true, 0);
        attempt(u, q, true, 0); // same BRT day -> one date
        attempt(u, q, false, 2);

        List<LocalDate> dates = attempts.activeDatesDesc(u);
        assertThat(dates).hasSize(2);
        assertThat(dates.get(0)).isAfter(dates.get(1));
    }

    @Test
    void weeklyVolumeCountsDistinctQuestoesInsideTheWindowOnly() {
        UUID u = newUser();
        String recent = newQuestao("qa5_" + UUID.randomUUID());
        String old = newQuestao("qa6_" + UUID.randomUUID());
        attempt(u, recent, true, 1);
        attempt(u, recent, true, 1); // twice -> still one questão
        attempt(u, old, true, 30);

        LocalDate from = LocalDate.now(java.time.ZoneId.of("America/Sao_Paulo")).minusDays(6);
        assertThat(attempts.distinctQuestionsSince(u, from)).isEqualTo(1);
    }

    @Test
    void savePersistsATentativa() {
        UUID u = newUser();
        String q = newQuestao("qa7_" + UUID.randomUUID());
        attempts.saveAndFlush(new QuestionAttempt(u, q, "B", false));

        Long n = jdbc.queryForObject(
                "SELECT count(*) FROM question_attempt WHERE user_id = ?", Long.class, u);
        assertThat(n).isEqualTo(1);
    }
}
