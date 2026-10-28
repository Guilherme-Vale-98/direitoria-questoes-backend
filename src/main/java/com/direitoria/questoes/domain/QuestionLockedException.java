package com.direitoria.questoes.domain;

import java.util.Map;

/**
 * An edit a questão with tentativas may not take: changing its type, or adding or
 * removing an alternativa (docs/adr/0009). Carries field errors; the web layer turns
 * it into a 400.
 */
public class QuestionLockedException extends RuntimeException {

    private final Map<String, String> fieldErrors;

    public QuestionLockedException(Map<String, String> fieldErrors) {
        super("Alteração bloqueada: a questão já tem tentativas " + fieldErrors.keySet());
        this.fieldErrors = Map.copyOf(fieldErrors);
    }

    public Map<String, String> getFieldErrors() {
        return fieldErrors;
    }
}
