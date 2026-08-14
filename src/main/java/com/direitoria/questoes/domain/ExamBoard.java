package com.direitoria.questoes.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "banca")
public class ExamBoard extends NamedLookupEntity {

    protected ExamBoard() {
    }

    public ExamBoard(String nome) {
        super(nome);
    }
}
