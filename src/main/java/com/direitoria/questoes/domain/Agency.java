package com.direitoria.questoes.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "orgao")
public class Agency extends NamedLookupEntity {

    protected Agency() {
    }

    public Agency(String nome) {
        super(nome);
    }
}
