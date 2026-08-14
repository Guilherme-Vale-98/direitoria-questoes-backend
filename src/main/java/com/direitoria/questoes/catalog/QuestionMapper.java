package com.direitoria.questoes.catalog;

import com.direitoria.questoes.domain.Difficulty;
import com.direitoria.questoes.domain.Question;
import com.direitoria.questoes.dto.OptionDto;
import com.direitoria.questoes.dto.QuestionResponse;
import java.util.ArrayList;
import java.util.List;

public final class QuestionMapper {

    private QuestionMapper() {
    }

    public static QuestionResponse toResponse(Question q) {
        Difficulty nivel = q.getNivel();
        return new QuestionResponse(
                q.getPublicId(),
                q.getEnunciado(),
                q.getTipo().name(),
                nivel == null ? null : nivel.name(),
                buildOptions(q),
                q.getSubjects().stream().map(LookupMapper::toLookup).toList(),
                q.getExamBoard() == null ? null : LookupMapper.toLookup(q.getExamBoard()),
                q.getAgency() == null ? null : LookupMapper.toLookup(q.getAgency()),
                q.getCargo(),
                q.getAno());
    }

    private static List<OptionDto> buildOptions(Question q) {
        List<OptionDto> options = new ArrayList<>();
        addOption(options, "A", q.getOpcaoA());
        addOption(options, "B", q.getOpcaoB());
        addOption(options, "C", q.getOpcaoC());
        addOption(options, "D", q.getOpcaoD());
        addOption(options, "E", q.getOpcaoE());
        return options;
    }

    private static void addOption(List<OptionDto> options, String label, String text) {
        if (text != null) {
            options.add(new OptionDto(label, text));
        }
    }
}
