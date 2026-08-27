package com.direitoria.questoes.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "questao")
public class Question {

    @Id
    @Column(name = "source_id")
    private String sourceId;

    // Public surrogate id (uuid). DB-managed (gen_random_uuid()); read-only here.
    @Column(name = "id", insertable = false, updatable = false)
    private UUID publicId;

    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "tipo")
    private QuestionType tipo;

    @Column(name = "enunciado")
    private String enunciado;

    @Column(name = "opcao_a")
    private String opcaoA;
    @Column(name = "opcao_b")
    private String opcaoB;
    @Column(name = "opcao_c")
    private String opcaoC;
    @Column(name = "opcao_d")
    private String opcaoD;
    @Column(name = "opcao_e")
    private String opcaoE;

    @Column(name = "gabarito")
    private String gabarito;

    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "nivel")
    private Difficulty nivel;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "banca_id")
    private ExamBoard examBoard;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "orgao_id")
    private Agency agency;

    @Column(name = "cargo")
    private String cargo;

    @Column(name = "ano")
    private Short ano;

    @Column(name = "comentario")
    private String comentario;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "questao_disciplina",
            joinColumns = @JoinColumn(name = "source_id"),
            inverseJoinColumns = @JoinColumn(name = "disciplina_id"))
    private List<Subject> subjects;

    protected Question() {
    }

    public UUID getPublicId() {
        return publicId;
    }

    /**
     * The internal catalog key. Needed to record a tentativa against this questão.
     * MUST NOT reach any DTO — QuestionMapper does not read it, and
     * QuestionControllerTest asserts it never appears in a response.
     */
    public String getSourceId() {
        return sourceId;
    }

    public QuestionType getTipo() {
        return tipo;
    }

    public String getEnunciado() {
        return enunciado;
    }

    public String getOpcaoA() {
        return opcaoA;
    }

    public String getOpcaoB() {
        return opcaoB;
    }

    public String getOpcaoC() {
        return opcaoC;
    }

    public String getOpcaoD() {
        return opcaoD;
    }

    public String getOpcaoE() {
        return opcaoE;
    }

    public String getGabarito() {
        return gabarito;
    }

    public Difficulty getNivel() {
        return nivel;
    }

    public ExamBoard getExamBoard() {
        return examBoard;
    }

    public Agency getAgency() {
        return agency;
    }

    public String getCargo() {
        return cargo;
    }

    public Short getAno() {
        return ano;
    }

    public String getComentario() {
        return comentario;
    }

    public List<Subject> getSubjects() {
        return subjects;
    }
}
