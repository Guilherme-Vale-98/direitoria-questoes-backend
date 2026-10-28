package com.direitoria.questoes.admin;

import java.sql.SQLException;
import org.hibernate.exception.ConstraintViolationException;

/**
 * Recognises a uq_questao_content_key violation anywhere in a cause chain — the
 * backstop behind the duplicate pre-check when two saves race (Bloco 3B spec §3).
 * Checks Hibernate's parsed constraint name AND the raw SQLState + message, because
 * Postgres localises its messages (the local dev database answers in Portuguese) and
 * the constraint name is the only part that survives translation.
 */
final class ContentKeyViolation {

    static final String CONSTRAINT = "uq_questao_content_key";

    private ContentKeyViolation() {
    }

    static boolean matches(Throwable t) {
        for (Throwable c = t; c != null; c = c.getCause()) {
            if (c instanceof ConstraintViolationException cve && CONSTRAINT.equals(cve.getConstraintName())) {
                return true;
            }
            if (c instanceof SQLException sql && "23505".equals(sql.getSQLState())
                    && String.valueOf(sql.getMessage()).contains(CONSTRAINT)) {
                return true;
            }
        }
        return false;
    }
}
