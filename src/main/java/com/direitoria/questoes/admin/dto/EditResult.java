package com.direitoria.questoes.admin.dto;

import java.util.UUID;

/** `rejudged` = tentativas whose verdict actually flipped; 0 when the gabarito did not change. */
public record EditResult(UUID id, int rejudged) {
}
