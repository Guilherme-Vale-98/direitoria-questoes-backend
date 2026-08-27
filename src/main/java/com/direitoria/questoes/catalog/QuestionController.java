package com.direitoria.questoes.catalog;

import com.direitoria.questoes.domain.Difficulty;
import com.direitoria.questoes.domain.QuestionType;
import com.direitoria.questoes.dto.AnswerRequest;
import com.direitoria.questoes.dto.AnswerResult;
import com.direitoria.questoes.dto.PageResponse;
import com.direitoria.questoes.dto.QuestionResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/questions")
public class QuestionController {

    private static final int MAX_SIZE = 100;

    private final QuestionService service;

    public QuestionController(QuestionService service) {
        this.service = service;
    }

    /**
     * The catalog. Public — EXCEPT when `historyStatus` is present, which filters by
     * the caller's own tentativas and therefore requires a logged-in student.
     */
    @GetMapping
    public PageResponse<QuestionResponse> list(
            @RequestParam(required = false) Integer subjectId,
            @RequestParam(required = false) Integer examBoardId,
            @RequestParam(required = false) Integer agencyId,
            @RequestParam(required = false) Short year,
            @RequestParam(required = false) QuestionType type,
            @RequestParam(required = false) Difficulty difficulty,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) HistoryStatus historyStatus,
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        if (historyStatus != null && jwt == null) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "Entre na sua conta para filtrar pelo seu histórico");
        }
        UUID userId = jwt == null ? null : UUID.fromString(jwt.getSubject());
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_SIZE);
        return PageResponse.from(
                service.search(
                        subjectId, examBoardId, agencyId, year, type, difficulty, search,
                        historyStatus, userId, PageRequest.of(safePage, safeSize)));
    }

    @GetMapping("/{id}")
    public QuestionResponse detail(@PathVariable UUID id) {
        return service.findByPublicId(id)
                .orElseThrow(() ->
                        new ResponseStatusException(HttpStatus.NOT_FOUND, "Questão não encontrada"));
    }

    /**
     * Judge a student's answer, reveal the gabarito + comentário, and record the
     * tentativa.
     *
     * The route is still permitAll() in SecurityConfig — transitional, for testing
     * convenience while the platform is open. Authentication is enforced HERE so an
     * unauthenticated caller gets a clean 401 the card can turn into an inline
     * "entre na sua conta" warning. When the platform closes (Bloco 4) this check
     * becomes redundant and the route joins the authenticated set. See docs/adr/0003.
     */
    @PostMapping("/{id}/answer")
    public AnswerResult answer(
            @PathVariable UUID id,
            @Valid @RequestBody AnswerRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        if (jwt == null) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "Entre na sua conta para responder");
        }
        UUID userId = UUID.fromString(jwt.getSubject());
        return service.answer(id, request.chosenAnswer(), userId)
                .orElseThrow(() ->
                        new ResponseStatusException(HttpStatus.NOT_FOUND, "Questão não encontrada"));
    }
}
