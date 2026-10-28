package com.direitoria.questoes.admin;

import com.direitoria.questoes.TestcontainersConfiguration;
import com.direitoria.questoes.auth.JwtService;
import com.direitoria.questoes.domain.Agency;
import com.direitoria.questoes.domain.ExamBoard;
import com.direitoria.questoes.domain.Subject;
import com.direitoria.questoes.repository.AgencyRepository;
import com.direitoria.questoes.repository.ExamBoardRepository;
import com.direitoria.questoes.repository.SubjectRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AdminQuestionImagesTest {

    private static final int MB = 1024 * 1024;

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JwtService jwt;
    @Autowired JdbcTemplate jdbc;
    @Autowired SubjectRepository subjects;
    @Autowired ExamBoardRepository boards;
    @Autowired AgencyRepository agencies;

    Subject subject;
    ExamBoard board;
    Agency agency;

    @BeforeEach
    void canonicals() {
        subject = subjects.save(new Subject("Disciplina I"));
        board = boards.save(new ExamBoard("Banca I"));
        agency = agencies.save(new Agency("Órgão I"));
    }

    private Map<String, Object> mc(String statement, List<Map<String, Object>> images) {
        Map<String, Object> m = AdminFixtures.mc(statement, subject.getId(), board.getId(), agency.getId());
        m.put("images", images);
        return m;
    }

    private static List<Map<String, Object>> uploads(int... indexes) {
        List<Map<String, Object>> l = new ArrayList<>();
        for (int i : indexes) {
            l.add(Map.of("upload", i));
        }
        return l;
    }

    private ResultActions send(MockMultipartHttpServletRequestBuilder req, Map<String, Object> body,
                               MockMultipartFile... files) throws Exception {
        req.file(AdminFixtures.json(json, body));
        for (MockMultipartFile f : files) {
            req.file(f);
        }
        return mvc.perform(req.header("Authorization", AdminFixtures.bearer(jwt, "ADMIN")));
    }

    private ResultActions post(Map<String, Object> body, MockMultipartFile... files) throws Exception {
        return send(multipart("/api/admin/questions"), body, files);
    }

    private ResultActions put(UUID id, Map<String, Object> body, MockMultipartFile... files) throws Exception {
        return send(multipart(HttpMethod.PUT, "/api/admin/questions/{id}", id), body, files);
    }

    private UUID idOf(ResultActions r) throws Exception {
        return UUID.fromString(json.readTree(r.andReturn().getResponse().getContentAsString()).get("id").asText());
    }

    private byte[] stored(UUID id, int ordem) {
        return jdbc.queryForObject("SELECT a.bytes FROM questao_asset a JOIN questao q ON q.source_id = a.source_id"
                + " WHERE q.id = ? AND a.ordem = ?", byte[].class, id, (short) ordem);
    }

    private long assetCount(UUID id) {
        return jdbc.queryForObject("SELECT count(*) FROM questao_asset a JOIN questao q ON q.source_id = a.source_id"
                + " WHERE q.id = ?", Long.class, id);
    }

    private long manualRows() {
        return jdbc.queryForObject("SELECT count(*) FROM questao WHERE source_id LIKE 'manual:%'", Long.class);
    }

    @Test
    void createStoresImagesInTheOrderGiven() throws Exception {
        byte[] a = AdminFixtures.png(40);
        byte[] b = AdminFixtures.jpeg(50);
        byte[] c = AdminFixtures.webp(60);
        UUID id = idOf(post(mc("Enunciado I1", uploads(2, 0, 1)),
                AdminFixtures.image("a.png", "image/png", a),
                AdminFixtures.image("b.jpg", "image/jpeg", b),
                AdminFixtures.image("c.webp", "image/webp", c)).andExpect(status().isCreated()));

        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT a.ordem, a.content_type, a.origem_url FROM questao_asset a"
                        + " JOIN questao q ON q.source_id = a.source_id WHERE q.id = ? ORDER BY a.ordem", id);
        assertThat(rows).extracting(r -> r.get("content_type"))
                .containsExactly("image/webp", "image/png", "image/jpeg");
        assertThat(rows).extracting(r -> r.get("origem_url"))
                .containsExactly("upload:c.webp", "upload:a.png", "upload:b.jpg");
        assertThat(stored(id, 0)).isEqualTo(c);
        assertThat(stored(id, 1)).isEqualTo(a);

        mvc.perform(get("/api/admin/questions/{id}", id).header("Authorization", AdminFixtures.bearer(jwt, "ADMIN")))
                .andExpect(jsonPath("$.images.length()").value(3))
                .andExpect(jsonPath("$.images[1].url").value("/api/questions/" + id + "/assets/1"));
        mvc.perform(get("/api/questions/{id}/assets/1", id))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/png"))
                .andExpect(content().bytes(a));
    }

    @Test
    void theDeclaredTypeIsIgnoredSoAnSvgNamedPngIs415() throws Exception {
        long before = manualRows();
        post(mc("Enunciado I2", uploads(0)), AdminFixtures.image("inocente.png", "image/png", AdminFixtures.svg()))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_IMAGE"));
        assertThat(manualRows()).isEqualTo(before);
    }

    @Test
    void aJpegNamedPngIsStoredAsJpeg() throws Exception {
        UUID id = idOf(post(mc("Enunciado I3", uploads(0)),
                AdminFixtures.image("foto.png", "image/png", AdminFixtures.jpeg(64))).andExpect(status().isCreated()));
        assertThat(jdbc.queryForObject("SELECT a.content_type FROM questao_asset a JOIN questao q"
                + " ON q.source_id = a.source_id WHERE q.id = ?", String.class, id)).isEqualTo("image/jpeg");
    }

    @Test
    void anImageOver5MbIs413() throws Exception {
        long before = manualRows();
        post(mc("Enunciado I4", uploads(0)),
                AdminFixtures.image("grande.png", "image/png", AdminFixtures.png(5 * MB + 1)))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.code").value("IMAGE_TOO_LARGE"));
        assertThat(manualRows()).isEqualTo(before);
    }

    @Test
    void moreThan12MbInOneSaveIs413() throws Exception {
        int size = 4 * MB + MB / 2; // each under 5 MB, three of them over 12 MB
        post(mc("Enunciado I5", uploads(0, 1, 2)),
                AdminFixtures.image("1.png", "image/png", AdminFixtures.png(size)),
                AdminFixtures.image("2.png", "image/png", AdminFixtures.png(size + 1)),
                AdminFixtures.image("3.png", "image/png", AdminFixtures.png(size + 2)))
                .andExpect(status().isPayloadTooLarge());
    }

    @Test
    void moreThanFiveImagesIs400() throws Exception {
        List<MockMultipartFile> files = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            files.add(AdminFixtures.image(i + ".png", "image/png", AdminFixtures.png(20 + i)));
        }
        post(mc("Enunciado I6", uploads(0, 1, 2, 3, 4, 5)), files.toArray(MockMultipartFile[]::new))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.images").exists());
    }

    @Test
    void everyUploadedFileNeedsAPlaceInTheOrder() throws Exception {
        post(mc("Enunciado I7", List.of()), AdminFixtures.image("solta.png", "image/png", AdminFixtures.png(30)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.images").exists());
    }

    @Test
    void keepingAnImageOnCreateIs400() throws Exception {
        post(mc("Enunciado I8", List.of(Map.of("keep", 0))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.images").exists());
    }

    @Test
    void anEditKeepsReordersDeletesAndAdds() throws Exception {
        byte[] a = AdminFixtures.png(40);
        byte[] b = AdminFixtures.jpeg(50);
        byte[] c = AdminFixtures.webp(60);
        byte[] d = AdminFixtures.png(70);
        UUID id = idOf(post(mc("Enunciado I9", uploads(0, 1, 2)),
                AdminFixtures.image("a.png", "image/png", a),
                AdminFixtures.image("b.jpg", "image/jpeg", b),
                AdminFixtures.image("c.webp", "image/webp", c)).andExpect(status().isCreated()));

        // Final order: c (was 2), the new d, a (was 0). b (was 1) is dropped.
        put(id, mc("Enunciado I9", List.of(Map.of("keep", 2), Map.of("upload", 0), Map.of("keep", 0))),
                AdminFixtures.image("d.png", "image/png", d)).andExpect(status().isOk());

        assertThat(assetCount(id)).isEqualTo(3L);
        assertThat(stored(id, 0)).isEqualTo(c);
        assertThat(stored(id, 1)).isEqualTo(d);
        assertThat(stored(id, 2)).isEqualTo(a);
    }

    @Test
    void anEditWithoutAnImagesFieldLeavesThemAlone() throws Exception {
        UUID id = idOf(post(mc("Enunciado I10", uploads(0)),
                AdminFixtures.image("a.png", "image/png", AdminFixtures.png(40))).andExpect(status().isCreated()));
        Map<String, Object> body = mc("Enunciado I10 revisto", List.of());
        body.remove("images"); // plan ruling 6: absent ≠ empty

        put(id, body).andExpect(status().isOk());
        assertThat(assetCount(id)).isEqualTo(1L);
    }

    @Test
    void anEditWithAnEmptyOrderRemovesThemAll() throws Exception {
        UUID id = idOf(post(mc("Enunciado I11", uploads(0)),
                AdminFixtures.image("a.png", "image/png", AdminFixtures.png(40))).andExpect(status().isCreated()));
        put(id, mc("Enunciado I11", List.of())).andExpect(status().isOk());
        assertThat(assetCount(id)).isZero();
    }
}
