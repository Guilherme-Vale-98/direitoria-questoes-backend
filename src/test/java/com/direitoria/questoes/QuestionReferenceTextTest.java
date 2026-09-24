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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class QuestionReferenceTextTest {

    @Autowired MockMvc mockMvc;
    @Autowired EntityManager em;
    @Autowired QuestionRepository questions;

    private UUID seed(String sourceId, String textoRef, boolean withAsset) {
        return seed(sourceId, "Julgue o item.", textoRef, withAsset);
    }

    private UUID seed(String sourceId, String enunciado, String textoRef, boolean withAsset) {
        em.createNativeQuery(
                        "INSERT INTO questao (source_id, tipo, enunciado, gabarito, banca, orgao, cargo, materia, assunto, texto_ref) "
                                + "VALUES (?1, 'CERTO_ERRADO', ?2, 'CERTO', 'B', 'O', 'C', 'M', 'A', ?3)")
                .setParameter(1, sourceId)
                .setParameter(2, enunciado)
                .setParameter(3, textoRef)
                .executeUpdate();
        if (withAsset) {
            em.createNativeQuery(
                            "INSERT INTO questao_asset (source_id, ordem, content_type, bytes, origem_url) "
                                    + "VALUES (?1, 0, 'image/png', decode('89504e47','hex'), 'https://exemplo/a.png')")
                    .setParameter(1, sourceId)
                    .executeUpdate();
        }
        em.flush();
        em.clear();
        return questions.findById(sourceId).orElseThrow().getPublicId();
    }

    @Test
    void detailExposesReferenceText() throws Exception {
        UUID id = seed("test:ref-1", "A discussão sobre povos e comunidades tradicionais afeta o debate.", false);

        mockMvc.perform(get("/api/questions/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.referenceText").value(
                        "A discussão sobre povos e comunidades tradicionais afeta o debate."))
                .andExpect(jsonPath("$.assets").isEmpty());
    }

    @Test
    void referenceTextIsNullWhenAbsent() throws Exception {
        UUID id = seed("test:ref-2", null, false);

        mockMvc.perform(get("/api/questions/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.referenceText").doesNotExist());
    }

    @Test
    void assetsPointAtOurOwnEndpointNotTheThirdPartyCdn() throws Exception {
        UUID id = seed("test:ref-3", null, true);

        mockMvc.perform(get("/api/questions/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assets[0].ordem").value(0))
                .andExpect(jsonPath("$.assets[0].contentType").value("image/png"))
                .andExpect(jsonPath("$.assets[0].url").value("/api/questions/" + id + "/assets/0"));
    }

    @Test
    void referenceTextIsSuppressedWhenIdenticalToEnunciado() throws Exception {
        String enunciado = "O acesso à informação pública é um direito fundamental assegurado pela Constituição.";
        UUID exact = seed("test:ref-4", enunciado, enunciado, false);

        mockMvc.perform(get("/api/questions/" + exact))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.referenceText").doesNotExist());

        // Whitespace and case differences must still count as duplication: the
        // repetition students see is rarely byte-exact. Enunciado text differs
        // (distinct content_key) but still contains the normalized texto_ref.
        String enunciadoVariant = enunciado + " Julgue o item conforme o texto.";
        String textoRefVariant =
                "  O ACESSO à informação   pública é um direito fundamental assegurado pela constituição.  ";
        UUID variant = seed("test:ref-4b", enunciadoVariant, textoRefVariant, false);

        mockMvc.perform(get("/api/questions/" + variant))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.referenceText").doesNotExist());
    }

    @Test
    void referenceTextIsSuppressedWhenAlreadyQuotedInlineInEnunciado() throws Exception {
        String textoRef = "Se Jair é culpado, é correto inferir que João é inocente.";
        String enunciado = "O texto \"" + textoRef + "\" permite concluir que…";
        UUID id = seed("test:ref-5", enunciado, textoRef, false);

        mockMvc.perform(get("/api/questions/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.referenceText").doesNotExist());
    }

    @Test
    void referenceTextIsKeptWhenGenuinelyDistinctFromEnunciado() throws Exception {
        String enunciado = "Com base no texto acima, julgue o item a seguir.";
        String textoRef =
                "A discussão sobre povos e comunidades tradicionais afeta o debate público contemporâneo.";
        UUID id = seed("test:ref-6", enunciado, textoRef, false);

        mockMvc.perform(get("/api/questions/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.referenceText").value(textoRef));
    }
}
