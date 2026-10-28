package com.direitoria.questoes.admin;

import com.direitoria.questoes.domain.QuestionType;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Checks a create before anything is written (Bloco 3C spec §4, "Server rules"). Pure — no
 * database. Spring never parses the PDF: it checks the magic bytes and the JSON's shape and
 * bounds, and sniffs every figure like 3B's images.
 */
final class ImportValidator {

    static final long MAX_PDF_BYTES = 20L * 1024 * 1024;
    static final long MAX_FIGURE_BYTES = 2L * 1024 * 1024;
    static final long MAX_FIGURES_TOTAL_BYTES = 8L * 1024 * 1024;
    static final int MAX_QUESTIONS = 300;
    static final int MAX_FIGURES_PER_QUESTION = 5;

    private static final byte[] PDF_MAGIC = "%PDF-".getBytes(StandardCharsets.US_ASCII);
    private static final Set<String> MC_ANSWERS = Set.of("A", "B", "C", "D", "E", ImportRules.ANULADA);
    private static final Set<String> CE_ANSWERS = Set.of("CERTO", "ERRADO", ImportRules.ANULADA);

    /** One `figuras` file of the create. */
    record Figure(byte[] bytes, String name) {
    }

    private ImportValidator() {
    }

    /** @return the importação's tipo */
    static QuestionType validate(ImportJson.ParseResult r, byte[] pdf, List<Figure> figures) {
        if (pdf.length > MAX_PDF_BYTES) {
            throw AdminApiException.pdfTooLarge();
        }
        if (!startsWith(pdf, PDF_MAGIC)) {
            throw AdminApiException.notAPdf();
        }
        if (r == null) {
            throw invalid("Envie o resultado da leitura do PDF.");
        }
        QuestionType type = parseType(r.type());
        if (r.fileName() == null || r.fileName().isBlank()) {
            throw invalid("Informe o nome do arquivo.");
        }
        if (r.pageCount() == null || r.pageCount() < 1) {
            throw invalid("Número de páginas inválido.");
        }
        List<ImportJson.ParsedQuestion> qs = orEmpty(r.questions());
        if (qs.isEmpty() || qs.size() > MAX_QUESTIONS) {
            throw invalid("A importação precisa ter de 1 a 300 questões.");
        }

        long total = 0;
        for (Figure f : figures) {
            if (f.bytes().length > MAX_FIGURE_BYTES) {
                throw AdminApiException.imageTooLarge("Figura muito grande (máx. 2 MB): " + f.name() + ".");
            }
            total += f.bytes().length;
            if (ImageSniffer.detect(f.bytes()).isEmpty()) {
                throw AdminApiException.unsupportedImage(f.name());
            }
        }
        if (total > MAX_FIGURES_TOTAL_BYTES) {
            throw AdminApiException.imageTooLarge("As figuras somam mais de 8 MB.");
        }

        Set<Integer> numbers = new HashSet<>();
        Set<Integer> partsUsed = new HashSet<>();
        for (ImportJson.ParsedQuestion q : qs) {
            if (q == null || q.number() == null || q.number() < 1 || !numbers.add(q.number())) {
                throw invalid("Números de questão repetidos ou inválidos.");
            }
            int n = q.number();
            if (q.statement() == null) {
                throw invalid("Questão " + n + " sem enunciado.");
            }
            if (q.options() != null && !q.options().isEmpty()) {
                if (type == QuestionType.CERTO_ERRADO) {
                    throw invalid("Questão " + n + ": certo/errado não tem alternativas.");
                }
                if (!AdminQuestionValidator.LETTERS.containsAll(q.options().keySet())) {
                    throw invalid("Questão " + n + ": alternativas inválidas.");
                }
            }
            for (ImportJson.Crop c : orEmpty(q.crop())) {
                if (c == null || c.page() == null || c.page() < 1 || c.page() > r.pageCount()) {
                    throw invalid("Questão " + n + ": recorte fora do PDF.");
                }
            }
            List<ImportJson.FigureRef> figs = orEmpty(q.figures());
            if (figs.size() > MAX_FIGURES_PER_QUESTION) {
                throw invalid("Questão " + n + ": mais de 5 figuras.");
            }
            for (ImportJson.FigureRef f : figs) {
                if (f == null || f.part() == null || f.part() < 0 || f.part() >= figures.size() || !partsUsed.add(f.part())
                        || f.page() == null || f.page() < 1 || f.page() > r.pageCount()) {
                    throw invalid("Questão " + n + ": figura inválida.");
                }
            }
        }
        if (partsUsed.size() != figures.size()) {
            throw invalid("Há figuras enviadas sem questão.");
        }
        validateGabarito(r.gabarito(), type);
        validateRanges(r.subjectRanges());
        return type;
    }

    private static void validateGabarito(ImportJson.Gabarito g, QuestionType type) {
        if (g == null) {
            return;
        }
        Set<String> allowed = type == QuestionType.MULTIPLA_ESCOLHA ? MC_ANSWERS : CE_ANSWERS;
        Set<Integer> keys = new HashSet<>();
        for (ImportJson.Section s : orEmpty(g.sections())) {
            if (s == null || s.key() == null || !keys.add(s.key()) || s.answers() == null) {
                throw invalid("Seções do gabarito inválidas.");
            }
            for (Map.Entry<String, String> a : s.answers().entrySet()) {
                if (!a.getKey().matches("[1-9][0-9]{0,2}") || !allowed.contains(a.getValue())) {
                    throw invalid("Gabarito com resposta inválida: " + a.getValue() + ".");
                }
            }
        }
        List<ImportJson.Candidate> candidates = orEmpty(g.candidates());
        for (ImportJson.Candidate c : candidates) {
            if (c == null || c.keys() == null || c.keys().isEmpty() || !keys.containsAll(c.keys())) {
                throw invalid("Candidato de gabarito inválido.");
            }
        }
        if (g.suggested() != null && (g.suggested() < 0 || g.suggested() >= candidates.size())) {
            throw invalid("Sugestão de gabarito inválida.");
        }
    }

    /** Disciplina ranges: 1 ≤ from ≤ to, never overlapping. Also used by PUT /{id}. */
    static void validateRanges(List<ImportJson.Range> ranges) {
        List<ImportJson.Range> rs = new ArrayList<>(orEmpty(ranges));
        for (ImportJson.Range x : rs) {
            if (x == null || x.from() == null || x.to() == null || x.from() < 1 || x.from() > x.to()) {
                throw AdminApiException.validation(Map.of("subjectRanges", "Faixa de disciplina inválida."));
            }
        }
        rs.sort(Comparator.comparing(ImportJson.Range::from));
        for (int i = 1; i < rs.size(); i++) {
            if (rs.get(i).from() <= rs.get(i - 1).to()) {
                throw AdminApiException.validation(Map.of("subjectRanges", "As faixas de disciplina não podem se sobrepor."));
            }
        }
    }

    /** Chosen gabarito sections: they must exist and must not share a questão number. Also used by PUT /{id}. */
    static void validateSections(ImportJson.Gabarito g, List<Integer> keys) {
        if (keys == null || keys.isEmpty()) {
            return;
        }
        if (g == null) {
            throw AdminApiException.validation(Map.of("gabaritoSections", "Esta importação não tem gabarito."));
        }
        Map<Integer, ImportJson.Section> byKey = new HashMap<>();
        for (ImportJson.Section s : orEmpty(g.sections())) {
            byKey.put(s.key(), s);
        }
        Set<Integer> distinct = new HashSet<>();
        Set<String> numbers = new HashSet<>();
        for (Integer k : keys) {
            ImportJson.Section s = byKey.get(k);
            if (s == null || !distinct.add(k)) {
                throw AdminApiException.validation(Map.of("gabaritoSections", "Seção de gabarito inválida."));
            }
            for (String n : s.answers().keySet()) {
                if (!numbers.add(n)) {
                    throw AdminApiException.validation(Map.of("gabaritoSections",
                            "As seções escolhidas repetem a questão " + n + "."));
                }
            }
        }
    }

    private static QuestionType parseType(String raw) {
        try {
            return QuestionType.valueOf(raw == null ? "" : raw.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw invalid("Tipo inválido.");
        }
    }

    private static AdminApiException invalid(String message) {
        return AdminApiException.validation(Map.of("importacao", message));
    }

    private static boolean startsWith(byte[] b, byte[] magic) {
        if (b.length < magic.length) {
            return false;
        }
        for (int i = 0; i < magic.length; i++) {
            if (b[i] != magic[i]) {
                return false;
            }
        }
        return true;
    }

    static <T> List<T> orEmpty(List<T> l) {
        return l == null ? List.of() : l;
    }
}
