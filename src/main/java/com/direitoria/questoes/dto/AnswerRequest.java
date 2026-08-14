package com.direitoria.questoes.dto;

import jakarta.validation.constraints.NotBlank;

public record AnswerRequest(@NotBlank String chosenAnswer) {
}
