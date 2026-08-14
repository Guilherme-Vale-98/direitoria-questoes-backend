package com.direitoria.questoes.auth;

import com.direitoria.questoes.domain.RefreshToken;
import com.direitoria.questoes.repository.RefreshTokenRepository;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class RefreshTokenService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();

    private final RefreshTokenRepository repository;
    private final RefreshTokenHasher hasher;
    private final long ttlDays;

    public RefreshTokenService(
            RefreshTokenRepository repository,
            RefreshTokenHasher hasher,
            @Value("${app.refresh.ttl-days}") long ttlDays) {
        this.repository = repository;
        this.hasher = hasher;
        this.ttlDays = ttlDays;
    }

    private static String newRawToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return B64.encodeToString(bytes);
    }

    private Instant expiry() {
        return Instant.now().plus(ttlDays, ChronoUnit.DAYS);
    }

    @Transactional
    public String issueNewFamily(UUID userId) {
        String raw = newRawToken();
        repository.save(new RefreshToken(userId, hasher.hash(raw), UUID.randomUUID(), expiry()));
        return raw;
    }

    // noRollbackFor: reuse detection commits the family revocation even though we
    // throw a 401 afterwards — the revocation must NOT be undone with the exception.
    @Transactional(noRollbackFor = ResponseStatusException.class)
    public RotationResult rotate(String rawToken) {
        RefreshToken current = repository.findByTokenHash(hasher.hash(rawToken))
                .orElseThrow(this::invalid);

        if (current.getRevokedAt() != null) {
            // Reuse of an already-rotated token => probable theft. Kill the whole family.
            repository.revokeFamily(current.getFamilyId(), Instant.now());
            throw invalid();
        }
        if (current.getExpiresAt().isBefore(Instant.now())) {
            throw invalid();
        }

        String newRaw = newRawToken();
        RefreshToken next = repository.save(
                new RefreshToken(current.getUserId(), hasher.hash(newRaw), current.getFamilyId(), expiry()));
        current.setRevokedAt(Instant.now());
        current.setReplacedBy(next.getId());
        repository.save(current);
        return new RotationResult(current.getUserId(), newRaw);
    }

    @Transactional
    public void revokeFamily(String rawToken) {
        repository.findByTokenHash(hasher.hash(rawToken))
                .ifPresent(t -> repository.revokeFamily(t.getFamilyId(), Instant.now()));
    }

    @Transactional
    public int purgeExpired() {
        return repository.deleteExpired(Instant.now());
    }

    private ResponseStatusException invalid() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid refresh token");
    }
}
