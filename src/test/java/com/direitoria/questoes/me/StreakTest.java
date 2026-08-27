package com.direitoria.questoes.me;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StreakTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 8, 16);

    @Test
    void noActivityIsZero() {
        assertThat(StatsService.currentStreak(List.of(), TODAY)).isZero();
    }

    @Test
    void threeConsecutiveDaysEndingTodayIsThree() {
        assertThat(StatsService.currentStreak(
                List.of(TODAY, TODAY.minusDays(1), TODAY.minusDays(2)), TODAY)).isEqualTo(3);
    }

    /** Alive through today until midnight: not having studied YET does not break it. */
    @Test
    void aStreakEndingYesterdayStillCounts() {
        assertThat(StatsService.currentStreak(
                List.of(TODAY.minusDays(1), TODAY.minusDays(2)), TODAY)).isEqualTo(2);
    }

    @Test
    void aGapEndsTheStreak() {
        assertThat(StatsService.currentStreak(
                List.of(TODAY, TODAY.minusDays(1), TODAY.minusDays(5)), TODAY)).isEqualTo(2);
    }

    @Test
    void activityOlderThanYesterdayIsADeadStreak() {
        assertThat(StatsService.currentStreak(
                List.of(TODAY.minusDays(2), TODAY.minusDays(3)), TODAY)).isZero();
    }
}
