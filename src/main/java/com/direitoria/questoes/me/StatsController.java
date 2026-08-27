package com.direitoria.questoes.me;

import com.direitoria.questoes.dto.StatsResponse;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** "Meu desempenho". Authentication is enforced by SecurityConfig's anyRequest().authenticated(). */
@RestController
@RequestMapping("/api/me/stats")
public class StatsController {

    private final StatsService stats;

    public StatsController(StatsService stats) {
        this.stats = stats;
    }

    @GetMapping
    public StatsResponse stats(@AuthenticationPrincipal Jwt jwt) {
        return stats.forUser(UUID.fromString(jwt.getSubject()));
    }
}
