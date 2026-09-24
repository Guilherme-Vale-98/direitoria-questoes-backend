package com.direitoria.questoes.catalog;

import com.direitoria.questoes.domain.Difficulty;
import com.direitoria.questoes.domain.Question;
import com.direitoria.questoes.domain.QuestionAsset;
import com.direitoria.questoes.dto.AssetDto;
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
                q.getAno(),
                referenceTextFor(q),
                buildAssets(q));
    }

    /**
     * Reference text that merely repeats the enunciado is noise, not context: the
     * student would read the same paragraph twice, once in the quoted block and
     * again in the question. 60 of the catalog's 119 values are like this — 40
     * identical, 20 already quoted inline. Compared normalized for whitespace and
     * case, because the duplication is rarely byte-exact.
     */
    private static String referenceTextFor(Question q) {
        String texto = q.getTextoRef();
        if (texto == null || texto.isBlank()) {
            return null;
        }
        return normalize(q.getEnunciado()).contains(normalize(texto)) ? null : texto;
    }

    private static String normalize(String s) {
        return s == null ? "" : s.replaceAll("\\s+", " ").trim().toLowerCase();
    }

    private static List<AssetDto> buildAssets(Question q) {
        List<QuestionAsset> assets = q.getAssets();
        if (assets == null) {
            return List.of();
        }
        return assets.stream()
                .map(a -> new AssetDto(
                        a.getOrdem(),
                        "/api/questions/" + q.getPublicId() + "/assets/" + a.getOrdem(),
                        a.getContentType()))
                .toList();
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
