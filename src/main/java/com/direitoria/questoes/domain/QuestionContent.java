package com.direitoria.questoes.domain;

import java.util.List;
import java.util.Set;

/**
 * Everything an admin sets on a questão, already validated and with its canonicals
 * resolved. Built by AdminQuestionService; consumed by Question.createManual/edit.
 * Blank optional text arrives here as null, except `role`, which is "" (cargo is NOT
 * NULL). Options are null for CERTO_ERRADO.
 */
public record QuestionContent(
        QuestionType type,
        String referenceText,
        String statement,
        String optionA,
        String optionB,
        String optionC,
        String optionD,
        String optionE,
        String answer,
        String commentary,
        List<Subject> subjects,
        ExamBoard examBoard,
        Agency agency,
        short year,
        String role,
        Difficulty difficulty) {

    /** Letters with an alternative, in order. Empty for CERTO_ERRADO. */
    public Set<String> filledLetters() {
        return type == QuestionType.CERTO_ERRADO
                ? Set.of()
                : Question.letters(optionA, optionB, optionC, optionD, optionE);
    }
}
