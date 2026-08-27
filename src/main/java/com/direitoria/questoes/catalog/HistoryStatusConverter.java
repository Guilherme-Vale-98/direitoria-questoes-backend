package com.direitoria.questoes.catalog;

import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

/**
 * Spring's default enum binding for {@code @RequestParam} is case-sensitive
 * (exact {@code Enum.valueOf}), so `?historyStatus=wrong` would otherwise 400
 * before the controller body runs. The frontend sends lowercase values
 * (`wrong`, `correct`, `unanswered`), so this converter is required.
 */
@Component
class HistoryStatusConverter implements Converter<String, HistoryStatus> {

    @Override
    public HistoryStatus convert(String source) {
        return HistoryStatus.valueOf(source.trim().toUpperCase());
    }
}
