package com.direitoria.questoes.auth;

import com.direitoria.questoes.TestcontainersConfiguration;
import com.direitoria.questoes.domain.RefreshToken;
import com.direitoria.questoes.domain.User;
import com.direitoria.questoes.repository.RefreshTokenRepository;
import com.direitoria.questoes.repository.UserRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class RefreshTokenPurgeJobTest {

    @Autowired RefreshTokenPurgeJob job;
    @Autowired RefreshTokenRepository tokens;
    @Autowired UserRepository users;

    @Test
    void purgeDeletesExpiredButKeepsActive() {
        User u = users.save(new User("T", "U", "u_" + UUID.randomUUID() + "@x.com", "hash"));
        String activeHash = "active_" + UUID.randomUUID();
        tokens.save(new RefreshToken(u.getId(), "expired_" + UUID.randomUUID(), UUID.randomUUID(),
                Instant.now().minus(1, ChronoUnit.DAYS)));
        tokens.save(new RefreshToken(u.getId(), activeHash, UUID.randomUUID(),
                Instant.now().plus(1, ChronoUnit.DAYS)));

        job.purge();

        assertThat(tokens.findByTokenHash(activeHash)).isPresent();
        // the expired one is gone (count of expired for this user is 0)
        assertThat(tokens.findAll().stream()
                .filter(t -> t.getUserId().equals(u.getId()) && t.getExpiresAt().isBefore(Instant.now()))
                .count()).isZero();
    }
}
