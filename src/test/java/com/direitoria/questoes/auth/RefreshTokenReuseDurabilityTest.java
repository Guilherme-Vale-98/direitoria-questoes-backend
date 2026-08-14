package com.direitoria.questoes.auth;

import com.direitoria.questoes.TestcontainersConfiguration;
import com.direitoria.questoes.domain.User;
import com.direitoria.questoes.repository.RefreshTokenRepository;
import com.direitoria.questoes.repository.UserRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Guards reuse-detection durability at the service layer with REAL per-call
 * transactions (deliberately NOT @Transactional). The existing
 * {@link RefreshTokenServiceTest} runs inside a single outer test transaction,
 * so it cannot catch a regression where the revoke-then-throw in
 * {@code rotate()} gets rolled back instead of committed. This test observes
 * committed state across separate service calls, the same way the real
 * application (and the MockMvc test) does.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class RefreshTokenReuseDurabilityTest {

    @Autowired RefreshTokenService service;
    @Autowired UserRepository userRepository;
    @Autowired RefreshTokenRepository refreshTokenRepository;
    @Autowired RefreshTokenHasher hasher;

    @Test
    void reuseDetectionSurvivesCommit() {
        UUID userId = userRepository.save(
                new User("T", "U", "u_" + UUID.randomUUID() + "@x.com", "hash")).getId();

        String first = service.issueNewFamily(userId);
        RotationResult second = service.rotate(first); // first now revoked; second is the live sibling

        // Replay the rotated token: reuse detected, revokes the family, throws.
        assertThatThrownBy(() -> service.rotate(first)).isInstanceOf(ResponseStatusException.class);

        // Durability assertion: in a SEPARATE call (no surrounding @Transactional),
        // the revocation from the previous call must already be committed.
        assertThatThrownBy(() -> service.rotate(second.rawRefreshToken()))
                .isInstanceOf(ResponseStatusException.class);

        assertThat(refreshTokenRepository.findByTokenHash(hasher.hash(second.rawRefreshToken())))
                .isPresent()
                .get()
                .extracting(t -> t.getRevokedAt())
                .isNotNull();
    }
}
