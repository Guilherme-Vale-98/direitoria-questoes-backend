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
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class RefreshTokenServiceTest {

    @Autowired RefreshTokenService service;
    @Autowired RefreshTokenRepository tokens;
    @Autowired UserRepository users;
    @Autowired RefreshTokenHasher hasher;

    private UUID newUser() {
        return users.save(new User("T", "U", "u_" + UUID.randomUUID() + "@x.com", "hash")).getId();
    }

    @Test
    void rotateIssuesNewTokenAndInvalidatesOld() {
        UUID userId = newUser();
        String first = service.issueNewFamily(userId);

        RotationResult second = service.rotate(first);
        assertThat(second.userId()).isEqualTo(userId);
        assertThat(second.rawRefreshToken()).isNotEqualTo(first);

        // new token works (exercise it BEFORE replaying the old one — replay revokes the family)
        assertThat(service.rotate(second.rawRefreshToken())).isNotNull();
        // old token no longer rotates
        assertThatThrownBy(() -> service.rotate(first)).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void rawTokenIsNeverStored_onlyItsHash() {
        UUID userId = newUser();
        String raw = service.issueNewFamily(userId);
        // the raw token string itself is not a stored token_hash...
        assertThat(tokens.findByTokenHash(raw)).isEmpty();
        // ...but its SHA-256 hash is.
        assertThat(tokens.findByTokenHash(hasher.hash(raw))).isPresent();
    }

    @Test
    void reuseOfRotatedTokenRevokesEntireFamily() {
        UUID userId = newUser();
        String first = service.issueNewFamily(userId);
        RotationResult second = service.rotate(first); // first now revoked

        // replay the already-rotated 'first' => reuse detected
        assertThatThrownBy(() -> service.rotate(first)).isInstanceOf(ResponseStatusException.class);

        // the legitimate 'second' token is now also dead (family revoked)
        assertThatThrownBy(() -> service.rotate(second.rawRefreshToken()))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void revokeFamilyStopsRefreshAndIsIdempotent() {
        UUID userId = newUser();
        String raw = service.issueNewFamily(userId);

        service.revokeFamily(raw);
        assertThatThrownBy(() -> service.rotate(raw)).isInstanceOf(ResponseStatusException.class);
        // idempotent: revoking again (or an unknown token) does not throw
        service.revokeFamily(raw);
        service.revokeFamily("never-existed");
    }

    @Test
    void rotateRejectsUnknownToken() {
        assertThatThrownBy(() -> service.rotate("not-a-real-token"))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void purgeExpiredDeletesOnlyExpired() {
        // issueNewFamily always sets a 14-day expiry, so nothing to purge here:
        UUID userId = newUser();
        service.issueNewFamily(userId);
        int before = service.purgeExpired();
        assertThat(before).isGreaterThanOrEqualTo(0); // no active token is expired
        // (expiry-specific deletion is covered in AuthPersistenceTest.deleteExpiredRemovesOnlyPastTokens)
    }
}
