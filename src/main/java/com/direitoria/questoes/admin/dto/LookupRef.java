package com.direitoria.questoes.admin.dto;

/** A disciplina/banca/órgão picked in the form: an existing one ({id}) or one to create ({name}). */
public record LookupRef(Integer id, String name) {
}
