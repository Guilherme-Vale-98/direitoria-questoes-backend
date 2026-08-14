package com.direitoria.questoes;

import com.direitoria.questoes.domain.RefreshToken;
import com.direitoria.questoes.domain.Role;
import com.direitoria.questoes.domain.User;
import com.direitoria.questoes.repository.RefreshTokenRepository;
import com.direitoria.questoes.repository.RoleRepository;
import com.direitoria.questoes.repository.UserRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class AuthPersistenceTest {

    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired RefreshTokenRepository tokens;

    private String email() {
        return "u_" + UUID.randomUUID() + "@x.com";
    }

    @Test
    void savesUserWithSeededRoleAndFindsByEmail() {
        Role userRole = roles.findByAuthority("USER").orElseThrow();
        String email = email();
        User u = new User("Ana", "Souza", email, "hash");
        u.addRole(userRole);
        users.save(u);

        User found = users.findByEmail(email).orElseThrow();
        assertThat(found.getId()).isNotNull();
        assertThat(found.getRoles()).extracting(Role::getAuthority).containsExactly("USER");
        assertThat(users.existsByEmail(email)).isTrue();
    }

    @Test
    void rotatesAndRevokesRefreshTokenFamily() {
        User u = users.save(new User("Bo", "Reis", email(), "hash"));
        UUID family = UUID.randomUUID();
        Instant exp = Instant.now().plus(14, ChronoUnit.DAYS);
        tokens.save(new RefreshToken(u.getId(), "hash-1", family, exp));
        tokens.save(new RefreshToken(u.getId(), "hash-2", family, exp));

        int revoked = tokens.revokeFamily(family, Instant.now());
        assertThat(revoked).isEqualTo(2);
        assertThat(tokens.findByTokenHash("hash-1").orElseThrow().getRevokedAt()).isNotNull();
    }

    @Test
    void deleteExpiredRemovesOnlyPastTokens() {
        User u = users.save(new User("Cid", "Lima", email(), "hash"));
        tokens.save(new RefreshToken(u.getId(), "past", UUID.randomUUID(),
                Instant.now().minus(1, ChronoUnit.DAYS)));
        tokens.save(new RefreshToken(u.getId(), "future", UUID.randomUUID(),
                Instant.now().plus(1, ChronoUnit.DAYS)));

        int deleted = tokens.deleteExpired(Instant.now());
        assertThat(deleted).isEqualTo(1);
        assertThat(tokens.findByTokenHash("future")).isPresent();
        assertThat(tokens.findByTokenHash("past")).isEmpty();
    }
}
