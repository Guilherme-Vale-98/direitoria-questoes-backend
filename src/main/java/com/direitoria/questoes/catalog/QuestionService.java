package com.direitoria.questoes.catalog;

import com.direitoria.questoes.domain.Difficulty;
import com.direitoria.questoes.domain.Question;
import com.direitoria.questoes.domain.QuestionType;
import com.direitoria.questoes.dto.AnswerResult;
import com.direitoria.questoes.dto.QuestionResponse;
import com.direitoria.questoes.repository.QuestionRepository;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class QuestionService {

    private final QuestionRepository repository;

    public QuestionService(QuestionRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public Page<QuestionResponse> search(
            Integer subjectId,
            Integer examBoardId,
            Integer agencyId,
            Short year,
            QuestionType type,
            Difficulty difficulty,
            String search,
            Pageable pageable) {
        return repository
                .findAll(
                        QuestionSpecifications.build(
                                subjectId, examBoardId, agencyId, year, type, difficulty, search),
                        pageable)
                .map(QuestionMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public Optional<QuestionResponse> findByPublicId(UUID publicId) {
        return repository.findByPublicId(publicId).map(QuestionMapper::toResponse);
    }

    /**
     * Judge a student's answer server-side (the gabarito is no longer sent to the
     * browser). Empty when the question doesn't exist (→ 404); throws 400 when the
     * chosen answer isn't a valid alternative for the question.
     */
    @Transactional(readOnly = true)
    public Optional<AnswerResult> answer(UUID publicId, String chosenAnswer) {
        return repository.findByPublicId(publicId).map(q -> judge(q, chosenAnswer));
    }

    private static AnswerResult judge(Question q, String chosenAnswer) {
        String chosen = normalize(chosenAnswer);
        if (!validTokens(q).contains(chosen)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Alternativa inválida");
        }
        boolean correct = chosen.equals(normalize(q.getGabarito()));
        return new AnswerResult(correct, q.getGabarito(), q.getComentario());
    }

    /** Valid answer tokens: CERTO/ERRADO for true-false, else the labels of the present options. */
    private static Set<String> validTokens(Question q) {
        if (q.getTipo() == QuestionType.CERTO_ERRADO) {
            return Set.of("CERTO", "ERRADO");
        }
        Set<String> tokens = new LinkedHashSet<>();
        if (q.getOpcaoA() != null) tokens.add("A");
        if (q.getOpcaoB() != null) tokens.add("B");
        if (q.getOpcaoC() != null) tokens.add("C");
        if (q.getOpcaoD() != null) tokens.add("D");
        if (q.getOpcaoE() != null) tokens.add("E");
        return tokens;
    }

    private static String normalize(String s) {
        return s == null ? "" : s.trim().toUpperCase();
    }
}
