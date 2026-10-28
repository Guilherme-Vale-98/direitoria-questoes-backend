package com.direitoria.questoes.admin;

import com.direitoria.questoes.admin.dto.LookupRef;
import com.direitoria.questoes.domain.Judging;
import com.direitoria.questoes.domain.QuestionType;
import com.direitoria.questoes.repository.QuestionRepository;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

/**
 * PDF importações (Bloco 3C): a server-side draft per prova until the admin commits or
 * discards it. Spring never parses the PDF — the browser did (docs/adr/0011). This class
 * owns the flags a draft carries: duplicates of the catalog or within the importação, and
 * the default exclusion of anuladas.
 */
@Service
public class ImportService {

    private final ImportStore store;
    private final QuestionRepository questions;
    private final QuestionImages images;

    public ImportService(ImportStore store, QuestionRepository questions, QuestionImages images) {
        this.store = store;
        this.questions = questions;
        this.images = images;
    }

    @Transactional
    public UUID create(MultipartFile pdf, ImportJson.ParseResult r, List<MultipartFile> figuras) {
        byte[] pdfBytes = bytesOf(pdf);
        List<ImportValidator.Figure> figures = ImportValidator.orEmpty(figuras).stream()
                .map(f -> new ImportValidator.Figure(bytesOf(f), nameOf(f))).toList();
        QuestionType type = ImportValidator.validate(r, pdfBytes, figures);

        ImportJson.Gabarito g = r.gabarito();
        List<Integer> sections = g == null || g.suggested() == null ? List.of() : g.candidates().get(g.suggested()).keys();
        String fileName = r.fileName().strip();
        UUID id = store.insertImport(fileName, type, r.pageCount(), pdfBytes, g, sections,
                ImportValidator.orEmpty(r.subjectRanges()));
        for (ImportJson.ParsedQuestion q : r.questions()) {
            store.insertQuestion(id, q.number(), initialDraft(q, type), ImportValidator.orEmpty(q.crop()),
                    ImportValidator.orEmpty(q.flags()));
            int ordem = 0;
            for (ImportJson.FigureRef f : ImportValidator.orEmpty(q.figures())) {
                byte[] bytes = figures.get(f.part()).bytes();
                store.insertImage(id, q.number(), ordem++, ImageSniffer.detect(bytes).orElseThrow(), bytes,
                        "pdf:" + fileName + "#p" + f.page());
            }
        }

        Set<Integer> all = r.questions().stream().map(ImportJson.ParsedQuestion::number).collect(Collectors.toSet());
        refreshDuplicates(id, all);
        Map<String, String> answers = ImportRules.chosenAnswers(g, sections);
        store.setIncluded(id, all.stream().filter(n -> ImportRules.anulada(answers, n)).toList(), false);
        return id;
    }

    @Transactional(readOnly = true)
    public List<ImportViews.Summary> list() {
        return store.summaries();
    }

    @Transactional(readOnly = true)
    public ImportStore.PdfFile pdf(UUID id) {
        return store.pdf(id).orElseThrow(AdminApiException::importNotFound);
    }

    @Transactional
    public void discard(UUID id) {
        if (store.delete(id) == 0) {
            throw AdminApiException.importNotFound();
        }
    }

    @Transactional(readOnly = true)
    public ImportViews.Overview overview(UUID id) {
        ImportStore.ImportRow imp = store.find(id).orElseThrow(AdminApiException::importNotFound);
        List<ImportStore.QuestionRow> rows = store.questions(id);
        Map<String, String> answers = ImportRules.chosenAnswers(imp.gabarito(), imp.sections());

        List<ImportViews.Row> out = new ArrayList<>();
        int anuladas = 0;
        int inCatalog = 0;
        int repeated = 0;
        int unreadable = 0;
        for (ImportStore.QuestionRow q : rows) {
            String pdfAnswer = ImportRules.pdfAnswer(answers, q.number());
            String answer = ImportRules.effectiveAnswer(q.draft(), pdfAnswer);
            List<LookupRef> subjects = ImportRules.effectiveSubjects(q.draft(), imp.ranges(), q.number());
            boolean anulada = ImportRules.ANULADA.equals(pdfAnswer);
            if (anulada && !q.included()) {
                anuladas++;
            }
            if (q.duplicateOfId() != null) {
                inCatalog++;
            }
            if (q.duplicateOfNumero() != null) {
                repeated++;
            }
            if (ImportRules.unreadable(q.draft())) {
                unreadable++;
            }
            out.add(new ImportViews.Row(q.number(), q.included(), preview(q.draft().statement()), answer,
                    ImportRules.answerSource(q.draft(), pdfAnswer), anulada, duplicateRef(q), subjects, q.flags(),
                    ImportRules.pendencias(imp.type(), q.draft(), answer, subjects)));
        }

        Integer gabaritoLast = null;
        Integer gabaritoFirst = null;
        if (!answers.isEmpty() && !rows.isEmpty()) {
            int max = answers.keySet().stream().mapToInt(Integer::parseInt).max().orElseThrow();
            int min = answers.keySet().stream().mapToInt(Integer::parseInt).min().orElseThrow();
            if (max > rows.get(rows.size() - 1).number()) {
                gabaritoLast = max;
            }
            if (min < rows.get(0).number()) {
                gabaritoFirst = min;
            }
        }
        ImportJson.Gabarito g = imp.gabarito();
        boolean gabaritoUnreadable = g != null && ImportValidator.orEmpty(g.sections()).isEmpty();
        List<ImportViews.SectionView> sections = g == null ? List.of() : ImportValidator.orEmpty(g.sections()).stream()
                .map(s -> new ImportViews.SectionView(s.key(), s.title(), s.code(), s.answers().size())).toList();
        List<ImportViews.CandidateView> candidates = new ArrayList<>();
        if (g != null) {
            List<ImportJson.Candidate> cs = ImportValidator.orEmpty(g.candidates());
            for (int i = 0; i < cs.size(); i++) {
                candidates.add(new ImportViews.CandidateView(cs.get(i).keys(), cs.get(i).score(),
                        g.suggested() != null && g.suggested() == i));
            }
        }
        List<String> missing = new ArrayList<>();
        if (imp.examBoard() == null) {
            missing.add("examBoard");
        }
        if (imp.agency() == null) {
            missing.add("agency");
        }
        if (imp.year() == null) {
            missing.add("year");
        }
        if (imp.role() == null || imp.role().isBlank()) {
            missing.add("role");
        }
        return new ImportViews.Overview(imp.id(), imp.fileName(), imp.type().name(), imp.pageCount(),
                g == null ? null : g.fileName(), sections, candidates, imp.sections(), imp.examBoard(), imp.agency(),
                imp.year(), imp.role(), imp.ranges(), missing,
                new ImportViews.Notices(anuladas, inCatalog, repeated, unreadable, gabaritoUnreadable, gabaritoLast,
                        gabaritoFirst),
                out);
    }

    /**
     * Saves the importação-level fields. A section change excludes the questões that BECOME
     * anuladas under it, and re-includes the excluded ones that STOP being anuladas (unless
     * they are duplicates) — those exclusions were the system's, so switching back restores
     * them. Every other include toggle stands (Review Focus 2).
     */
    @Transactional
    public ImportViews.Overview updateFields(UUID id, ImportJson.FieldsRequest f) {
        ImportStore.ImportRow imp = lockAndFind(id);
        List<Integer> chosen = ImportValidator.orEmpty(f.gabaritoSections());
        ImportValidator.validateSections(imp.gabarito(), chosen);
        ImportValidator.validateRanges(f.subjectRanges());
        Map<String, String> before = ImportRules.chosenAnswers(imp.gabarito(), imp.sections());
        Map<String, String> after = ImportRules.chosenAnswers(imp.gabarito(), chosen);
        store.updateFields(id, f);
        List<ImportStore.QuestionRow> rows = store.questions(id);
        store.setIncluded(id, rows.stream().map(ImportStore.QuestionRow::number)
                .filter(n -> ImportRules.anulada(after, n) && !ImportRules.anulada(before, n)).toList(), false);
        store.setIncluded(id, rows.stream()
                .filter(q -> !q.included() && !q.duplicate())
                .map(ImportStore.QuestionRow::number)
                .filter(n -> ImportRules.anulada(before, n) && !ImportRules.anulada(after, n)).toList(), true);
        return overview(id);
    }

    @Transactional(readOnly = true)
    public ImportViews.QuestionView question(UUID id, int number) {
        ImportStore.ImportRow imp = store.find(id).orElseThrow(AdminApiException::importNotFound);
        List<ImportStore.QuestionRow> rows = store.questions(id);
        int i = 0;
        while (i < rows.size() && rows.get(i).number() != number) {
            i++;
        }
        if (i == rows.size()) {
            throw AdminApiException.notFound();
        }
        ImportStore.QuestionRow q = rows.get(i);
        ImportJson.Draft d = q.draft();
        Map<String, String> answers = ImportRules.chosenAnswers(imp.gabarito(), imp.sections());
        String pdfAnswer = ImportRules.pdfAnswer(answers, number);
        String answer = ImportRules.effectiveAnswer(d, pdfAnswer);
        List<LookupRef> subjects = ImportRules.effectiveSubjects(d, imp.ranges(), number);
        String override = Judging.normalize(d.answer());
        List<ImportViews.Image> imgs = store.imageOrdens(id, number).stream()
                .map(o -> new ImportViews.Image(o, "/api/admin/imports/" + id + "/questions/" + number + "/images/" + o))
                .toList();
        return new ImportViews.QuestionView(number, imp.type().name(), d.referenceText(), d.statement(), d.options(),
                override.isEmpty() ? null : override, answer, pdfAnswer, d.commentary(),
                d.subjects() == null ? List.of() : d.subjects(), ImportRules.range(imp.ranges(), number),
                d.difficulty(), q.crop(), imgs, q.included(), q.flags(),
                ImportRules.pendencias(imp.type(), d, answer, subjects), duplicateRef(q),
                i > 0 ? rows.get(i - 1).number() : null, i < rows.size() - 1 ? rows.get(i + 1).number() : null,
                imp.examBoard(), imp.agency(), imp.year(), imp.role());
    }

    /**
     * Saves one draft. Only the image rules are checked (3B's, with the draft's own ordens);
     * everything else becomes a pendência — a draft may be wrong until the commit (Ruling 3).
     */
    @Transactional
    public ImportViews.QuestionView saveQuestion(UUID id, int number, ImportJson.DraftRequest r, List<MultipartFile> files) {
        ImportStore.ImportRow imp = lockAndFind(id);
        store.question(id, number).orElseThrow(AdminApiException::notFound);
        if (r == null) {
            throw AdminApiException.validation(Map.of("questao", "Envie a questão."));
        }
        boolean touchesImages = r.images() != null || (files != null && !files.isEmpty());
        List<QuestionImages.Slot> slots = touchesImages ? images.plan(r.images(), files, store.imageOrdens(id, number)) : null;
        store.updateDraft(id, number, draftOf(r, imp.type()));
        if (slots != null) {
            store.applyImages(id, number, slots);
        }
        refreshDuplicates(id, Set.of(number));
        return question(id, number);
    }

    /** A duplicate (of the catalog or within the importação) can never be included (ADR 0007; Ruling 6). */
    @Transactional
    public void setIncluded(UUID id, int number, Boolean included) {
        lockAndFind(id);
        ImportStore.QuestionRow q = store.question(id, number).orElseThrow(AdminApiException::notFound);
        if (included == null) {
            throw AdminApiException.validation(Map.of("included", "Informe se a questão entra na importação."));
        }
        if (included && q.duplicate()) {
            throw AdminApiException.validation(Map.of("included",
                    "Esta questão já está no acervo ou se repete nesta importação."));
        }
        store.setIncluded(id, number, included);
        store.touch(id);
    }

    @Transactional(readOnly = true)
    public ImportStore.StoredImage image(UUID id, int number, int ordem) {
        return store.image(id, number, ordem)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Imagem não encontrada"));
    }

    /**
     * Recomputes "Já no acervo" (against the catalog, for the questões in `checkCatalog` —
     * the others keep what they had) and "Repetida" (a lower-numbered questão of the same
     * importação has the same content key). A questão that BECOMES a duplicate is excluded;
     * one that stops being a duplicate keeps whatever the admin had chosen.
     */
    void refreshDuplicates(UUID id, Set<Integer> checkCatalog) {
        Map<Integer, String> keys = store.contentKeys(id);
        Map<String, Integer> firstByKey = new HashMap<>();
        for (ImportStore.QuestionRow q : store.questions(id)) {
            UUID catalog = checkCatalog.contains(q.number()) ? catalogDuplicate(q.draft()) : q.duplicateOfId();
            String key = keys.get(q.number());
            Integer repeatOf = null;
            if (key != null) {
                Integer first = firstByKey.putIfAbsent(key, q.number());
                if (catalog == null && first != null) {
                    repeatOf = first;
                }
            }
            if (!Objects.equals(catalog, q.duplicateOfId()) || !Objects.equals(repeatOf, q.duplicateOfNumero())) {
                store.setDuplicate(id, q.number(), catalog, repeatOf);
            }
            boolean isDuplicate = catalog != null || repeatOf != null;
            if (isDuplicate && !q.duplicate() && q.included()) {
                store.setIncluded(id, q.number(), false);
            }
        }
    }

    /**
     * Every path that writes an importação's rows starts here: the importação's row lock,
     * the one the commit takes, so a save and a commit run one after the other instead of
     * deadlocking — a save that waited for a commit then finds the importação gone (404).
     */
    private ImportStore.ImportRow lockAndFind(UUID id) {
        if (!store.lock(id)) {
            throw AdminApiException.importNotFound();
        }
        return store.find(id).orElseThrow(AdminApiException::importNotFound);
    }

    /** The public id of a catalog questão with this draft's content key, or null (3B's duplicate rule). */
    UUID catalogDuplicate(ImportJson.Draft d) {
        Map<String, String> o = d.options() == null ? Map.of() : d.options();
        return questions.findDuplicate(strip(d.statement()), strip(o.get("A")), strip(o.get("B")), strip(o.get("C")),
                strip(o.get("D")), strip(o.get("E")), "").orElse(null);
    }

    private static ImportJson.Draft initialDraft(ImportJson.ParsedQuestion q, QuestionType type) {
        Map<String, String> options = null;
        if (type == QuestionType.MULTIPLA_ESCOLHA) {
            options = new LinkedHashMap<>();
            Map<String, String> in = q.options() == null ? Map.of() : q.options();
            for (String l : AdminQuestionValidator.LETTERS) {
                String v = in.get(l);
                options.put(l, v == null || v.isBlank() ? null : v);
            }
        }
        String ref = q.referenceText() == null || q.referenceText().isBlank() ? null : q.referenceText();
        return new ImportJson.Draft(ref, q.statement(), options, null, null, null, null);
    }

    private static String strip(String s) {
        return s == null ? null : s.strip();
    }

    static byte[] bytesOf(MultipartFile f) {
        try {
            return f.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static String nameOf(MultipartFile f) {
        String n = f.getOriginalFilename();
        return n == null || n.isBlank() ? "arquivo" : n;
    }

    private static ImportJson.Draft draftOf(ImportJson.DraftRequest r, QuestionType type) {
        Map<String, String> options = null;
        if (type == QuestionType.MULTIPLA_ESCOLHA) {
            options = new LinkedHashMap<>();
            Map<String, String> in = r.options() == null ? Map.of() : r.options();
            for (String l : AdminQuestionValidator.LETTERS) {
                options.put(l, blankToNull(in.get(l)));
            }
        }
        String answer = Judging.normalize(r.answer());
        List<LookupRef> subjects = r.subjects() == null || r.subjects().isEmpty() ? null : r.subjects();
        return new ImportJson.Draft(blankToNull(r.referenceText()), r.statement() == null ? "" : r.statement(), options,
                answer.isEmpty() ? null : answer, blankToNull(r.commentary()), subjects, blankToNull(r.difficulty()));
    }

    private static ImportViews.DuplicateRef duplicateRef(ImportStore.QuestionRow q) {
        return q.duplicate() ? new ImportViews.DuplicateRef(q.duplicateOfId(), q.duplicateOfNumero()) : null;
    }

    private static String preview(String statement) {
        String flat = statement == null ? "" : statement.replaceAll("\\s+", " ").strip();
        return flat.length() <= 120 ? flat : flat.substring(0, 119) + "…";
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}
