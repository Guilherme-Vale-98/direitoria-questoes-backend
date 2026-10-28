package com.direitoria.questoes.admin;

import com.direitoria.questoes.TestcontainersConfiguration;
import com.direitoria.questoes.auth.JwtService;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AdminImportCreateTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JwtService jwt;
    @Autowired JdbcTemplate jdbc;

    private String admin() {
        return AdminFixtures.bearer(jwt, "ADMIN");
    }

    private ResultActions create(Map<String, Object> result, byte[] pdf, MockMultipartFile... figures) throws Exception {
        MockMultipartHttpServletRequestBuilder req = multipart("/api/admin/imports")
                .file(ImportFixtures.pdfPart(pdf))
                .file(ImportFixtures.json(json, result));
        for (MockMultipartFile f : figures) {
            req.file(f);
        }
        return mvc.perform(req.header("Authorization", admin()));
    }

    private UUID idOf(ResultActions r) throws Exception {
        return UUID.fromString(json.readTree(r.andExpect(status().isCreated()).andReturn().getResponse()
                .getContentAsString()).get("id").asText());
    }

    private static List<Map<String, Object>> list(Map<String, Object>... qs) {
        return new ArrayList<>(List.of(qs));
    }

    private boolean included(UUID id, int n) {
        return jdbc.queryForObject("SELECT included FROM importacao_questao WHERE importacao_id = ? AND numero = ?",
                Boolean.class, id, n);
    }

    @Test
    void createStoresTheImportItsQuestoesAndFigures() throws Exception {
        Map<String, Object> q1 = ImportFixtures.mc(1, "Enunciado C1");
        q1.put("figures", List.of(Map.of("part", 0, "page", 2)));
        byte[] figure = AdminFixtures.png(64);
        UUID id = idOf(create(ImportFixtures.result("MULTIPLA_ESCOLHA", list(q1, ImportFixtures.mc(2, "Enunciado C2"))),
                ImportFixtures.pdf(), ImportFixtures.figure(figure)));

        Map<String, Object> row = jdbc.queryForMap("SELECT file_name, tipo::text AS tipo, page_count FROM importacao WHERE id = ?", id);
        assertThat(row).containsEntry("file_name", "prova.pdf").containsEntry("tipo", "MULTIPLA_ESCOLHA");
        assertThat(((Number) row.get("page_count")).intValue()).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT draft->>'statement' FROM importacao_questao WHERE importacao_id = ? AND numero = 1",
                String.class, id)).isEqualTo("Enunciado C1");
        assertThat(jdbc.queryForObject("SELECT draft->>'answer' FROM importacao_questao WHERE importacao_id = ? AND numero = 1",
                String.class, id)).isNull();
        Map<String, Object> img = jdbc.queryForMap(
                "SELECT ordem, content_type, bytes, origem FROM importacao_imagem WHERE importacao_id = ? AND numero = 1", id);
        assertThat(((Number) img.get("ordem")).intValue()).isZero();
        assertThat(img).containsEntry("content_type", "image/png").containsEntry("origem", "pdf:prova.pdf#p2");
        assertThat((byte[]) img.get("bytes")).isEqualTo(figure);
        assertThat(included(id, 1)).isTrue();
        assertThat(included(id, 2)).isTrue();
    }

    @Test
    void listSummarisesImportsNewestFirst() throws Exception {
        idOf(create(ImportFixtures.result("CERTO_ERRADO", list(ImportFixtures.ce(1, "Item L1"))), ImportFixtures.pdf()));
        mvc.perform(get("/api/admin/imports").header("Authorization", admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].fileName").value("prova.pdf"))
                .andExpect(jsonPath("$[0].type").value("CERTO_ERRADO"))
                .andExpect(jsonPath("$[0].found").value(1))
                .andExpect(jsonPath("$[0].included").value(1));
    }

    @Test
    void thePdfIsServedAsAnAttachment() throws Exception {
        byte[] pdf = ImportFixtures.pdf();
        UUID id = idOf(create(ImportFixtures.result("CERTO_ERRADO", list(ImportFixtures.ce(1, "Item P1"))), pdf));
        mvc.perform(get("/api/admin/imports/{id}/pdf", id).header("Authorization", admin()))
                .andExpect(status().isOk())
                .andExpect(content().bytes(pdf))
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.startsWith("attachment")));
    }

    @Test
    void discardDeletesEverythingAndLaterCallsAnswer404() throws Exception {
        Map<String, Object> q1 = ImportFixtures.mc(1, "Enunciado D1");
        q1.put("figures", List.of(Map.of("part", 0, "page", 1)));
        UUID id = idOf(create(ImportFixtures.result("MULTIPLA_ESCOLHA", list(q1)), ImportFixtures.pdf(),
                ImportFixtures.figure(AdminFixtures.png(40))));
        mvc.perform(delete("/api/admin/imports/{id}", id).header("Authorization", admin())).andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM importacao_questao WHERE importacao_id = ?", Long.class, id)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM importacao_imagem WHERE importacao_id = ?", Long.class, id)).isZero();
        mvc.perform(get("/api/admin/imports/{id}/pdf", id).header("Authorization", admin()))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("IMPORT_NOT_FOUND"));
        mvc.perform(delete("/api/admin/imports/{id}", id).header("Authorization", admin())).andExpect(status().isNotFound());
    }

    @Test
    void aFileThatIsNotAPdfIs415AndWritesNothing() throws Exception {
        long before = jdbc.queryForObject("SELECT count(*) FROM importacao", Long.class);
        create(ImportFixtures.result("CERTO_ERRADO", list(ImportFixtures.ce(1, "Item N1"))), "hello".getBytes())
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_PDF"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM importacao", Long.class)).isEqualTo(before);
    }

    @Test
    void anSvgFigureIs415() throws Exception {
        Map<String, Object> q1 = ImportFixtures.mc(1, "Enunciado S1");
        q1.put("figures", List.of(Map.of("part", 0, "page", 1)));
        create(ImportFixtures.result("MULTIPLA_ESCOLHA", list(q1)), ImportFixtures.pdf(),
                new MockMultipartFile("figuras", "fig.png", "image/png", AdminFixtures.svg()))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_IMAGE"));
    }

    @Test
    void aBadParseResultIs400() throws Exception {
        create(ImportFixtures.result("MULTIPLA_ESCOLHA", list(ImportFixtures.mc(1, "A"), ImportFixtures.mc(1, "B"))),
                ImportFixtures.pdf())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION"))
                .andExpect(jsonPath("$.fieldErrors.importacao").exists());
    }

    @Test
    void catalogDuplicatesAndRepeatsAreFlaggedAndStartExcluded() throws Exception {
        UUID existing = AdminFixtures.seedMc(jdbc, "manual:" + UUID.randomUUID(), "Enunciado Dup", "A",
                "Alternativa A de Enunciado Dup", "Alternativa B de Enunciado Dup", "Alternativa C de Enunciado Dup",
                "Alternativa D de Enunciado Dup", "Alternativa E de Enunciado Dup");
        UUID id = idOf(create(ImportFixtures.result("MULTIPLA_ESCOLHA", list(
                        ImportFixtures.mc(1, "Enunciado Dup"),
                        ImportFixtures.mc(2, "Enunciado R"),
                        ImportFixtures.mc(3, "Enunciado R"))), ImportFixtures.pdf()));

        assertThat(jdbc.queryForObject("SELECT duplicate_of_id FROM importacao_questao WHERE importacao_id = ? AND numero = 1",
                UUID.class, id)).isEqualTo(existing);
        assertThat(jdbc.queryForObject("SELECT duplicate_of_numero FROM importacao_questao WHERE importacao_id = ? AND numero = 3",
                Integer.class, id)).isEqualTo(2);
        assertThat(included(id, 1)).isFalse();
        assertThat(included(id, 2)).isTrue();
        assertThat(included(id, 3)).isFalse();
    }

    @Test
    void anuladasOfTheSuggestedSectionStartExcluded() throws Exception {
        Map<String, Object> r = ImportFixtures.result("MULTIPLA_ESCOLHA",
                list(ImportFixtures.mc(1, "Enunciado AN1"), ImportFixtures.mc(2, "Enunciado AN2")));
        r.put("gabarito", ImportFixtures.gabarito(Map.of("1", "A", "2", "ANULADA")));
        UUID id = idOf(create(r, ImportFixtures.pdf()));
        assertThat(included(id, 1)).isTrue();
        assertThat(included(id, 2)).isFalse();
        assertThat(jdbc.queryForObject("SELECT gabarito_sections::text FROM importacao WHERE id = ?", String.class, id))
                .isEqualTo("[0]");
    }

    @Test
    void onlyAdminsMayUseTheImportEndpoints() throws Exception {
        String user = AdminFixtures.bearer(jwt, "USER");
        UUID any = UUID.randomUUID();
        mvc.perform(get("/api/admin/imports").header("Authorization", user)).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/imports/{id}/pdf", any).header("Authorization", user)).andExpect(status().isForbidden());
        mvc.perform(delete("/api/admin/imports/{id}", any).header("Authorization", user)).andExpect(status().isForbidden());
        mvc.perform(multipart("/api/admin/imports").file(ImportFixtures.pdfPart(ImportFixtures.pdf()))
                .header("Authorization", user)).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/imports")).andExpect(status().isUnauthorized());
    }
}
