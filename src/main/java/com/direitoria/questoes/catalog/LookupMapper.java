package com.direitoria.questoes.catalog;

import com.direitoria.questoes.domain.NamedEntity;
import com.direitoria.questoes.dto.LookupResponse;

public final class LookupMapper {

    private LookupMapper() {
    }

    public static LookupResponse toLookup(NamedEntity entity) {
        return new LookupResponse(entity.getId(), entity.getNome());
    }
}
