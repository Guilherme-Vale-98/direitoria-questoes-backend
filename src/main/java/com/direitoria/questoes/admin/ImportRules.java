package com.direitoria.questoes.admin;

import com.direitoria.questoes.admin.dto.AdminQuestionRequest;
import com.direitoria.questoes.admin.dto.LookupRef;
import com.direitoria.questoes.domain.Judging;
import com.direitoria.questoes.domain.QuestionType;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The pure rules of a draft questão (Bloco 3C spec §3, "Include rules"; §4): its effective
 * gabarito and disciplinas, whether it is anulada, and what blocks its commit. The overview,
 * the review page and the commit all read these — one rule, three readers.
 */
final class ImportRules {

    /** What the browser parser puts where it could not trust a glyph (spec §2). */
    static final String UNREADABLE = "⟦?⟧";
    static final String ANULADA = "ANULADA";

    private ImportRules() {
    }

    /** The chosen sections' answers merged: questão number → normalised answer. */
    static Map<String, String> chosenAnswers(ImportJson.Gabarito g, List<Integer> keys) {
        Map<String, String> out = new HashMap<>();
        if (g == null || g.sections() == null || keys == null) {
            return out;
        }
        for (ImportJson.Section s : g.sections()) {
            if (keys.contains(s.key()) && s.answers() != null) {
                out.putAll(s.answers());
            }
        }
        return out;
    }

    /** The section's answer for a questão: a letter, CERTO/ERRADO, ANULADA, or null. */
    static String pdfAnswer(Map<String, String> answers, int number) {
        return answers.get(String.valueOf(number));
    }

    static boolean anulada(Map<String, String> answers, int number) {
        return ANULADA.equals(pdfAnswer(answers, number));
    }

    /** The admin's override, else the section's answer; an anulada has none until the admin picks one. */
    static String effectiveAnswer(ImportJson.Draft d, String pdfAnswer) {
        String own = Judging.normalize(d.answer());
        if (!own.isEmpty()) {
            return own;
        }
        return pdfAnswer == null || ANULADA.equals(pdfAnswer) ? null : pdfAnswer;
    }

    /** ADMIN, GABARITO or NONE — where the effective gabarito came from. */
    static String answerSource(ImportJson.Draft d, String pdfAnswer) {
        if (!Judging.normalize(d.answer()).isEmpty()) {
            return "ADMIN";
        }
        return effectiveAnswer(d, pdfAnswer) == null ? "NONE" : "GABARITO";
    }

    /** The disciplina range covering `number`, or null. */
    static ImportJson.Range range(List<ImportJson.Range> ranges, int number) {
        for (ImportJson.Range r : ImportValidator.orEmpty(ranges)) {
            if (r.from() <= number && number <= r.to()) {
                return r;
            }
        }
        return null;
    }

    /** The disciplina of the range covering `number`, or null. */
    static LookupRef rangeSubject(List<ImportJson.Range> ranges, int number) {
        ImportJson.Range r = range(ranges, number);
        return r == null ? null : r.subject();
    }

    /** The admin's override when non-empty, else the range's disciplina, else none. */
    static List<LookupRef> effectiveSubjects(ImportJson.Draft d, List<ImportJson.Range> ranges, int number) {
        if (d.subjects() != null && !d.subjects().isEmpty()) {
            return d.subjects();
        }
        LookupRef fromRange = rangeSubject(ranges, number);
        return fromRange == null ? List.of() : List.of(fromRange);
    }

    static boolean unreadable(ImportJson.Draft d) {
        if (contains(d.referenceText()) || contains(d.statement()) || contains(d.commentary())) {
            return true;
        }
        return d.options() != null && d.options().values().stream().anyMatch(ImportRules::contains);
    }

    private static boolean contains(String s) {
        return s != null && s.contains(UNREADABLE);
    }

    /**
     * What blocks this questão's commit. 3B's validator judges the questão's own fields; the
     * importação-level fields (banca, órgão, ano) get placeholders here — the commit reports
     * those once, not per questão.
     */
    static List<ImportViews.Pendencia> pendencias(QuestionType type, ImportJson.Draft d, String answer, List<LookupRef> subjects) {
        List<ImportViews.Pendencia> out = new ArrayList<>();
        if (answer == null) {
            out.add(new ImportViews.Pendencia("SEM_GABARITO", "answer", "Escolha o gabarito."));
        }
        if (unreadable(d)) {
            out.add(new ImportViews.Pendencia("ILEGIVEL", "statement",
                    "Há caracteres ilegíveis (" + UNREADABLE + "): corrija pelo recorte."));
        }
        if (subjects.isEmpty()) {
            out.add(new ImportViews.Pendencia("SEM_DISCIPLINA", "subjects", "Escolha a disciplina (ou defina uma faixa)."));
        }
        AdminQuestionRequest probe = new AdminQuestionRequest(type.name(), d.referenceText(), d.statement(), d.options(),
                answer, d.commentary(), List.of(new LookupRef(0, null)), new LookupRef(0, null), new LookupRef(0, null),
                AdminQuestionValidator.currentYear(), null, d.difficulty(), List.of(), false);
        try {
            AdminQuestionValidator.validate(probe, null, AdminQuestionValidator.currentYear());
        } catch (AdminApiException e) {
            @SuppressWarnings("unchecked")
            Map<String, String> fieldErrors = (Map<String, String>) e.getBody().get("fieldErrors");
            for (Map.Entry<String, String> fe : fieldErrors.entrySet()) {
                if (fe.getKey().equals("answer") && answer == null) {
                    continue; // already SEM_GABARITO
                }
                out.add(new ImportViews.Pendencia("INVALIDA", fe.getKey(), fe.getValue()));
            }
        }
        return out;
    }
}
