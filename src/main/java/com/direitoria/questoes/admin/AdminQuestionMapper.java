package com.direitoria.questoes.admin;

import com.direitoria.questoes.admin.dto.AdminQuestionResponse;
import com.direitoria.questoes.catalog.LookupMapper;
import com.direitoria.questoes.domain.Judging;
import com.direitoria.questoes.domain.Question;
import com.direitoria.questoes.domain.QuestionType;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class AdminQuestionMapper {

    private AdminQuestionMapper() {
    }

    /** Must run inside a transaction: subjects and assets are lazy. */
    static AdminQuestionResponse toResponse(Question q, long attemptCount, long studentCount) {
        boolean answered = attemptCount > 0;
        Map<String, String> options = new LinkedHashMap<>();
        if (q.getTipo() == QuestionType.MULTIPLA_ESCOLHA) {
            options.put("A", q.getOpcaoA());
            options.put("B", q.getOpcaoB());
            options.put("C", q.getOpcaoC());
            options.put("D", q.getOpcaoD());
            options.put("E", q.getOpcaoE());
        }
        List<AdminQuestionResponse.Image> images = q.getAssets() == null ? List.of() : q.getAssets().stream()
                .map(a -> new AdminQuestionResponse.Image(
                        a.getOrdem(), "/api/questions/" + q.getPublicId() + "/assets/" + a.getOrdem()))
                .toList();
        return new AdminQuestionResponse(
                q.getPublicId(),
                q.getTipo().name(),
                q.getTextoRef(),
                q.getEnunciado(),
                options,
                Judging.normalize(q.getGabarito()),
                q.getComentario(),
                q.getSubjects().stream().map(LookupMapper::toLookup).toList(),
                q.getExamBoard() == null ? null : LookupMapper.toLookup(q.getExamBoard()),
                q.getAgency() == null ? null : LookupMapper.toLookup(q.getAgency()),
                q.getAno(),
                q.getCargo(),
                q.getNivel() == null ? null : q.getNivel().name(),
                images,
                attemptCount,
                studentCount,
                new AdminQuestionResponse.Locked(answered, answered));
    }
}
