package com.direitoria.questoes.admin;

import com.direitoria.questoes.admin.dto.AdminQuestionRequest;
import com.direitoria.questoes.admin.dto.LookupRef;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * "Adicionar N questões" (Bloco 3C spec D6, §4): every included draft reaches the catalog
 * through 3B's create path in ONE transaction, then the importação is deleted — or nothing
 * happens and every problem is listed. The row lock makes a double click or a second tab
 * wait for the first commit, then find the importação gone (Review Focus 3).
 */
@Service
public class ImportCommitService {

    private final ImportStore store;
    private final ImportService imports;
    private final AdminQuestionService admin;

    public ImportCommitService(ImportStore store, ImportService imports, AdminQuestionService admin) {
        this.store = store;
        this.imports = imports;
        this.admin = admin;
    }

    @Transactional
    public int commit(UUID id) {
        if (!store.lock(id)) {
            throw AdminApiException.importNotFound();
        }
        ImportStore.ImportRow imp = store.find(id).orElseThrow(AdminApiException::importNotFound);
        List<ImportStore.QuestionRow> included = store.questions(id).stream().filter(ImportStore.QuestionRow::included).toList();
        Map<String, String> answers = ImportRules.chosenAnswers(imp.gabarito(), imp.sections());

        List<ImportViews.Problem> problems = new ArrayList<>(importLevelProblems(imp));
        if (included.isEmpty()) {
            problems.add(new ImportViews.Problem(null, "included", "Nenhuma questão incluída."));
        }
        // Duplicates are judged afresh (the catalog may have changed since the review) and
        // reported, never silently skipped: the admin was told N questões would be added.
        Map<Integer, String> keys = store.contentKeys(id);
        Map<String, Integer> firstIncluded = new HashMap<>();
        for (ImportStore.QuestionRow q : included) {
            Integer repeatOf = firstIncluded.putIfAbsent(keys.get(q.number()), q.number());
            if (imports.catalogDuplicate(q.draft()) != null) {
                problems.add(new ImportViews.Problem(q.number(), "statement", "Esta questão já existe no acervo."));
            } else if (repeatOf != null) {
                problems.add(new ImportViews.Problem(q.number(), "statement",
                        "Igual à questão " + repeatOf + " desta importação."));
            }
            String answer = ImportRules.effectiveAnswer(q.draft(), ImportRules.pdfAnswer(answers, q.number()));
            List<LookupRef> subjects = ImportRules.effectiveSubjects(q.draft(), imp.ranges(), q.number());
            for (ImportViews.Pendencia p : ImportRules.pendencias(imp.type(), q.draft(), answer, subjects)) {
                problems.add(new ImportViews.Problem(q.number(), p.field(), p.message()));
            }
        }
        if (!problems.isEmpty()) {
            throw AdminApiException.commitBlocked(problems);
        }

        for (ImportStore.QuestionRow q : included) {
            ImportJson.Draft d = q.draft();
            String answer = ImportRules.effectiveAnswer(d, ImportRules.pdfAnswer(answers, q.number()));
            List<LookupRef> subjects = ImportRules.effectiveSubjects(d, imp.ranges(), q.number());
            AdminQuestionRequest request = new AdminQuestionRequest(imp.type().name(), d.referenceText(), d.statement(),
                    d.options(), answer, d.commentary(), subjects, imp.examBoard(), imp.agency(), imp.year(),
                    imp.role(), d.difficulty(), List.of(), false);
            List<QuestionImages.Upload> uploads = store.images(id, q.number()).stream()
                    .map(i -> new QuestionImages.Upload(i.contentType(), i.bytes(), i.origem())).toList();
            admin.createImported(request, uploads);
        }
        store.delete(id);
        return included.size();
    }

    private static List<ImportViews.Problem> importLevelProblems(ImportStore.ImportRow imp) {
        List<ImportViews.Problem> out = new ArrayList<>();
        if (imp.examBoard() == null) {
            out.add(new ImportViews.Problem(null, "examBoard", "Informe a banca."));
        }
        if (imp.agency() == null) {
            out.add(new ImportViews.Problem(null, "agency", "Informe o órgão."));
        }
        int current = AdminQuestionValidator.currentYear();
        if (imp.year() == null) {
            out.add(new ImportViews.Problem(null, "year", "Informe o ano."));
        } else if (imp.year() < AdminQuestionValidator.MIN_YEAR || imp.year() > current) {
            out.add(new ImportViews.Problem(null, "year",
                    "O ano deve estar entre " + AdminQuestionValidator.MIN_YEAR + " e " + current + "."));
        }
        if (imp.role() == null || imp.role().isBlank()) {
            out.add(new ImportViews.Problem(null, "role", "Informe o cargo."));
        }
        return out;
    }
}
