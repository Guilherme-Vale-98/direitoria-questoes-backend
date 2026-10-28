package com.direitoria.questoes.admin;

import com.direitoria.questoes.admin.dto.ImageRef;
import com.direitoria.questoes.admin.dto.LookupRef;
import java.util.List;
import java.util.Map;

/**
 * The JSON of Bloco 3C (spec §2, §4): the browser parser's result (the `importacao` part of
 * a create), the draft stored per questão, and the review screens' request bodies. Types are
 * loose on purpose: ImportValidator turns a bad value into a Portuguese error instead of a
 * Jackson parse failure. Pages are 1-based everywhere on the wire.
 */
public final class ImportJson {

    private ImportJson() {
    }

    public record ParseResult(String fileName, String type, Integer pageCount, Gabarito gabarito,
                              List<Range> subjectRanges, List<ParsedQuestion> questions) {
    }

    /** `sections` empty = a gabarito PDF was given but yielded nothing. */
    public record Gabarito(String fileName, List<Section> sections, List<Candidate> candidates, Integer suggested) {
    }

    /** `answers`: questão number → `A`–`E`, `CERTO`/`ERRADO` or `ANULADA`. */
    public record Section(Integer key, String title, String code, Map<String, String> answers) {
    }

    public record Candidate(List<Integer> keys, Integer score) {
    }

    /** A disciplina range. `subject` null = the heading matched no disciplina (hint only). */
    public record Range(Integer from, Integer to, LookupRef subject, String hint) {
    }

    public record ParsedQuestion(Integer number, String referenceText, String statement, Map<String, String> options,
                                 List<Crop> crop, List<FigureRef> figures, List<String> flags) {
    }

    /** A crop rectangle in PDF points. */
    public record Crop(Integer page, Double x0, Double top, Double x1, Double bottom) {
    }

    /** `part` indexes the create's `figuras` files. */
    public record FigureRef(Integer part, Integer page) {
    }

    /**
     * A questão's editable content, stored as importacao_questao.draft. `answer` and
     * `subjects` are the admin's OVERRIDES: null follows the gabarito section / the range.
     */
    public record Draft(String referenceText, String statement, Map<String, String> options, String answer,
                        String commentary, List<LookupRef> subjects, String difficulty) {
    }

    /** PUT /{id}. */
    public record FieldsRequest(LookupRef examBoard, LookupRef agency, Integer year, String role,
                                List<Integer> gabaritoSections, List<Range> subjectRanges) {
    }

    /** The `questao` part of PUT /{id}/questions/{n}. `images` follows 3B: null = untouched. */
    public record DraftRequest(String referenceText, String statement, Map<String, String> options, String answer,
                               String commentary, List<LookupRef> subjects, String difficulty, List<ImageRef> images) {
    }

    public record IncludedRequest(Boolean included) {
    }
}
