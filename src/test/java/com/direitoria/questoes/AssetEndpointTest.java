package com.direitoria.questoes;

import com.direitoria.questoes.repository.QuestionRepository;
import jakarta.persistence.EntityManager;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AssetEndpointTest {

    @Autowired MockMvc mockMvc;
    @Autowired EntityManager em;
    @Autowired QuestionRepository questions;

    private UUID seedWithAsset(String sourceId) {
        em.createNativeQuery(
                        "INSERT INTO questao (source_id, tipo, enunciado, gabarito, banca, orgao, cargo, materia, assunto) "
                                + "VALUES (?1, 'CERTO_ERRADO', 'Julgue o item.', 'CERTO', 'B', 'O', 'C', 'M', 'A')")
                .setParameter(1, sourceId)
                .executeUpdate();
        em.createNativeQuery(
                        "INSERT INTO questao_asset (source_id, ordem, content_type, bytes, origem_url) "
                                + "VALUES (?1, 0, 'image/png', decode('89504e470d0a1a0a','hex'), 'https://exemplo/a.png')")
                .setParameter(1, sourceId)
                .executeUpdate();
        em.flush();
        em.clear();
        return questions.findById(sourceId).orElseThrow().getPublicId();
    }

    @Test
    void servesTheStoredBytesWithTheStoredContentType() throws Exception {
        UUID id = seedWithAsset("test:asset-1");
        byte[] expected = new byte[] {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a};

        byte[] body = mockMvc.perform(get("/api/questions/" + id + "/assets/0"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/png"))
                .andReturn()
                .getResponse()
                .getContentAsByteArray();

        assertArrayEquals(expected, body);
    }

    @Test
    void returns404ForAnUnknownOrdem() throws Exception {
        UUID id = seedWithAsset("test:asset-2");
        mockMvc.perform(get("/api/questions/" + id + "/assets/7")).andExpect(status().isNotFound());
    }

    @Test
    void returns404ForAnUnknownQuestion() throws Exception {
        mockMvc.perform(get("/api/questions/" + UUID.randomUUID() + "/assets/0"))
                .andExpect(status().isNotFound());
    }
}
