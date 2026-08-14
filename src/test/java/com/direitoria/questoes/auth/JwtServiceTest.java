package com.direitoria.questoes.auth;

import com.direitoria.questoes.config.JwtConfig;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.util.List;
import java.util.UUID;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private static final String SECRET = "test-only-secret-key-at-least-32-bytes-long-0123456789";
    private static final String ISSUER = "direitoria";
    private static final String AUD = "direitoria-app";

    private final SecretKeySpec key = JwtConfig.secretKey(SECRET);
    private final JwtService service = new JwtService(
            new NimbusJwtEncoder(new ImmutableSecret<>(key)), ISSUER, AUD, 900);

    private NimbusJwtDecoder decoder(String issuer, String aud) {
        NimbusJwtDecoder d = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        d.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                new JwtTimestampValidator(),
                new JwtIssuerValidator(issuer),
                new JwtClaimValidator<List<String>>(JwtClaimNames.AUD, a -> a != null && a.contains(aud))));
        return d;
    }

    @Test
    void issuesTokenWithSubjectAndRoles() {
        UUID id = UUID.randomUUID();
        String token = service.issueAccessToken(id, List.of("USER"));
        Jwt jwt = decoder(ISSUER, AUD).decode(token);
        assertThat(jwt.getSubject()).isEqualTo(id.toString());
        assertThat(jwt.getClaimAsStringList("roles")).containsExactly("USER");
        assertThat(jwt.<Object>getClaim("email")).isNull();
        assertThat(jwt.<Object>getClaim("first_name")).isNull();
    }

    @Test
    void rejectsTamperedSignature() {
        String token = service.issueAccessToken(UUID.randomUUID(), List.of("USER"));
        String tampered = token.substring(0, token.length() - 2)
                + (token.endsWith("a") ? "b" : "a");
        assertThatThrownBy(() -> decoder(ISSUER, AUD).decode(tampered)).isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsWrongIssuer() {
        String token = service.issueAccessToken(UUID.randomUUID(), List.of("USER"));
        assertThatThrownBy(() -> decoder("someone-else", AUD).decode(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsWrongAudience() {
        String token = service.issueAccessToken(UUID.randomUUID(), List.of("USER"));
        assertThatThrownBy(() -> decoder(ISSUER, "other-app").decode(token)).isInstanceOf(JwtException.class);
    }
}
