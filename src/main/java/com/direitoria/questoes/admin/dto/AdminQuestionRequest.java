package com.direitoria.questoes.admin.dto;

import java.util.List;
import java.util.Map;

/**
 * The `questao` JSON part of a create or edit (Bloco 3B spec §4). Types are loose on
 * purpose — `type`, `difficulty` and `year` are checked by AdminQuestionValidator, so a
 * bad value becomes a Portuguese field error instead of a Jackson parse failure.
 * `options` is keyed "A".."E"; it is ignored for CERTO_ERRADO.
 */
public record AdminQuestionRequest(
        String type,
        String referenceText,
        String statement,
        Map<String, String> options,
        String answer,
        String commentary,
        List<LookupRef> subjects,
        LookupRef examBoard,
        LookupRef agency,
        Integer year,
        String role,
        String difficulty,
        List<ImageRef> images,
        Boolean confirmRejudge) {

    public boolean rejudgeConfirmed() {
        return Boolean.TRUE.equals(confirmRejudge);
    }
}
