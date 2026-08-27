package com.direitoria.questoes.catalog;

/**
 * Filters the catalog by the student's own relationship to a questão, judged on
 * the ÚLTIMA TENTATIVA (docs/adr/0001) — not on any earlier one.
 */
public enum HistoryStatus {
    /** No tentativa at all. */
    UNANSWERED,
    /** Última tentativa was correct. */
    CORRECT,
    /** Última tentativa was not correct. */
    WRONG
}
