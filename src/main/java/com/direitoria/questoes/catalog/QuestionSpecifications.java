package com.direitoria.questoes.catalog;

import com.direitoria.questoes.domain.Difficulty;
import com.direitoria.questoes.domain.Question;
import com.direitoria.questoes.domain.QuestionType;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;

public final class QuestionSpecifications {

    private QuestionSpecifications() {
    }

    public static Specification<Question> build(
            Integer subjectId,
            Integer examBoardId,
            Integer agencyId,
            Short year,
            QuestionType type,
            Difficulty difficulty,
            String search,
            List<String> historySourceIds,
            boolean historyExcludes) {
        return Specification.allOf(
                hasSubject(subjectId),
                hasExamBoard(examBoardId),
                hasAgency(agencyId),
                hasYear(year),
                hasType(type),
                hasDifficulty(difficulty),
                matchesSearch(search),
                matchesHistory(historySourceIds, historyExcludes));
    }

    private static Specification<Question> hasSubject(Integer subjectId) {
        return (root, query, cb) -> {
            if (subjectId == null) {
                return null;
            }
            if (query != null) {
                query.distinct(true);
            }
            return cb.equal(root.join("subjects").get("id"), subjectId);
        };
    }

    private static Specification<Question> hasExamBoard(Integer examBoardId) {
        return (root, query, cb) ->
                examBoardId == null ? null : cb.equal(root.get("examBoard").get("id"), examBoardId);
    }

    private static Specification<Question> hasAgency(Integer agencyId) {
        return (root, query, cb) ->
                agencyId == null ? null : cb.equal(root.get("agency").get("id"), agencyId);
    }

    private static Specification<Question> hasYear(Short year) {
        return (root, query, cb) -> year == null ? null : cb.equal(root.get("ano"), year);
    }

    private static Specification<Question> hasType(QuestionType type) {
        return (root, query, cb) -> type == null ? null : cb.equal(root.get("tipo"), type);
    }

    private static Specification<Question> hasDifficulty(Difficulty difficulty) {
        return (root, query, cb) ->
                difficulty == null ? null : cb.equal(root.get("nivel"), difficulty);
    }

    private static Specification<Question> matchesSearch(String search) {
        return (root, query, cb) -> {
            if (search == null || search.isBlank()) {
                return null;
            }
            return cb.like(cb.lower(root.get("enunciado")), "%" + search.toLowerCase() + "%");
        };
    }

    /**
     * `historySourceIds == null` means no history filter at all — the endpoint stays
     * exactly as it was.
     *
     * An EMPTY list is meaningful and must not be confused with null: for
     * `correct`/`wrong` it means "the student has none", so nothing matches; for
     * `unanswered` (excludes = true) it means "the student answered nothing", so
     * everything matches.
     */
    private static Specification<Question> matchesHistory(
            List<String> historySourceIds, boolean historyExcludes) {
        return (root, query, cb) -> {
            if (historySourceIds == null) {
                return null;
            }
            if (historySourceIds.isEmpty()) {
                return historyExcludes ? cb.conjunction() : cb.disjunction();
            }
            var in = root.get("sourceId").in(historySourceIds);
            return historyExcludes ? cb.not(in) : in;
        };
    }
}
