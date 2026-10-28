package com.direitoria.questoes.admin;

import com.direitoria.questoes.admin.AdminQuestionValidator.Draft;
import com.direitoria.questoes.admin.AdminQuestionValidator.Lock;
import com.direitoria.questoes.admin.dto.AdminQuestionRequest;
import com.direitoria.questoes.admin.dto.LookupRef;
import com.direitoria.questoes.domain.Difficulty;
import com.direitoria.questoes.domain.QuestionType;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AdminQuestionValidatorTest {

    private static final int CURRENT_YEAR = 2026;

    /** A mutable request builder: each test changes only what it is about. */
    private static final class Req {
        String type = "MULTIPLA_ESCOLHA";
        String statement = "  Enunciado  ";
        Map<String, String> options = opts("A", "B", "C", "D");
        String answer = "c";
        List<LookupRef> subjects = List.of(new LookupRef(1, null));
        LookupRef examBoard = new LookupRef(2, null);
        LookupRef agency = new LookupRef(3, null);
        Integer year = 2024;
        String difficulty = null;

        AdminQuestionRequest build() {
            return new AdminQuestionRequest(type, "   ", statement, options, answer, " ",
                    subjects, examBoard, agency, year, "  Analista ", difficulty, List.of(), null);
        }
    }

    private static Map<String, String> opts(String... letters) {
        Map<String, String> m = new LinkedHashMap<>();
        for (String l : letters) {
            m.put(l, "Texto " + l);
        }
        return m;
    }

    private static Draft valid(Req r, Lock lock) {
        return AdminQuestionValidator.validate(r.build(), lock, CURRENT_YEAR);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> errorsOf(Req r, Lock lock) {
        try {
            AdminQuestionValidator.validate(r.build(), lock, CURRENT_YEAR);
        } catch (AdminApiException e) {
            return (Map<String, String>) e.getBody().get("fieldErrors");
        }
        throw new AssertionError("expected a validation error");
    }

    @Test
    void aValidMultiplaEscolhaComesBackTrimmedAndNormalised() {
        Draft d = valid(new Req(), null);
        assertThat(d.type()).isEqualTo(QuestionType.MULTIPLA_ESCOLHA);
        assertThat(d.statement()).isEqualTo("Enunciado");
        assertThat(d.answer()).isEqualTo("C");
        assertThat(d.option("E")).isNull();
        assertThat(d.filledLetters()).containsExactly("A", "B", "C", "D");
        assertThat(d.referenceText()).isNull();
        assertThat(d.commentary()).isNull();
        assertThat(d.role()).isEqualTo("Analista");
        assertThat(d.year()).isEqualTo((short) 2024);
    }

    @Test
    void aMissingOrBlankAlternativeAmongAToDIsAFieldError() {
        Req r = new Req();
        r.options = opts("A", "D");
        r.options.put("B", "   ");
        assertThat(errorsOf(r, null)).containsKeys("options.B", "options.C").doesNotContainKey("options.A");
    }

    @Test
    void alternativeEIsOptionalAndKeptWhenFilled() {
        Req r = new Req();
        r.options = opts("A", "B", "C", "D", "E");
        r.answer = "E";
        assertThat(valid(r, null).option("E")).isEqualTo("Texto E");
    }

    @Test
    void theAnswerMustBeAFilledLetter() {
        Req r = new Req();
        r.answer = "E";
        assertThat(errorsOf(r, null)).containsKey("answer");
        r.answer = "Z";
        assertThat(errorsOf(r, null)).containsKey("answer");
        r.answer = null;
        assertThat(errorsOf(r, null)).containsKey("answer");
    }

    @Test
    void certoErradoIgnoresAlternativesAndNeedsCertoOrErrado() {
        Req r = new Req();
        r.type = "CERTO_ERRADO";
        r.answer = " errado ";
        Draft d = valid(r, null);
        assertThat(d.answer()).isEqualTo("ERRADO");
        assertThat(d.filledLetters()).isEmpty();
        r.answer = "A";
        assertThat(errorsOf(r, null)).containsKey("answer");
    }

    @Test
    void theTypeIsRequiredAndMustBeKnown() {
        Req r = new Req();
        r.type = null;
        assertThat(errorsOf(r, null)).containsKey("type");
        r.type = "DISCURSIVA";
        assertThat(errorsOf(r, null)).containsKey("type");
    }

    @Test
    void aBlankStatementIsAFieldError() {
        Req r = new Req();
        r.statement = "  \n ";
        assertThat(errorsOf(r, null)).containsKey("statement");
    }

    @Test
    void classificationIsRequired() {
        Req r = new Req();
        r.subjects = List.of();
        r.examBoard = null;
        r.agency = null;
        assertThat(errorsOf(r, null)).containsKeys("subjects", "examBoard", "agency");
    }

    @Test
    void theYearRunsFrom1990ToTheCurrentYear() {
        Req r = new Req();
        r.year = null;
        assertThat(errorsOf(r, null)).containsKey("year");
        r.year = 1989;
        assertThat(errorsOf(r, null)).containsKey("year");
        r.year = CURRENT_YEAR + 1;
        assertThat(errorsOf(r, null)).containsKey("year");
        r.year = 1990;
        assertThat(valid(r, null).year()).isEqualTo((short) 1990);
        r.year = CURRENT_YEAR;
        assertThat(valid(r, null).year()).isEqualTo((short) CURRENT_YEAR);
    }

    @Test
    void theDifficultyIsOptionalButMustBeKnown() {
        Req r = new Req();
        r.difficulty = "DIFICIL";
        assertThat(valid(r, null).difficulty()).isEqualTo(Difficulty.DIFICIL);
        r.difficulty = "IMPOSSIVEL";
        assertThat(errorsOf(r, null)).containsKey("difficulty");
    }

    @Test
    void onceAnsweredTheTypeCannotChange() {
        Req r = new Req();
        r.type = "CERTO_ERRADO";
        r.answer = "CERTO";
        Lock lock = new Lock(QuestionType.MULTIPLA_ESCOLHA, Set.of("A", "B", "C", "D"));
        assertThat(errorsOf(r, lock)).containsKey("type");
    }

    @Test
    void onceAnsweredLettersCannotBeAddedOrRemoved() {
        Lock abcde = new Lock(QuestionType.MULTIPLA_ESCOLHA, Set.of("A", "B", "C", "D", "E"));
        assertThat(errorsOf(new Req(), abcde).get("options.E")).contains("remover");

        Req withE = new Req();
        withE.options = opts("A", "B", "C", "D", "E");
        Lock abcd = new Lock(QuestionType.MULTIPLA_ESCOLHA, Set.of("A", "B", "C", "D"));
        assertThat(errorsOf(withE, abcd).get("options.E")).contains("adicionar");
    }

    @Test
    void anAnsweredLegacyRowKeepsItsOwnLetters() {
        // Review Focus 1 — e.g. a QAPI row that only ever had A, D and E.
        Req r = new Req();
        r.options = opts("A", "D", "E");
        r.answer = "D";
        Lock gap = new Lock(QuestionType.MULTIPLA_ESCOLHA, Set.of("A", "D", "E"));
        assertThat(valid(r, gap).filledLetters()).containsExactly("A", "D", "E");
    }
}
