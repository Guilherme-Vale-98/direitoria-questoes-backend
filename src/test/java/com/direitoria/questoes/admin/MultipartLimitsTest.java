package com.direitoria.questoes.admin;

import com.direitoria.questoes.TestcontainersConfiguration;
import com.direitoria.questoes.auth.JwtService;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MultipartLimitsTest {

    @LocalServerPort int port;
    @Autowired JwtService jwt;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;

    private final HttpClient http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
    private final String tag = UUID.randomUUID().toString().substring(0, 8);
    private final List<UUID> created = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        for (UUID id : created) {
            jdbc.update("DELETE FROM questao WHERE id = ?", id); // cascades links, assets, tentativas
        }
        jdbc.update("DELETE FROM disciplina WHERE nome = ?", "Disciplina Tomcat " + tag);
        jdbc.update("DELETE FROM banca WHERE nome = ?", "Banca Tomcat " + tag);
        jdbc.update("DELETE FROM orgao WHERE nome = ?", "Órgão Tomcat " + tag);
    }

    private Map<String, Object> body(String statement, List<Map<String, Object>> images) {
        Map<String, Object> m = AdminFixtures.mc(statement, 0, 0, 0);
        m.put("subjects", List.of(AdminFixtures.named("Disciplina Tomcat " + tag)));
        m.put("examBoard", AdminFixtures.named("Banca Tomcat " + tag));
        m.put("agency", AdminFixtures.named("Órgão Tomcat " + tag));
        m.put("images", images);
        return m;
    }

    private HttpResponse<String> send(String method, String path, Map<String, Object> questao, byte[] image)
            throws Exception {
        String boundary = "----3b" + UUID.randomUUID();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"questao\"\r\n"
                + "Content-Type: application/json\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(json.writeValueAsBytes(questao));
        out.write("\r\n".getBytes(StandardCharsets.UTF_8));
        if (image != null) {
            out.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"imagens\"; filename=\"figura.png\"\r\n"
                    + "Content-Type: image/png\r\n\r\n").getBytes(StandardCharsets.UTF_8));
            out.write(image);
            out.write("\r\n".getBytes(StandardCharsets.UTF_8));
        }
        out.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        HttpRequest req = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Authorization", AdminFixtures.bearer(jwt, "ADMIN"))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .method(method, HttpRequest.BodyPublishers.ofByteArray(out.toByteArray()))
                .build();
        return http.send(req, HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void realTomcatParsesAMultipartPostAndPut() throws Exception {
        // 2 MB: above Spring's own 1 MB default max-file-size, below the configured 5 MB,
        // so this only passes because of the property — not despite it being absent.
        HttpResponse<String> posted = send("POST", "/api/admin/questions",
                body("Enunciado Tomcat " + tag, List.of(Map.of("upload", 0))), AdminFixtures.png(2 * 1024 * 1024));
        assertThat(posted.statusCode()).as(posted.body()).isEqualTo(201);
        UUID id = UUID.fromString(json.readTree(posted.body()).get("id").asText());
        created.add(id);

        HttpResponse<String> put = send("PUT", "/api/admin/questions/" + id,
                body("Enunciado Tomcat " + tag + " revisto", List.of(Map.of("keep", 0))), null);
        assertThat(put.statusCode()).as(put.body()).isEqualTo(200);
        assertThat(jdbc.queryForObject("SELECT enunciado FROM questao WHERE id = ?", String.class, id))
                .endsWith("revisto");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM questao_asset a JOIN questao q"
                + " ON q.source_id = a.source_id WHERE q.id = ?", Long.class, id)).isEqualTo(1L);
    }

    @Test
    void anOversizeImageGetsAClean413NotAConnectionReset() throws Exception {
        // 12 MB: under the 20 MB max-file-size, so Tomcat accepts it and QuestionImages
        // refuses it in code (5 MB per image) — still a clean 413 IMAGE_TOO_LARGE.
        HttpResponse<String> res = send("POST", "/api/admin/questions",
                body("Enunciado grande " + tag, List.of(Map.of("upload", 0))), AdminFixtures.png(12 * 1024 * 1024));
        assertThat(res.statusCode()).isEqualTo(413);
        assertThat(res.body()).contains("IMAGE_TOO_LARGE");
    }

    private HttpResponse<String> sendImport(byte[] pdf) throws Exception {
        String boundary = "----3c" + UUID.randomUUID();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"importacao\"\r\n"
                + "Content-Type: application/json\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(json.writeValueAsBytes(ImportFixtures.result("CERTO_ERRADO",
                new ArrayList<>(List.of(ImportFixtures.ce(1, "Item Tomcat " + tag))))));
        out.write(("\r\n--" + boundary + "\r\nContent-Disposition: form-data; name=\"pdf\"; filename=\"p.pdf\"\r\n"
                + "Content-Type: application/pdf\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(pdf);
        out.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        HttpRequest req = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/admin/imports"))
                .header("Authorization", AdminFixtures.bearer(jwt, "ADMIN"))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(out.toByteArray()))
                .build();
        return http.send(req, HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void anOversizePdfGetsAClean413WithTheImportCopy() throws Exception {
        // 21 MB: over the 20 MB max-file-size, under the 30 MB max-request-size; the
        // 31 MB max-swallow-size lets Tomcat answer instead of resetting the connection.
        byte[] pdf = new byte[21 * 1024 * 1024];
        System.arraycopy(ImportFixtures.pdf(), 0, pdf, 0, 5);
        HttpResponse<String> res = sendImport(pdf);
        assertThat(res.statusCode()).isEqualTo(413);
        assertThat(res.body()).contains("PDF_TOO_LARGE");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM importacao_questao q JOIN importacao i ON i.id = q.importacao_id"
                + " WHERE q.draft->>'statement' = ?", Long.class, "Item Tomcat " + tag)).isZero();
    }
}
