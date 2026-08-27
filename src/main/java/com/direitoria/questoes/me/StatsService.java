package com.direitoria.questoes.me;

import com.direitoria.questoes.dto.StatsResponse;
import com.direitoria.questoes.repository.QuestionAttemptRepository;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StatsService {

    /** Day boundaries are Brazilian, wherever the student is. No DST since 2019. */
    static final ZoneId BRT = ZoneId.of("America/Sao_Paulo");

    /** Today and the six days before it. */
    private static final int WEEK_WINDOW_DAYS = 6;

    private final QuestionAttemptRepository attempts;

    public StatsService(QuestionAttemptRepository attempts) {
        this.attempts = attempts;
    }

    @Transactional(readOnly = true)
    public StatsResponse forUser(UUID userId) {
        var overall = attempts.overall(userId);
        List<LocalDate> activeDates = attempts.activeDatesDesc(userId);
        LocalDate today = LocalDate.now(BRT);

        return new StatsResponse(
                StatsResponse.Overall.of(overall.getAnswered(), overall.getCorrect()),
                new StatsResponse.Activity(
                        currentStreak(activeDates, today),
                        attempts.distinctQuestionsSince(userId, today.minusDays(WEEK_WINDOW_DAYS)),
                        activeDates.isEmpty() ? null : activeDates.get(0)),
                attempts.bySubject(userId).stream()
                        .map(r -> StatsResponse.SubjectStat.of(
                                r.getSubjectId(), r.getSubjectName(), r.getAnswered(), r.getCorrect()))
                        .toList());
    }

    /**
     * Consecutive BRT days with activity, ending today OR yesterday. Ending
     * yesterday still counts: a student who has not studied yet today has not yet
     * lost their ofensiva. `activeDates` must be distinct and newest-first.
     */
    static int currentStreak(List<LocalDate> activeDates, LocalDate today) {
        if (activeDates.isEmpty()) {
            return 0;
        }
        LocalDate newest = activeDates.get(0);
        if (newest.isBefore(today.minusDays(1))) {
            return 0;
        }
        int streak = 1;
        for (int i = 1; i < activeDates.size(); i++) {
            if (!activeDates.get(i).equals(activeDates.get(i - 1).minusDays(1))) {
                break;
            }
            streak++;
        }
        return streak;
    }
}
