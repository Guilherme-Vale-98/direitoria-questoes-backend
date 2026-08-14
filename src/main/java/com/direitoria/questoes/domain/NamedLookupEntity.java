package com.direitoria.questoes.domain;

import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;

/**
 * Shared mapping for the canonical lookup tables (disciplina/banca/orgao), each a
 * (serial id, text nome) pair. Subclasses only add {@code @Entity}/{@code @Table}.
 */
@MappedSuperclass
public abstract class NamedLookupEntity implements NamedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false)
    private String nome;

    protected NamedLookupEntity() {
    }

    protected NamedLookupEntity(String nome) {
        this.nome = nome;
    }

    @Override
    public Integer getId() {
        return id;
    }

    @Override
    public String getNome() {
        return nome;
    }
}
