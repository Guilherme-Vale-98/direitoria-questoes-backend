package com.direitoria.questoes.catalog;

import com.direitoria.questoes.domain.Difficulty;
import com.direitoria.questoes.domain.Question;
import com.direitoria.questoes.domain.QuestionType;
import com.direitoria.questoes.dto.AnswerResult;
import com.direitoria.questoes.dto.QuestionResponse;
import com.direitoria.questoes.repository.QuestionAttemptRepository;
import com.direitoria.questoes.repository.QuestionRepository;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class QuestionService {

    private static final Logger log = LoggerFactory.getLogger(QuestionService.class);

    private final QuestionRepository repository;
    private final AttemptService attempts;
    private final QuestionAttemptRepository attemptRepository;

    public QuestionService(
            QuestionRepository repository,
            AttemptService attempts,
            QuestionAttemptRepository attemptRepository) {
        this.repository = repository;
        this.attempts = attempts;
        this.attemptRepository = attemptRepository;
    }

    @Transactional(readOnly = true)
    public Page<QuestionResponse> search(
            Integer subjectId,
            Integer examBoardId,
            Integer agencyId,
            Short year,
            QuestionType type,
            Difficulty difficulty,
            String search,
            HistoryStatus historyStatus,
            UUID userId,
            Pageable pageable) {
        List<String> historyIds = null;
        boolean excludes = false;
        if (historyStatus != null) {
            switch (historyStatus) {
                case WRONG -> historyIds = attemptRepository.sourceIdsByLatestOutcome(userId, false);
                case CORRECT -> historyIds = attemptRepository.sourceIdsByLatestOutcome(userId, true);
                case UNANSWERED -> {
                    historyIds = attemptRepository.answeredSourceIds(userId);
                    excludes = true;
                }
            }
        }
        return repository
                .findAll(
                        QuestionSpecifications.build(
                                subjectId, examBoardId, agencyId, year, type, difficulty, search,
                                historyIds, excludes),
                        pageable)
                .map(QuestionMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public Optional<QuestionResponse> findByPublicId(UUID publicId) {
        return repository.findByPublicId(publicId).map(QuestionMapper::toResponse);
    }

    /**
     * Judge a student's answer server-side and record the tentativa. Empty when the
     * questão doesn't exist (→ 404); throws 400 when the chosen alternativa isn't
     * valid for the questão.
     *
     * Stays readOnly: judging IS a read. Only the recording writes, and it does so
     * in its own REQUIRES_NEW transaction (AttemptService) so a failed insert can
     * never cost the student their verdict. See docs/adr/0003.
     */
    @Transactional(readOnly = true)
    public Optional<AnswerResult> answer(UUID publicId, String chosenAnswer, UUID userId) {
        return repository.findByPublicId(publicId).map(q -> {
            AnswerResult result = judge(q, chosenAnswer);
            try {
                attempts.record(userId, q.getSourceId(), normalize(chosenAnswer), result.correct());
            } catch (RuntimeException e) {
                // Silent to the student, loud here: a dropped tentativa costs a
                // statistic, never a verdict.
                log.error("Failed to record tentativa user={} questao={}", userId, q.getSourceId(), e);
            }
            return result;
        });
    }

    private static AnswerResult judge(Question q, String chosenAnswer) {
        String chosen = normalize(chosenAnswer);
        if (!validTokens(q).contains(chosen)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Alternativa inválida");
        }
        boolean correct = chosen.equals(normalize(q.getGabarito()));
        return new AnswerResult(correct, q.getGabarito(), q.getComentario());
    }

    /** Valid answer tokens: CERTO/ERRADO for true-false, else the labels of the present options. */
    private static Set<String> validTokens(Question q) {
        if (q.getTipo() == QuestionType.CERTO_ERRADO) {
            return Set.of("CERTO", "ERRADO");
        }
        Set<String> tokens = new LinkedHashSet<>();
        if (q.getOpcaoA() != null) tokens.add("A");
        if (q.getOpcaoB() != null) tokens.add("B");
        if (q.getOpcaoC() != null) tokens.add("C");
        if (q.getOpcaoD() != null) tokens.add("D");
        if (q.getOpcaoE() != null) tokens.add("E");
        return tokens;
    }

    private static String normalize(String s) {
        return s == null ? "" : s.trim().toUpperCase();
    }
}
