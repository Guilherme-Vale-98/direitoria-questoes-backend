package com.direitoria.questoes.auth;

import java.util.UUID;

public record RotationResult(UUID userId, String rawRefreshToken) {
}
