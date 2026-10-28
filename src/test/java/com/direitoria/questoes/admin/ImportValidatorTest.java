package com.direitoria.questoes.admin;

import com.direitoria.questoes.domain.QuestionType;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ImportValidatorTest {

    private final ObjectMapper mapper = JsonMapper.builder().build();

    private ImportJson.ParseResult parse(Map<String, Object> m) {
        return mapper.convertValue(m, ImportJson.ParseResult.class);
    }

    private static List<ImportValidator.Figure> figures(byte[]... files) {
        List<ImportValidator.Figure> out = new ArrayList<>();
        for (byte[] f : files) {
            out.add(new ImportValidator.Figure(f, "fig.png"));
        }
        return out;
    }

    private static void fails(Runnable r, HttpStatus status, String code) {
        assertThatThrownBy(r::run).isInstanceOfSatisfying(AdminApiException.class, e -> {
            assertThat(e.getStatus()).isEqualTo(status);
            assertThat(e.getBody().get("code")).isEqualTo(code);
        });
    }

    private Map<String, Object> twoQuestions() {
        return ImportFixtures.result("MULTIPLA_ESCOLHA",
                new ArrayList<>(List.of(ImportFixtures.mc(1, "Um"), ImportFixtures.mc(2, "Dois"))));
    }

    @Test
    void aValidCreateReturnsItsTipo() {
        assertThat(ImportValidator.validate(parse(twoQuestions()), ImportFixtures.pdf(), List.of()))
                .isEqualTo(QuestionType.MULTIPLA_ESCOLHA);
    }

    @Test
    void thePdfMustStartWithItsMagicBytes() {
        fails(() -> ImportValidator.validate(parse(twoQuestions()), "hello".getBytes(), List.of()),
                HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_PDF");
    }

    @Test
    void aPdfOver20MbIs413() {
        byte[] big = new byte[(int) ImportValidator.MAX_PDF_BYTES + 1];
        System.arraycopy(ImportFixtures.pdf(), 0, big, 0, 5);
        fails(() -> ImportValidator.validate(parse(twoQuestions()), big, List.of()),
                HttpStatus.CONTENT_TOO_LARGE, "PDF_TOO_LARGE");
    }

    @Test
    void theTipoAndTheQuestionNumbersAreChecked() {
        Map<String, Object> badType = twoQuestions();
        badType.put("type", "DISCURSIVA");
        fails(() -> ImportValidator.validate(parse(badType), ImportFixtures.pdf(), List.of()), HttpStatus.BAD_REQUEST, "VALIDATION");

        Map<String, Object> repeated = ImportFixtures.result("MULTIPLA_ESCOLHA",
                new ArrayList<>(List.of(ImportFixtures.mc(1, "Um"), ImportFixtures.mc(1, "Outro"))));
        fails(() -> ImportValidator.validate(parse(repeated), ImportFixtures.pdf(), List.of()), HttpStatus.BAD_REQUEST, "VALIDATION");

        Map<String, Object> none = ImportFixtures.result("MULTIPLA_ESCOLHA", new ArrayList<>());
        fails(() -> ImportValidator.validate(parse(none), ImportFixtures.pdf(), List.of()), HttpStatus.BAD_REQUEST, "VALIDATION");
    }

    @Test
    void aCertoErradoQuestaoCarriesNoAlternatives() {
        Map<String, Object> r = ImportFixtures.result("CERTO_ERRADO", new ArrayList<>(List.of(ImportFixtures.mc(1, "Um"))));
        fails(() -> ImportValidator.validate(parse(r), ImportFixtures.pdf(), List.of()), HttpStatus.BAD_REQUEST, "VALIDATION");
    }

    @Test
    void cropPagesMustExist() {
        Map<String, Object> r = twoQuestions();
        @SuppressWarnings("unchecked")
        Map<String, Object> q = ((List<Map<String, Object>>) r.get("questions")).get(0);
        q.put("crop", List.of(Map.of("page", 3, "x0", 0.0, "top", 0.0, "x1", 1.0, "bottom", 1.0)));
        fails(() -> ImportValidator.validate(parse(r), ImportFixtures.pdf(), List.of()), HttpStatus.BAD_REQUEST, "VALIDATION");
    }

    @Test
    void everyFigurePartIsUsedExactlyOnce() {
        Map<String, Object> r = twoQuestions();
        @SuppressWarnings("unchecked")
        Map<String, Object> q = ((List<Map<String, Object>>) r.get("questions")).get(0);
        q.put("figures", List.of(Map.of("part", 1, "page", 1)));
        fails(() -> ImportValidator.validate(parse(r), ImportFixtures.pdf(), figures(AdminFixtures.png(40))),
                HttpStatus.BAD_REQUEST, "VALIDATION");

        q.put("figures", List.of());
        fails(() -> ImportValidator.validate(parse(r), ImportFixtures.pdf(), figures(AdminFixtures.png(40))),
                HttpStatus.BAD_REQUEST, "VALIDATION");
    }

    @Test
    void figuresAreSniffedAndSized() {
        Map<String, Object> r = twoQuestions();
        @SuppressWarnings("unchecked")
        Map<String, Object> q = ((List<Map<String, Object>>) r.get("questions")).get(0);
        q.put("figures", List.of(Map.of("part", 0, "page", 1)));
        fails(() -> ImportValidator.validate(parse(r), ImportFixtures.pdf(), figures(AdminFixtures.svg())),
                HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_IMAGE");
        fails(() -> ImportValidator.validate(parse(r), ImportFixtures.pdf(),
                        figures(AdminFixtures.png((int) ImportValidator.MAX_FIGURE_BYTES + 1))),
                HttpStatus.CONTENT_TOO_LARGE, "IMAGE_TOO_LARGE");
    }

    @Test
    void gabaritoAnswersMustBeTheTiposNormalisedValues() {
        Map<String, Object> ok = twoQuestions();
        ok.put("gabarito", ImportFixtures.gabarito(Map.of("1", "A", "2", "ANULADA")));
        assertThat(ImportValidator.validate(parse(ok), ImportFixtures.pdf(), List.of())).isNotNull();

        Map<String, Object> bad = twoQuestions();
        bad.put("gabarito", ImportFixtures.gabarito(Map.of("1", "C", "2", "CERTO")));
        fails(() -> ImportValidator.validate(parse(bad), ImportFixtures.pdf(), List.of()), HttpStatus.BAD_REQUEST, "VALIDATION");
    }

    @Test
    void rangesMustNotOverlap() {
        List<ImportJson.Range> overlapping = List.of(new ImportJson.Range(1, 10, null, "A"), new ImportJson.Range(10, 20, null, "B"));
        fails(() -> ImportValidator.validateRanges(overlapping), HttpStatus.BAD_REQUEST, "VALIDATION");
        ImportValidator.validateRanges(List.of(new ImportJson.Range(1, 10, null, "A"), new ImportJson.Range(11, 20, null, "B")));
    }

    @Test
    void chosenSectionsMustExistAndNotShareANumber() {
        ImportJson.Gabarito g = new ImportJson.Gabarito("g.pdf", List.of(
                new ImportJson.Section(0, "Básicos", null, Map.of("1", "A")),
                new ImportJson.Section(1, "Cargo 1", null, Map.of("2", "B")),
                new ImportJson.Section(2, "Cargo 2", null, Map.of("1", "C"))), List.of(), null);
        ImportValidator.validateSections(g, List.of(0, 1));
        fails(() -> ImportValidator.validateSections(g, List.of(0, 2)), HttpStatus.BAD_REQUEST, "VALIDATION");
        fails(() -> ImportValidator.validateSections(g, List.of(7)), HttpStatus.BAD_REQUEST, "VALIDATION");
        fails(() -> ImportValidator.validateSections(null, List.of(0)), HttpStatus.BAD_REQUEST, "VALIDATION");
        ImportValidator.validateSections(null, List.of());
    }
}
