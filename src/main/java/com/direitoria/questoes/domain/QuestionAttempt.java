package com.direitoria.questoes.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

/**
 * One tentativa: a student's recorded submission against a questão, judged at the
 * moment it was made. Append-only — never updated, never deleted by the app.
 * See CONTEXT.md ("Tentativa") and docs/adr/0001.
 */
@Entity
@Table(name = "question_attempt")
public class QuestionAttempt {

    @Id
    @UuidGenerator
    @Column(name = "id")
    private UUID id;

    @Column(name = "user_id")
    private UUID userId;

    /** The internal questão key. Never exposed by the API. */
    @Column(name = "question_source_id")
    private String questionSourceId;

    /** Normalized token ("B", "CERTO") — the same form judging compares. */
    @Column(name = "chosen_answer")
    private String chosenAnswer;

    @Column(name = "is_correct")
    private boolean correct;

    // DB-managed (defaultNow()); read-only here.
    @Column(name = "answered_at", insertable = false, updatable = false)
    private Instant answeredAt;

    protected QuestionAttempt() {
    }

    public QuestionAttempt(UUID userId, String questionSourceId, String chosenAnswer, boolean correct) {
        this.userId = userId;
        this.questionSourceId = questionSourceId;
        this.chosenAnswer = chosenAnswer;
        this.correct = correct;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getQuestionSourceId() {
        return questionSourceId;
    }

    public String getChosenAnswer() {
        return chosenAnswer;
    }

    public boolean isCorrect() {
        return correct;
    }

    public Instant getAnsweredAt() {
        return answeredAt;
    }
}
