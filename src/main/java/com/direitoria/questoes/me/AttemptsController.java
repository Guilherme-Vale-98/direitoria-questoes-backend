package com.direitoria.questoes.me;

import com.direitoria.questoes.dto.AttemptDto;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Auth enforced by SecurityConfig's anyRequest().authenticated(). */
@RestController
@RequestMapping("/api/me/attempts")
public class AttemptsController {

    private final AttemptHistoryService history;

    public AttemptsController(AttemptHistoryService history) {
        this.history = history;
    }

    @GetMapping
    public Map<String, List<AttemptDto>> attempts(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "") List<UUID> questionIds) {
        return history.historyFor(UUID.fromString(jwt.getSubject()), questionIds);
    }
}
