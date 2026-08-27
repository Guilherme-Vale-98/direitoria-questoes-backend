package com.direitoria.questoes.dto;

import java.time.Instant;

/** One tentativa as the history line sees it. Never carries the gabarito. */
public record AttemptDto(Instant answeredAt, String chosenAnswer, boolean correct) {
}
