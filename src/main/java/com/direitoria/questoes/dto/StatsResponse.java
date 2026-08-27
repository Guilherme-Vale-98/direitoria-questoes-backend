package com.direitoria.questoes.dto;

import java.time.LocalDate;
import java.util.List;

/** The "Meu desempenho" payload. All figures are over the questões this student answered. */
public record StatsResponse(Overall overall, Activity activity, List<SubjectStat> bySubject) {

    public record Overall(long answered, long correct, long incorrect, double accuracy) {
        public static Overall of(long answered, long correct) {
            long incorrect = answered - correct;
            double accuracy = answered == 0 ? 0.0 : round1(correct * 100.0 / answered);
            return new Overall(answered, correct, incorrect, accuracy);
        }
    }

    public record Activity(int currentStreakDays, long answeredLast7Days, LocalDate lastActiveDate) {
    }

    public record SubjectStat(Integer subjectId, String subjectName, long answered, long correct,
                              double accuracy) {
        public static SubjectStat of(Integer id, String name, long answered, long correct) {
            return new SubjectStat(id, name, answered, correct,
                    answered == 0 ? 0.0 : round1(correct * 100.0 / answered));
        }
    }

    private static double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }
}
