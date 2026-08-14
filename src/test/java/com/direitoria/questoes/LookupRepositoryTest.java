package com.direitoria.questoes;

import com.direitoria.questoes.domain.Subject;
import com.direitoria.questoes.repository.SubjectRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class LookupRepositoryTest {

    @Autowired
    SubjectRepository subjects;

    @Test
    void mapsDisciplinaAndOrdersByNome() {
        subjects.save(new Subject("Direito Penal"));
        subjects.save(new Subject("Direito Administrativo"));

        List<String> names = subjects.findAllByOrderByNomeAsc().stream().map(Subject::getNome).toList();

        assertEquals(List.of("Direito Administrativo", "Direito Penal"), names);
    }
}
