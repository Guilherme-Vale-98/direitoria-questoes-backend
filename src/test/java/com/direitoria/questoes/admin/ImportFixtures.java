package com.direitoria.questoes.admin;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import tools.jackson.databind.ObjectMapper;

/** Parse results and multipart parts for the import tests (Bloco 3C). */
final class ImportFixtures {

    private ImportFixtures() {
    }

    /** Starts with the PDF magic bytes; Spring never parses further. */
    static byte[] pdf() {
        return "%PDF-1.7\n% fixture\n".getBytes(StandardCharsets.US_ASCII);
    }

    /** A múltipla escolha questão A–E; alternative texts derive from the statement, so statements never collide. */
    static Map<String, Object> mc(int number, String statement) {
        Map<String, Object> options = new LinkedHashMap<>();
        for (String l : List.of("A", "B", "C", "D", "E")) {
            options.put(l, "Alternativa " + l + " de " + statement);
        }
        Map<String, Object> q = new LinkedHashMap<>();
        q.put("number", number);
        q.put("referenceText", null);
        q.put("statement", statement);
        q.put("options", options);
        q.put("crop", List.of(Map.of("page", 1, "x0", 0.0, "top", 10.0, "x1", 297.5, "bottom", 100.0)));
        q.put("figures", new ArrayList<>());
        q.put("flags", new ArrayList<>());
        return q;
    }

    static Map<String, Object> ce(int number, String statement) {
        Map<String, Object> q = mc(number, statement);
        q.put("options", null);
        return q;
    }

    static Map<String, Object> result(String type, List<Map<String, Object>> questions) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("fileName", "prova.pdf");
        r.put("type", type);
        r.put("pageCount", 2);
        r.put("gabarito", null);
        r.put("subjectRanges", new ArrayList<>());
        r.put("questions", questions);
        return r;
    }

    /** One section holding `answers`, suggested. */
    static Map<String, Object> gabarito(Map<String, String> answers) {
        Map<String, Object> section = new LinkedHashMap<>();
        section.put("key", 0);
        section.put("title", "Cargo 1: ANALISTA");
        section.put("code", null);
        section.put("answers", answers);
        Map<String, Object> g = new LinkedHashMap<>();
        g.put("fileName", "gabarito.pdf");
        g.put("sections", List.of(section));
        g.put("candidates", List.of(Map.of("keys", List.of(0), "score", 10)));
        g.put("suggested", 0);
        return g;
    }

    static MockMultipartFile pdfPart(byte[] bytes) {
        return new MockMultipartFile("pdf", "prova.pdf", "application/pdf", bytes);
    }

    static MockMultipartFile json(ObjectMapper mapper, Map<String, Object> body) {
        return new MockMultipartFile("importacao", "", MediaType.APPLICATION_JSON_VALUE, mapper.writeValueAsBytes(body));
    }

    static MockMultipartFile figure(byte[] bytes) {
        return new MockMultipartFile("figuras", "fig.png", "image/png", bytes);
    }
}
