package com.direitoria.questoes.admin;

import com.direitoria.questoes.admin.dto.LookupRef;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Response bodies of /api/admin/imports (Bloco 3C spec §4). Never carry a source_id. */
public final class ImportViews {

    private ImportViews() {
    }

    public record Summary(UUID id, String fileName, String type, OffsetDateTime createdAt, int found, int included) {
    }

    public record SectionView(int key, String title, String code, int answers) {
    }

    public record CandidateView(List<Integer> keys, int score, boolean suggested) {
    }

    /**
     * Counts behind the overview's notices. `gabaritoLast` is set when the chosen sections go
     * past the last questão found — a missed questão start folds every later questão into the
     * one before it; `gabaritoFirst` when they start before the first (a partial booklet).
     */
    public record Notices(int anuladas, int inCatalog, int repeated, int unreadable, boolean gabaritoUnreadable,
                          Integer gabaritoLast, Integer gabaritoFirst) {
    }

    public record Pendencia(String code, String field, String message) {
    }

    /** Exactly one is set: a catalog questão's public id, or a lower number of the same importação. */
    public record DuplicateRef(UUID catalogId, Integer number) {
    }

    public record Row(int number, boolean included, String statementPreview, String answer, String answerSource,
                      boolean anulada, DuplicateRef duplicateOf, List<LookupRef> subjects, List<String> flags,
                      List<Pendencia> pendencias) {
    }

    public record Overview(UUID id, String fileName, String type, int pageCount, String gabaritoFileName,
                           List<SectionView> sections, List<CandidateView> candidates, List<Integer> gabaritoSections,
                           LookupRef examBoard, LookupRef agency, Integer year, String role,
                           List<ImportJson.Range> subjectRanges, List<String> missingFields, Notices notices,
                           List<Row> questions) {
    }

    /** `url` is the API's own path; the frontend prefixes /bff. */
    public record Image(int ordem, String url) {
    }

    /**
     * One draft for the review page. `answer`/`subjects` are the admin's overrides (null /
     * empty = following the section / the range); `effectiveAnswer` is what a commit would save;
     * `range` is the disciplina range covering this questão (or null). The importação-level
     * fields ride along so the page can show them read-only.
     */
    public record QuestionView(int number, String type, String referenceText, String statement,
                               Map<String, String> options, String answer, String effectiveAnswer,
                               String gabaritoFromPdf, String commentary, List<LookupRef> subjects,
                               ImportJson.Range range, String difficulty, List<ImportJson.Crop> crop,
                               List<Image> images, boolean included, List<String> flags,
                               List<Pendencia> pendencias, DuplicateRef duplicateOf, Integer previous, Integer next,
                               LookupRef examBoard, LookupRef agency, Integer year, String role) {
    }

    public record CommitResult(int created) {
    }

    /** One reason a commit was refused. `number` is null for an importação-level problem (banca, ano…). */
    public record Problem(Integer number, String field, String message) {
    }
}
