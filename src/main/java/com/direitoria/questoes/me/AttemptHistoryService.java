package com.direitoria.questoes.me;

import com.direitoria.questoes.dto.AttemptDto;
import com.direitoria.questoes.repository.QuestionAttemptRepository;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Batch per-questão history. Batched deliberately so the public catalog endpoint
 * stays untouched — see the spec's API section.
 */
@Service
public class AttemptHistoryService {

    private final QuestionAttemptRepository attempts;

    public AttemptHistoryService(QuestionAttemptRepository attempts) {
        this.attempts = attempts;
    }

    @Transactional(readOnly = true)
    public Map<String, List<AttemptDto>> historyFor(UUID userId, Collection<UUID> questionIds) {
        if (questionIds.isEmpty()) {
            return Map.of();
        }
        Map<String, List<AttemptDto>> out = new LinkedHashMap<>();
        for (var row : attempts.historyFor(userId, questionIds)) {
            out.computeIfAbsent(row.getQuestionId().toString(), k -> new ArrayList<>())
                    .add(new AttemptDto(row.getAnsweredAt(), row.getChosenAnswer(), row.getCorrect()));
        }
        return out;
    }
}
