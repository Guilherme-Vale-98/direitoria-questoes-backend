package com.direitoria.questoes.domain;

/**
 * The one judging rule. The answer endpoint and re-judging after a gabarito change
 * both call this — two copies would be two places for the rule to drift apart
 * (docs/adr/0003, docs/adr/0009).
 */
public final class Judging {

    private Judging() {
    }

    /** The stored and compared form of an alternativa: trimmed, upper-case ("b " → "B"). */
    public static String normalize(String s) {
        return s == null ? "" : s.trim().toUpperCase();
    }

    public static boolean isCorrect(String chosen, String gabarito) {
        return normalize(chosen).equals(normalize(gabarito));
    }
}
