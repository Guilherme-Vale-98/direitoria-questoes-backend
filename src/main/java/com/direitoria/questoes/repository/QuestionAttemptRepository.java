package com.direitoria.questoes.repository;

import com.direitoria.questoes.domain.QuestionAttempt;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Every statistic here is computed over the ÚLTIMA TENTATIVA per (user, questão)
 * — see docs/adr/0001. The `latest` CTE is that rule; do not bypass it.
 *
 * Disciplinas are joined LIVE to the questão's current classification, never
 * snapshotted onto the tentativa — see docs/adr/0004.
 */
public interface QuestionAttemptRepository extends JpaRepository<QuestionAttempt, UUID> {

    interface OverallProjection {
        long getAnswered();
        long getCorrect();
    }

    interface SubjectStatProjection {
        Integer getSubjectId();
        String getSubjectName();
        long getAnswered();
        long getCorrect();
    }

    @Query(value = """
            WITH latest AS (
              SELECT DISTINCT ON (question_source_id) question_source_id, is_correct
              FROM question_attempt
              WHERE user_id = :userId
              ORDER BY question_source_id, answered_at DESC, id DESC
            )
            SELECT count(*) AS answered,
                   count(*) FILTER (WHERE is_correct) AS correct
            FROM latest
            """, nativeQuery = true)
    OverallProjection overall(@Param("userId") UUID userId);

    @Query(value = """
            WITH latest AS (
              SELECT DISTINCT ON (question_source_id) question_source_id, is_correct
              FROM question_attempt
              WHERE user_id = :userId
              ORDER BY question_source_id, answered_at DESC, id DESC
            )
            SELECT d.id AS subject_id,
                   d.nome AS subject_name,
                   count(*) AS answered,
                   count(*) FILTER (WHERE l.is_correct) AS correct
            FROM latest l
            JOIN questao_disciplina qd ON qd.source_id = l.question_source_id
            JOIN disciplina d ON d.id = qd.disciplina_id
            GROUP BY d.id, d.nome
            ORDER BY answered DESC, d.nome ASC
            """, nativeQuery = true)
    List<SubjectStatProjection> bySubject(@Param("userId") UUID userId);

    /** Distinct questões touched on or after `from` (a BRT date). Activity volume. */
    @Query(value = """
            SELECT count(DISTINCT question_source_id)
            FROM question_attempt
            WHERE user_id = :userId
              AND (answered_at AT TIME ZONE 'America/Sao_Paulo')::date >= :from
            """, nativeQuery = true)
    long distinctQuestionsSince(@Param("userId") UUID userId, @Param("from") LocalDate from);

    /** Every BRT day this student was active, newest first. The ofensiva walks this. */
    @Query(value = """
            SELECT DISTINCT (answered_at AT TIME ZONE 'America/Sao_Paulo')::date AS d
            FROM question_attempt
            WHERE user_id = :userId
            ORDER BY d DESC
            """, nativeQuery = true)
    List<LocalDate> activeDatesDesc(@Param("userId") UUID userId);

    interface HistoryProjection {
        UUID getQuestionId();
        Instant getAnsweredAt();
        String getChosenAnswer();
        boolean getCorrect();
    }

    /**
     * Every tentativa this student made on the given questões, newest first.
     * Unlike the statistics queries this is NOT limited to the última tentativa —
     * the history line shows all of them (ADR-0001: graphs count the latest, the
     * history shows every one).
     */
    @Query(value = """
            SELECT q.id           AS question_id,
                   a.answered_at  AS answered_at,
                   a.chosen_answer AS chosen_answer,
                   a.is_correct   AS correct
            FROM question_attempt a
            JOIN questao q ON q.source_id = a.question_source_id
            WHERE a.user_id = :userId
              AND q.id IN (:questionIds)
            ORDER BY q.id, a.answered_at DESC, a.id DESC
            """, nativeQuery = true)
    List<HistoryProjection> historyFor(
            @Param("userId") UUID userId, @Param("questionIds") Collection<UUID> questionIds);

    /**
     * source_ids whose ÚLTIMA TENTATIVA matched `correct`. Same CTE as the
     * statistics queries — ADR-0001 lives in exactly one place.
     */
    @Query(value = """
            WITH latest AS (
              SELECT DISTINCT ON (question_source_id) question_source_id, is_correct
              FROM question_attempt
              WHERE user_id = :userId
              ORDER BY question_source_id, answered_at DESC, id DESC
            )
            SELECT question_source_id FROM latest WHERE is_correct = :correct
            """, nativeQuery = true)
    List<String> sourceIdsByLatestOutcome(
            @Param("userId") UUID userId, @Param("correct") boolean correct);

    /** Every source_id this student has any tentativa on — the complement of "não respondidas". */
    @Query(value = """
            SELECT DISTINCT question_source_id FROM question_attempt WHERE user_id = :userId
            """, nativeQuery = true)
    List<String> answeredSourceIds(@Param("userId") UUID userId);
}
