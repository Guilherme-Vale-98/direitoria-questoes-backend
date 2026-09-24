package com.direitoria.questoes.dto;

/** `url` is always one of OUR paths — never the source CDN. See docs/adr/0008. */
public record AssetDto(short ordem, String url, String contentType) {
}
