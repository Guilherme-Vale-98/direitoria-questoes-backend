package com.direitoria.questoes.dto;

import java.util.List;
import java.util.UUID;

public record QuestionResponse(
        UUID id,
        String statement,
        String type,
        String difficulty,
        List<OptionDto> options,
        List<LookupResponse> subjects,
        LookupResponse examBoard,
        LookupResponse agency,
        String role,
        Short year) {
}
