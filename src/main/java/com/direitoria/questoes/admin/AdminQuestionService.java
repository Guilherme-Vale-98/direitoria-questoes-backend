package com.direitoria.questoes.admin;

import com.direitoria.questoes.admin.AdminQuestionValidator.Draft;
import com.direitoria.questoes.admin.AdminQuestionValidator.Lock;
import com.direitoria.questoes.admin.dto.AdminQuestionRequest;
import com.direitoria.questoes.admin.dto.AdminQuestionResponse;
import com.direitoria.questoes.admin.dto.EditResult;
import com.direitoria.questoes.domain.Agency;
import com.direitoria.questoes.domain.ExamBoard;
import com.direitoria.questoes.domain.Judging;
import com.direitoria.questoes.domain.Question;
import com.direitoria.questoes.domain.QuestionAttempt;
import com.direitoria.questoes.domain.QuestionContent;
import com.direitoria.questoes.domain.Subject;
import com.direitoria.questoes.repository.QuestionAttemptRepository;
import com.direitoria.questoes.repository.QuestionRepository;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * The only path that writes questões from the app (Bloco 3B). One transaction per
 * call: nothing is written until every check has passed, and any failure after the
 * first write rolls everything back — no orphan canonical, no questão without its
 * images.
 */
@Service
public class AdminQuestionService {

    private final EntityManager em;
    private final QuestionRepository questions;
    private final QuestionAttemptRepository attempts;
    private final CanonicalResolver canonicals;
    private final QuestionImages images;

    public AdminQuestionService(
            EntityManager em,
            QuestionRepository questions,
            QuestionAttemptRepository attempts,
            CanonicalResolver canonicals,
            QuestionImages images) {
        this.em = em;
        this.questions = questions;
        this.attempts = attempts;
        this.canonicals = canonicals;
        this.images = images;
    }

    @Transactional(readOnly = true)
    public AdminQuestionResponse get(UUID publicId) {
        Question q = questions.findByPublicId(publicId).orElseThrow(AdminApiException::notFound);
        QuestionAttemptRepository.AttemptCounts counts = attempts.countsFor(q.getSourceId());
        return AdminQuestionMapper.toResponse(q, counts.getAttempts(), counts.getStudents());
    }

    @Transactional
    public UUID create(AdminQuestionRequest request, List<MultipartFile> files) {
        Draft d = AdminQuestionValidator.validate(request, null, AdminQuestionValidator.currentYear());
        List<QuestionImages.Slot> slots = images.plan(request.images(), files, List.of());
        rejectDuplicate(d, "");
        Question q = Question.createManual(resolve(d, request));
        em.persist(q);
        flushOrDuplicate();
        images.apply(q.getSourceId(), slots);
        return refreshed(q).getPublicId();
    }

    /**
     * Bloco 3C's commit: creates one imported questão inside the caller's transaction, by
     * exactly 3B's rules — validation, duplicate check, canonical resolution, the race
     * backstop. A canonical created by name for the first questão is found by the next one's
     * resolution, because it is already in this transaction.
     */
    @Transactional
    public UUID createImported(AdminQuestionRequest request, List<QuestionImages.Upload> uploads) {
        Draft d = AdminQuestionValidator.validate(request, null, AdminQuestionValidator.currentYear());
        List<QuestionImages.Slot> slots = images.planUploads(uploads);
        rejectDuplicate(d, "");
        Question q = Question.createImported(resolve(d, request));
        em.persist(q);
        flushOrDuplicate();
        images.apply(q.getSourceId(), slots);
        return refreshed(q).getPublicId();
    }

    /**
     * Edits any questão in the catalog. The checks run before the first write, in the
     * order the admin needs them: invalid or locked fields (400), a duplicate (409),
     * and — only when the save would otherwise succeed — the re-judge confirmation
     * (409). Re-judging runs inside the same transaction as the edit.
     */
    @Transactional
    public EditResult edit(UUID publicId, AdminQuestionRequest request, List<MultipartFile> files) {
        Question q = questions.findByPublicId(publicId).orElseThrow(AdminApiException::notFound);
        QuestionAttemptRepository.AttemptCounts counts = attempts.countsFor(q.getSourceId());
        boolean answered = counts.getAttempts() > 0;
        Lock lock = answered ? new Lock(q.getTipo(), q.filledLetters()) : null;
        Draft d = AdminQuestionValidator.validate(request, lock, AdminQuestionValidator.currentYear());
        // No order and no files = the client did not touch the images (plan ruling 6).
        boolean touchesImages = request.images() != null || (files != null && !files.isEmpty());
        List<QuestionImages.Slot> slots = touchesImages
                ? images.plan(request.images(), files, images.existingOrdens(q.getSourceId()))
                : null;
        rejectDuplicate(d, q.getSourceId());

        boolean gabaritoChanges = !Judging.normalize(q.getGabarito()).equals(d.answer());
        if (gabaritoChanges && answered && !request.rejudgeConfirmed()) {
            throw AdminApiException.rejudgeRequired(counts.getAttempts(), counts.getStudents());
        }

        q.edit(resolve(d, request), answered);
        flushOrDuplicate();
        if (slots != null) {
            images.apply(q.getSourceId(), slots);
        }
        int rejudged = gabaritoChanges && answered ? rejudge(q) : 0;
        refreshed(q);
        return new EditResult(publicId, rejudged);
    }

    /**
     * Hard-deletes a questão (docs/adr/0005), but only if the admin confirmed the
     * tentativa count it really has. The row lock makes that count exact: a concurrent
     * tentativa insert waits for this transaction and then fails its foreign key (logged
     * by QuestionService.answer; the student still gets their verdict — ADR 0003).
     */
    @Transactional
    public void delete(UUID publicId, long expectedAttempts) {
        Question q = questions.findByPublicId(publicId).orElseThrow(AdminApiException::notFound);
        String sourceId = q.getSourceId();
        em.detach(q); // its row is about to disappear; Hibernate must never flush it
        questions.lockForDelete(sourceId).orElseThrow(AdminApiException::notFound);
        QuestionAttemptRepository.AttemptCounts counts = attempts.countsFor(sourceId);
        if (counts.getAttempts() != expectedAttempts) {
            throw AdminApiException.deleteConfirmationRequired(counts.getAttempts(), counts.getStudents());
        }
        questions.hardDelete(sourceId);
    }

    /** docs/adr/0009: every verdict follows the new gabarito; the chosen letters never change. */
    private int rejudge(Question q) {
        int flipped = 0;
        for (QuestionAttempt a : attempts.findByQuestionSourceId(q.getSourceId())) {
            if (a.rejudge(q.getGabarito())) {
                flipped++;
            }
        }
        // Flush now so the rows agree with the count returned, even when an enclosing
        // transaction (a test's) delays the commit.
        em.flush();
        return flipped;
    }

    /**
     * Re-reads the row, then lets go of the instance. The public id is generated by the
     * database and mapped read-only, so before the refresh the in-memory Question has a
     * null id; and images (Task 6) are written with native SQL, so its asset list is
     * stale. Detaching means anything later in the SAME persistence context — a test's
     * GET right after its POST, inside one test transaction — loads the questão afresh
     * instead of being handed this instance.
     */
    private Question refreshed(Question q) {
        em.refresh(q);
        em.detach(q);
        return q;
    }

    private void rejectDuplicate(Draft d, String selfSourceId) {
        questions.findDuplicate(d.statement(), d.option("A"), d.option("B"), d.option("C"),
                        d.option("D"), d.option("E"), selfSourceId)
                .ifPresent(existing -> {
                    throw AdminApiException.duplicate(existing);
                });
    }

    /**
     * The first write of every save: typed names become canonical rows. Order is banca,
     * órgão, disciplinas — a failure resolving a disciplina rolls back a new órgão.
     */
    private QuestionContent resolve(Draft d, AdminQuestionRequest r) {
        ExamBoard examBoard = canonicals.examBoard(r.examBoard());
        Agency agency = canonicals.agency(r.agency());
        List<Subject> subjects = canonicals.subjects(r.subjects());
        return new QuestionContent(d.type(), d.referenceText(), d.statement(),
                d.option("A"), d.option("B"), d.option("C"), d.option("D"), d.option("E"),
                d.answer(), d.commentary(), subjects, examBoard, agency, d.year(), d.role(), d.difficulty());
    }

    /** uq_questao_content_key is the race-proof backstop behind the pre-check. */
    private void flushOrDuplicate() {
        try {
            em.flush();
        } catch (RuntimeException e) {
            if (ContentKeyViolation.matches(e)) {
                throw AdminApiException.duplicate(null);
            }
            throw e;
        }
    }
}
