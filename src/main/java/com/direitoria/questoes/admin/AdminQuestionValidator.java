package com.direitoria.questoes.admin;

import com.direitoria.questoes.admin.dto.AdminQuestionRequest;
import com.direitoria.questoes.domain.Difficulty;
import com.direitoria.questoes.domain.Judging;
import com.direitoria.questoes.domain.QuestionType;
import java.time.Year;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The rules of Bloco 3B spec §4, plus the lock rules of §5 for questões that already
 * have tentativas. Pure — no database. Canonical ids are checked later, by
 * CanonicalResolver, because resolving a typed name writes.
 */
final class AdminQuestionValidator {

    static final int MIN_YEAR = 1990;
    /** The platform's day boundary (CONTEXT.md, "Ofensiva"). */
    static final ZoneId PLATFORM_ZONE = ZoneId.of("America/Sao_Paulo");
    static final List<String> LETTERS = List.of("A", "B", "C", "D", "E");
    private static final Set<String> REQUIRED_LETTERS = Set.of("A", "B", "C", "D");

    /** What an answered questão may not change: its type and its letters (docs/adr/0009). */
    record Lock(QuestionType type, Set<String> letters) {
    }

    /** Validated, trimmed values. Canonicals are still refs in the request. */
    record Draft(
            QuestionType type,
            String referenceText,
            String statement,
            Map<String, String> options,
            String answer,
            String commentary,
            short year,
            String role,
            Difficulty difficulty) {

        String option(String letter) {
            return options.get(letter);
        }

        Set<String> filledLetters() {
            Set<String> s = new LinkedHashSet<>();
            for (String l : LETTERS) {
                if (options.get(l) != null) {
                    s.add(l);
                }
            }
            return s;
        }
    }

    private AdminQuestionValidator() {
    }

    static int currentYear() {
        return Year.now(PLATFORM_ZONE).getValue();
    }

    /**
     * @param lock the questão's current type and letters when it already has
     *             tentativas; null on create and on an unanswered questão. An answered
     *             questão must keep exactly the letters it has — which is also what keeps
     *             legacy rows with irregular letters (e.g. A, D, E) editable.
     */
    static Draft validate(AdminQuestionRequest r, Lock lock, int currentYear) {
        Map<String, String> errors = new LinkedHashMap<>();

        QuestionType type = parseType(r.type(), errors);
        if (lock != null && type != null && type != lock.type()) {
            errors.put("type", "O tipo não pode mudar: a questão já tem tentativas.");
        }
        boolean typeOk = type != null && !errors.containsKey("type");

        String statement = trimToNull(r.statement());
        if (statement == null) {
            errors.put("statement", "Escreva o enunciado.");
        }

        Map<String, String> options = new LinkedHashMap<>();
        for (String l : LETTERS) {
            options.put(l, null);
        }
        if (typeOk && type == QuestionType.MULTIPLA_ESCOLHA) {
            Map<String, String> in = r.options() == null ? Map.of() : r.options();
            for (String l : LETTERS) {
                options.put(l, trimToNull(in.get(l)));
            }
            for (String l : LETTERS) {
                boolean filled = options.get(l) != null;
                if (lock == null) {
                    if (!filled && REQUIRED_LETTERS.contains(l)) {
                        errors.put("options." + l, "Preencha a alternativa " + l + ".");
                    }
                } else if (!filled && lock.letters().contains(l)) {
                    errors.put("options." + l, "Não é possível remover a alternativa " + l + ": a questão já tem tentativas.");
                } else if (filled && !lock.letters().contains(l)) {
                    errors.put("options." + l, "Não é possível adicionar a alternativa " + l + ": a questão já tem tentativas.");
                }
            }
        }

        String answer = Judging.normalize(r.answer());
        if (typeOk && type == QuestionType.MULTIPLA_ESCOLHA
                && (!LETTERS.contains(answer) || options.get(answer) == null)) {
            errors.put("answer", "Marque o gabarito entre as alternativas preenchidas.");
        }
        if (typeOk && type == QuestionType.CERTO_ERRADO && !answer.equals("CERTO") && !answer.equals("ERRADO")) {
            errors.put("answer", "Marque Certo ou Errado.");
        }

        if (r.subjects() == null || r.subjects().isEmpty()) {
            errors.put("subjects", "Escolha ao menos uma disciplina.");
        }
        if (r.examBoard() == null) {
            errors.put("examBoard", "Informe a banca.");
        }
        if (r.agency() == null) {
            errors.put("agency", "Informe o órgão.");
        }

        Integer year = r.year();
        if (year == null) {
            errors.put("year", "Informe o ano.");
        } else if (year < MIN_YEAR || year > currentYear) {
            errors.put("year", "O ano deve estar entre " + MIN_YEAR + " e " + currentYear + ".");
        }

        Difficulty difficulty = parseDifficulty(r.difficulty(), errors);

        if (!errors.isEmpty()) {
            throw AdminApiException.validation(errors);
        }
        return new Draft(type, trimToNull(r.referenceText()), statement, options, answer,
                trimToNull(r.commentary()), year.shortValue(),
                r.role() == null ? "" : r.role().strip(), difficulty);
    }

    private static QuestionType parseType(String raw, Map<String, String> errors) {
        if (raw == null || raw.isBlank()) {
            errors.put("type", "Escolha o tipo da questão.");
            return null;
        }
        try {
            return QuestionType.valueOf(raw.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            errors.put("type", "Tipo inválido.");
            return null;
        }
    }

    private static Difficulty parseDifficulty(String raw, Map<String, String> errors) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Difficulty.valueOf(raw.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            errors.put("difficulty", "Dificuldade inválida.");
            return null;
        }
    }

    /** Trims the ends (inner newlines stay); blank becomes null. */
    private static String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.strip();
        return t.isEmpty() ? null : t;
    }
}
