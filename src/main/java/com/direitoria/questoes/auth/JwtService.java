package com.direitoria.questoes.auth;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

    private final JwtEncoder encoder;
    private final String issuer;
    private final String audience;
    private final long accessTtlSeconds;

    public JwtService(
            JwtEncoder encoder,
            @Value("${app.jwt.issuer}") String issuer,
            @Value("${app.jwt.audience}") String audience,
            @Value("${app.jwt.access-ttl-seconds}") long accessTtlSeconds) {
        this.encoder = encoder;
        this.issuer = issuer;
        this.audience = audience;
        this.accessTtlSeconds = accessTtlSeconds;
    }

    public String issueAccessToken(UUID userId, Collection<String> roles) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(userId.toString())
                .issuer(issuer)
                .audience(List.of(audience))
                .issuedAt(now)
                .expiresAt(now.plusSeconds(accessTtlSeconds))
                .claim("roles", List.copyOf(roles))
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    public long getAccessTtlSeconds() {
        return accessTtlSeconds;
    }
}
