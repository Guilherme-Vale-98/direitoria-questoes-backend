package com.direitoria.questoes;

import com.direitoria.questoes.domain.Judging;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JudgingTest {

    @Test
    void comparesTrimmedUpperCasedTokens() {
        assertTrue(Judging.isCorrect(" b ", "B"));
        assertTrue(Judging.isCorrect("certo", "CERTO"));
        assertFalse(Judging.isCorrect("A", "B"));
        assertFalse(Judging.isCorrect("ERRADO", "CERTO"));
    }

    @Test
    void aMissingChoiceNeverMatchesARealGabarito() {
        assertFalse(Judging.isCorrect(null, "A"));
    }

    @Test
    void normalizeIsTheStoredForm() {
        assertEquals("ERRADO", Judging.normalize("  errado "));
        assertEquals("", Judging.normalize(null));
    }
}
