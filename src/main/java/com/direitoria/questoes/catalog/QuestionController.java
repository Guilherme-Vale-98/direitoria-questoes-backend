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

    @GetMapping
    public PageResponse<QuestionResponse> list(
            @RequestParam(required = false) Integer subjectId,
            @RequestParam(required = false) Integer examBoardId,
            @RequestParam(required = false) Integer agencyId,
            @RequestParam(required = false) Short year,
            @RequestParam(required = false) QuestionType type,
            @RequestParam(required = false) Difficulty difficulty,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_SIZE);
        return PageResponse.from(
                service.search(
                        subjectId, examBoardId, agencyId, year, type, difficulty, search,
                        PageRequest.of(safePage, safeSize)));
    }

    @GetMapping("/{id}")
    public QuestionResponse detail(@PathVariable UUID id) {
        return service.findByPublicId(id)
                .orElseThrow(() ->
                        new ResponseStatusException(HttpStatus.NOT_FOUND, "Questão não encontrada"));
    }

    /** Judge a student's answer server-side and reveal the gabarito + comentário. Public. */
    @PostMapping("/{id}/answer")
    public AnswerResult answer(@PathVariable UUID id, @Valid @RequestBody AnswerRequest request) {
        return service.answer(id, request.chosenAnswer())
                .orElseThrow(() ->
                        new ResponseStatusException(HttpStatus.NOT_FOUND, "Questão não encontrada"));
    }
}
