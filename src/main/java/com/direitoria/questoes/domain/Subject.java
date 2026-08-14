package com.direitoria.questoes.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "disciplina")
public class Subject extends NamedLookupEntity {

    protected Subject() {
    }

    public Subject(String nome) {
        super(nome);
    }
}
