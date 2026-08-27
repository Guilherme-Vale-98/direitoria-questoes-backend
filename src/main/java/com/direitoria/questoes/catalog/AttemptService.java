package com.direitoria.questoes.catalog;

import com.direitoria.questoes.domain.QuestionAttempt;
import com.direitoria.questoes.repository.QuestionAttemptRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records tentativas. Deliberately a separate bean with ONE public method: the
 * caller's try/catch must go through Spring's proxy for REQUIRES_NEW to apply,
 * and self-invocation would silently disable it. See docs/adr/0003.
 */
@Service
public class AttemptService {

    private final QuestionAttemptRepository attempts;

    public AttemptService(QuestionAttemptRepository attempts) {
        this.attempts = attempts;
    }

    /**
     * Appends one tentativa in its OWN transaction, so a failure here can neither
     * roll back nor fail the answer the student is waiting for.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(UUID userId, String questionSourceId, String chosenAnswer, boolean correct) {
        attempts.save(new QuestionAttempt(userId, questionSourceId, chosenAnswer, correct));
    }
}
