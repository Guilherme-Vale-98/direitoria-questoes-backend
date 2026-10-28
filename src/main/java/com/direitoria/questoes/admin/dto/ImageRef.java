package com.direitoria.questoes.admin.dto;

/** One slot of the final image order: an existing asset kept by ordem, or the i-th uploaded file. */
public record ImageRef(Integer keep, Integer upload) {
}
