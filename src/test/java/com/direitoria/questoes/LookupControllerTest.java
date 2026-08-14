package com.direitoria.questoes;

import com.direitoria.questoes.domain.Subject;
import com.direitoria.questoes.repository.SubjectRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
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
class LookupControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    SubjectRepository subjects;

    @Test
    void getSubjectsReturnsOrderedLookups() throws Exception {
        subjects.save(new Subject("Informática"));
        subjects.save(new Subject("Atualidades"));

        mockMvc.perform(get("/api/subjects"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Atualidades"))
                .andExpect(jsonPath("$[1].name").value("Informática"))
                .andExpect(jsonPath("$[0].id").isNumber());
    }
}
