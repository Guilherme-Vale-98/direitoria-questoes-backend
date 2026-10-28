package com.direitoria.questoes;

import com.direitoria.questoes.domain.Agency;
import com.direitoria.questoes.domain.Difficulty;
import com.direitoria.questoes.domain.ExamBoard;
import com.direitoria.questoes.domain.Question;
import com.direitoria.questoes.domain.QuestionContent;
import com.direitoria.questoes.domain.QuestionLockedException;
import com.direitoria.questoes.domain.QuestionType;
import com.direitoria.questoes.domain.Subject;
import com.direitoria.questoes.repository.AgencyRepository;
import com.direitoria.questoes.repository.ExamBoardRepository;
import com.direitoria.questoes.repository.SubjectRepository;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.catchThrowable;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class QuestionWriteTest {

    @Autowired EntityManager em;
    @Autowired JdbcTemplate jdbc;
    @Autowired SubjectRepository subjects;
    @Autowired ExamBoardRepository boards;
    @Autowired AgencyRepository agencies;

    Subject penal;
    Subject civil;
    ExamBoard fgv;
    Agency tce;

    @BeforeEach
    void canonicals() {
        penal = subjects.save(new Subject("Direito Penal W"));
        civil = subjects.save(new Subject("Direito Civil W"));
        fgv = boards.save(new ExamBoard("FGV W"));
        tce = agencies.save(new Agency("TCE W"));
    }

    private QuestionContent mc(String statement, String e, String answer) {
        return new QuestionContent(QuestionType.MULTIPLA_ESCOLHA, null, statement,
                "alfa", "beta", "gama", "delta", e, answer, "Comentário.",
                List.of(penal, civil), fgv, tce, (short) 2024, "Analista", Difficulty.MODERADO);
    }

    private QuestionContent ce(String statement, String answer) {
        return new QuestionContent(QuestionType.CERTO_ERRADO, "Texto de apoio.", statement,
                null, null, null, null, null, answer, null,
                List.of(penal), fgv, tce, (short) 2023, "", null);
    }

    private Question persist(QuestionContent c) {
        Question q = Question.createManual(c);
        em.persist(q);
        em.flush();
        return q;
    }

    @Test
    void createManualWritesACompleteRow() {
        Question q = persist(mc("Enunciado W1", null, "c"));

        assertThat(q.getSourceId()).startsWith("manual:");
        Map<String, Object> row = jdbc.queryForMap(
                "SELECT id, content_key, banca, orgao, materia, assunto, cargo, figura_ref, gabarito,"
                        + " opcao_a, opcao_e, tipo::text AS tipo, nivel::text AS nivel, ano, texto_ref, comentario"
                        + " FROM questao WHERE source_id = ?", q.getSourceId());
        assertThat(row.get("id")).isNotNull();
        assertThat(row.get("content_key")).isNotNull();
        assertThat(row.get("banca")).isEqualTo("FGV W");
        assertThat(row.get("orgao")).isEqualTo("TCE W");
        assertThat(row.get("materia")).isEqualTo("Direito Penal W / Direito Civil W");
        assertThat(row.get("assunto")).isEqualTo("");
        assertThat(row.get("cargo")).isEqualTo("Analista");
        assertThat(row.get("figura_ref")).isNull();
        assertThat(row.get("gabarito")).isEqualTo("C");
        assertThat(row.get("opcao_a")).isEqualTo("alfa");
        assertThat(row.get("opcao_e")).isNull();
        assertThat(row.get("tipo")).isEqualTo("MULTIPLA_ESCOLHA");
        assertThat(row.get("nivel")).isEqualTo("MODERADO");
        assertThat(((Number) row.get("ano")).intValue()).isEqualTo(2024);
        assertThat(row.get("comentario")).isEqualTo("Comentário.");
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM questao_disciplina WHERE source_id = ?", Long.class, q.getSourceId()))
                .isEqualTo(2L);
    }

    @Test
    void certoErradoStoresNoAlternativesAndANormalisedGabarito() {
        Question q = persist(ce("Julgue W2.", "certo"));

        Map<String, Object> row = jdbc.queryForMap(
                "SELECT opcao_a, opcao_b, gabarito, cargo, nivel, texto_ref FROM questao WHERE source_id = ?",
                q.getSourceId());
        assertThat(row.get("opcao_a")).isNull();
        assertThat(row.get("opcao_b")).isNull();
        assertThat(row.get("gabarito")).isEqualTo("CERTO");
        assertThat(row.get("cargo")).isEqualTo("");
        assertThat(row.get("nivel")).isNull();
        assertThat(row.get("texto_ref")).isEqualTo("Texto de apoio.");
    }

    @Test
    void editRewritesContentLinksAndRawColumns() {
        Question q = persist(mc("Enunciado W3", null, "A"));
        Subject adm = subjects.save(new Subject("Direito Administrativo W"));
        QuestionContent next = new QuestionContent(QuestionType.MULTIPLA_ESCOLHA, null, "Enunciado W3 revisto",
                "alfa", "beta", "gama", "delta", "épsilon", "E", null,
                List.of(adm), fgv, tce, (short) 2025, "Técnico", Difficulty.DIFICIL);

        q.edit(next, false);
        em.flush();

        Map<String, Object> row = jdbc.queryForMap(
                "SELECT enunciado, opcao_e, gabarito, materia, cargo FROM questao WHERE source_id = ?",
                q.getSourceId());
        assertThat(row.get("enunciado")).isEqualTo("Enunciado W3 revisto");
        assertThat(row.get("opcao_e")).isEqualTo("épsilon");
        assertThat(row.get("gabarito")).isEqualTo("E");
        assertThat(row.get("materia")).isEqualTo("Direito Administrativo W");
        assertThat(row.get("cargo")).isEqualTo("Técnico");
        assertThat(jdbc.queryForList(
                "SELECT disciplina_id FROM questao_disciplina WHERE source_id = ?", Integer.class, q.getSourceId()))
                .containsExactly(adm.getId());
    }

    @Test
    void withoutTentativasNothingIsLocked() {
        Question q = persist(mc("Enunciado W4", null, "A"));
        assertThatNoException().isThrownBy(() ->
                q.assertEditable(QuestionType.CERTO_ERRADO, Set.of(), false));
    }

    @Test
    void onceAnsweredTheTypeIsLocked() {
        Question q = persist(mc("Enunciado W5", null, "A"));
        Throwable t = catchThrowable(() -> q.assertEditable(QuestionType.CERTO_ERRADO, Set.of(), true));
        assertThat(t).isInstanceOf(QuestionLockedException.class);
        assertThat(((QuestionLockedException) t).getFieldErrors()).containsKey("type");
    }

    @Test
    void onceAnsweredTheSetOfAlternativesIsLocked() {
        Question q = persist(mc("Enunciado W6", null, "A"));
        Throwable t = catchThrowable(() -> q.assertEditable(
                QuestionType.MULTIPLA_ESCOLHA, Set.of("A", "B", "C", "D", "E"), true));
        assertThat(t).isInstanceOf(QuestionLockedException.class);
        assertThat(((QuestionLockedException) t).getFieldErrors()).containsKey("options");
    }

    @Test
    void onceAnsweredTheSameLettersMayStillBeEdited() {
        Question q = persist(mc("Enunciado W7", null, "A"));
        assertThatNoException().isThrownBy(() -> q.assertEditable(
                QuestionType.MULTIPLA_ESCOLHA, Set.of("A", "B", "C", "D"), true));
        assertThat(q.filledLetters()).containsExactly("A", "B", "C", "D");
    }

    @Test
    void editEnforcesTheLockItself() {
        Question q = persist(mc("Enunciado W8", null, "A"));
        Throwable t = catchThrowable(() -> q.edit(ce("Enunciado W8", "CERTO"), true));
        assertThat(t).isInstanceOf(QuestionLockedException.class);
    }
}
