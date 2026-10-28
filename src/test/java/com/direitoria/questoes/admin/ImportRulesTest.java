package com.direitoria.questoes.admin;

import com.direitoria.questoes.admin.dto.LookupRef;
import com.direitoria.questoes.domain.QuestionType;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ImportRulesTest {

    private static Map<String, String> options(String a, String b, String c, String d, String e) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("A", a);
        m.put("B", b);
        m.put("C", c);
        m.put("D", d);
        m.put("E", e);
        return m;
    }

    private static ImportJson.Draft mc(String statement, String answer, List<LookupRef> subjects) {
        return new ImportJson.Draft(null, statement, options("a", "b", "c", "d", null), answer, null, subjects, null);
    }

    private static final ImportJson.Gabarito GAB = new ImportJson.Gabarito("g.pdf", List.of(
            new ImportJson.Section(0, "Básicos", null, Map.of("1", "A", "2", "ANULADA")),
            new ImportJson.Section(1, "Cargo 1", null, Map.of("3", "C"))), List.of(), null);

    @Test
    void chosenSectionsMergeAndAnuladaIsKnownFromThem() {
        Map<String, String> answers = ImportRules.chosenAnswers(GAB, List.of(0, 1));
        assertThat(answers).containsEntry("1", "A").containsEntry("3", "C");
        assertThat(ImportRules.anulada(answers, 2)).isTrue();
        assertThat(ImportRules.anulada(answers, 1)).isFalse();
        assertThat(ImportRules.chosenAnswers(GAB, List.of())).isEmpty();
        assertThat(ImportRules.chosenAnswers(null, List.of(0))).isEmpty();
    }

    @Test
    void anOverrideWinsThenTheSectionAndAnAnuladaHasNoGabarito() {
        assertThat(ImportRules.effectiveAnswer(mc("x", " b ", null), "A")).isEqualTo("B");
        assertThat(ImportRules.answerSource(mc("x", "B", null), "A")).isEqualTo("ADMIN");
        assertThat(ImportRules.effectiveAnswer(mc("x", null, null), "A")).isEqualTo("A");
        assertThat(ImportRules.answerSource(mc("x", null, null), "A")).isEqualTo("GABARITO");
        assertThat(ImportRules.effectiveAnswer(mc("x", null, null), "ANULADA")).isNull();
        assertThat(ImportRules.effectiveAnswer(mc("x", "", null), null)).isNull();
        assertThat(ImportRules.answerSource(mc("x", null, null), null)).isEqualTo("NONE");
    }

    @Test
    void disciplinasFollowTheRangeUnlessOverridden() {
        List<ImportJson.Range> ranges = List.of(new ImportJson.Range(1, 10, new LookupRef(7, null), "LP"),
                new ImportJson.Range(11, 20, null, "ESPECÍFICOS"));
        assertThat(ImportRules.effectiveSubjects(mc("x", null, null), ranges, 5)).containsExactly(new LookupRef(7, null));
        assertThat(ImportRules.effectiveSubjects(mc("x", null, List.of()), ranges, 5)).containsExactly(new LookupRef(7, null));
        assertThat(ImportRules.effectiveSubjects(mc("x", null, List.of(new LookupRef(9, null))), ranges, 5))
                .containsExactly(new LookupRef(9, null));
        assertThat(ImportRules.effectiveSubjects(mc("x", null, null), ranges, 15)).isEmpty();
        assertThat(ImportRules.effectiveSubjects(mc("x", null, null), ranges, 99)).isEmpty();
    }

    @Test
    void aCompleteQuestaoHasNoPendencia() {
        assertThat(ImportRules.pendencias(QuestionType.MULTIPLA_ESCOLHA, mc("Enunciado", null, null), "C",
                List.of(new LookupRef(1, null)))).isEmpty();
    }

    @Test
    void pendenciasNameWhatBlocksTheCommit() {
        ImportJson.Draft unreadable = mc("Texto com " + ImportRules.UNREADABLE, null, null);
        assertThat(ImportRules.pendencias(QuestionType.MULTIPLA_ESCOLHA, unreadable, null, List.of()))
                .extracting(ImportViews.Pendencia::code)
                .containsExactly("SEM_GABARITO", "ILEGIVEL", "SEM_DISCIPLINA");
    }

    @Test
    void threeBsRulesBecomeInvalidaPendencias() {
        ImportJson.Draft missingD = new ImportJson.Draft(null, "Enunciado", options("a", "b", "c", null, null), null, null, null, null);
        assertThat(ImportRules.pendencias(QuestionType.MULTIPLA_ESCOLHA, missingD, "E", List.of(new LookupRef(1, null))))
                .extracting(ImportViews.Pendencia::code, ImportViews.Pendencia::field)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple("INVALIDA", "options.D"),
                        org.assertj.core.groups.Tuple.tuple("INVALIDA", "answer"));
    }

    @Test
    void aCertoErradoGabaritoMustBeCertoOrErrado() {
        ImportJson.Draft ce = new ImportJson.Draft(null, "Item", null, null, null, null, null);
        assertThat(ImportRules.pendencias(QuestionType.CERTO_ERRADO, ce, "CERTO", List.of(new LookupRef(1, null)))).isEmpty();
    }

    @Test
    void theMarkerIsFoundInAnyText() {
        assertThat(ImportRules.unreadable(new ImportJson.Draft("apoio " + ImportRules.UNREADABLE, "x", null, null, null, null, null))).isTrue();
        assertThat(ImportRules.unreadable(new ImportJson.Draft(null, "x", options("a", "b" + ImportRules.UNREADABLE, "c", "d", null), null, null, null, null))).isTrue();
        assertThat(ImportRules.unreadable(mc("limpo", null, null))).isFalse();
    }
}
