package com.direitoria.questoes.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Metadata for one self-hosted image. Deliberately has NO `bytes` field: listing
 * questões must never drag image data into memory. Bytes are read only by
 * QuestionAssetRepository, when a browser actually asks for them.
 */
@Entity
@Table(name = "questao_asset")
public class QuestionAsset {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @Column(name = "source_id", insertable = false, updatable = false)
    private String sourceId;

    @Column(name = "ordem")
    private Short ordem;

    @Column(name = "content_type")
    private String contentType;

    protected QuestionAsset() {
    }

    public Integer getId() {
        return id;
    }

    public String getSourceId() {
        return sourceId;
    }

    public Short getOrdem() {
        return ordem;
    }

    public String getContentType() {
        return contentType;
    }
}
