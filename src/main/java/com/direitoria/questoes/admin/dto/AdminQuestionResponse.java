package com.direitoria.questoes.admin.dto;

import com.direitoria.questoes.dto.LookupResponse;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Everything the edit form needs — including the gabarito, which the public endpoint
 * never sends before an answer. `options` holds "A".."E" (null where absent) for
 * MULTIPLA_ESCOLHA and is empty for CERTO_ERRADO. `images[].url` is the public asset
 * path; the frontend prefixes /bff. Never carries source_id.
 */
public record AdminQuestionResponse(
        UUID id,
        String type,
        String referenceText,
        String statement,
        Map<String, String> options,
        String answer,
        String commentary,
        List<LookupResponse> subjects,
        LookupResponse examBoard,
        LookupResponse agency,
        Short year,
        String role,
        String difficulty,
        List<Image> images,
        long attemptCount,
        long studentCount,
        Locked locked) {

    public record Image(short ordem, String url) {
    }

    /** What the form must disable because the questão already has tentativas (docs/adr/0009). */
    public record Locked(boolean type, boolean optionCount) {
    }
}
