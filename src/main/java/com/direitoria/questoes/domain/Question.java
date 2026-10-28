package com.direitoria.questoes.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "questao")
public class Question {

    /** source_id prefix of questões created in the app (mirrors `quest:<id>`). */
    public static final String MANUAL_PREFIX = "manual:";

    /** source_id prefix of questões imported from a PDF (Bloco 3C, docs/adr/0011). */
    public static final String PDF_PREFIX = "pdf:";

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

    /** The reading passage this questão depends on. Null for most rows. */
    @Column(name = "texto_ref")
    private String textoRef;

    // Raw text columns, NOT NULL in questao. Only the ingest pipeline and `normalize`
    // read them; the app writes them so a manual row is as complete as a pipeline row
    // (Bloco 3B spec §3). Spring never reads them back.
    @Column(name = "banca")
    private String bancaRaw;
    @Column(name = "orgao")
    private String orgaoRaw;
    @Column(name = "materia")
    private String materiaRaw;
    @Column(name = "assunto")
    private String assuntoRaw;

    @OneToMany(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_id", referencedColumnName = "source_id")
    @OrderBy("ordem ASC")
    private List<QuestionAsset> assets;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "questao_disciplina",
            joinColumns = @JoinColumn(name = "source_id"),
            inverseJoinColumns = @JoinColumn(name = "disciplina_id"))
    private List<Subject> subjects;

    protected Question() {
    }

    /**
     * A questão created by the admin. `id` (public uuid) and `content_key` are left to
     * the database; read the id back after flushing.
     */
    public static Question createManual(QuestionContent c) {
        return create(MANUAL_PREFIX, c);
    }

    /** A questão imported from a PDF — the same row as a typed one, another provenance. */
    public static Question createImported(QuestionContent c) {
        return create(PDF_PREFIX, c);
    }

    private static Question create(String prefix, QuestionContent c) {
        Question q = new Question();
        q.sourceId = prefix + UUID.randomUUID();
        q.assuntoRaw = ""; // not collected; nothing reads it
        q.subjects = new ArrayList<>();
        q.assets = new ArrayList<>();
        q.apply(c);
        return q;
    }

    /** Applies an admin edit. `answered` = the questão already has tentativas. */
    public void edit(QuestionContent c, boolean answered) {
        assertEditable(c.type(), c.filledLetters(), answered);
        apply(c);
    }

    /**
     * Once a questão has tentativas its type and its set of alternatives are locked:
     * students chose letters, and a stored letter must keep pointing at something
     * (docs/adr/0009). Text, gabarito and classification stay editable.
     */
    public void assertEditable(QuestionType nextType, Set<String> nextLetters, boolean answered) {
        if (!answered) {
            return;
        }
        Map<String, String> errors = new LinkedHashMap<>();
        if (nextType != tipo) {
            errors.put("type", "O tipo não pode mudar: a questão já tem tentativas.");
        } else if (tipo == QuestionType.MULTIPLA_ESCOLHA && !nextLetters.equals(filledLetters())) {
            errors.put("options", "Não é possível adicionar ou remover alternativas: a questão já tem tentativas.");
        }
        if (!errors.isEmpty()) {
            throw new QuestionLockedException(errors);
        }
    }

    /** Letters this questão has today. Empty for CERTO_ERRADO, whatever its columns hold. */
    public Set<String> filledLetters() {
        return tipo == QuestionType.CERTO_ERRADO
                ? Set.of()
                : letters(opcaoA, opcaoB, opcaoC, opcaoD, opcaoE);
    }

    static Set<String> letters(String a, String b, String c, String d, String e) {
        Set<String> s = new LinkedHashSet<>();
        if (a != null) s.add("A");
        if (b != null) s.add("B");
        if (c != null) s.add("C");
        if (d != null) s.add("D");
        if (e != null) s.add("E");
        return s;
    }

    private void apply(QuestionContent c) {
        boolean mc = c.type() == QuestionType.MULTIPLA_ESCOLHA;
        tipo = c.type();
        textoRef = c.referenceText();
        enunciado = c.statement();
        opcaoA = mc ? c.optionA() : null;
        opcaoB = mc ? c.optionB() : null;
        opcaoC = mc ? c.optionC() : null;
        opcaoD = mc ? c.optionD() : null;
        opcaoE = mc ? c.optionE() : null;
        gabarito = Judging.normalize(c.answer());
        comentario = c.commentary();
        examBoard = c.examBoard();
        agency = c.agency();
        bancaRaw = c.examBoard().getNome();
        orgaoRaw = c.agency().getNome();
        ano = c.year();
        cargo = c.role() == null ? "" : c.role();
        nivel = c.difficulty();
        // Mutate in place: the managed collection is what Hibernate tracks.
        subjects.clear();
        subjects.addAll(c.subjects());
        materiaRaw = c.subjects().stream().map(Subject::getNome).collect(Collectors.joining(" / "));
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

    public String getTextoRef() {
        return textoRef;
    }

    public List<QuestionAsset> getAssets() {
        return assets;
    }

    public List<Subject> getSubjects() {
        return subjects;
    }
}
