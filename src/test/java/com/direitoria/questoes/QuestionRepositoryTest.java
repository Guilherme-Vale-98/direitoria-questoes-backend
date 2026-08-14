package com.direitoria.questoes;

import com.direitoria.questoes.domain.Agency;
import com.direitoria.questoes.domain.Difficulty;
import com.direitoria.questoes.domain.ExamBoard;
import com.direitoria.questoes.domain.Question;
import com.direitoria.questoes.domain.QuestionType;
import com.direitoria.questoes.domain.Subject;
import com.direitoria.questoes.repository.AgencyRepository;
import com.direitoria.questoes.repository.ExamBoardRepository;
import com.direitoria.questoes.repository.QuestionRepository;
import com.direitoria.questoes.repository.SubjectRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class QuestionRepositoryTest {

    @Autowired QuestionRepository questions;
    @Autowired SubjectRepository subjects;
    @Autowired ExamBoardRepository examBoards;
    @Autowired AgencyRepository agencies;
    @Autowired JdbcTemplate jdbc;

    @Test
    void readsQuestionWithEnumsRelationsAndOptions() {
        Subject s = subjects.save(new Subject("Direito Penal"));
        ExamBoard b = examBoards.save(new ExamBoard("FGV"));
        Agency a = agencies.save(new Agency("Polícia Federal"));
        UUID pubId = UUID.randomUUID();

        jdbc.update(
                "INSERT INTO questao (id, source_id, tipo, enunciado, opcao_a, opcao_b, gabarito,"
                        + " nivel, banca, orgao, cargo, ano, materia, assunto, banca_id, orgao_id)"
                        + " VALUES (?, ?, ?::tipo_questao, ?, ?, ?, ?, ?::nivel_questao, ?, ?, ?, ?, ?, ?, ?, ?)",
                pubId, "src-1", "MULTIPLA_ESCOLHA", "Enunciado?", "Opção A", "Opção B", "A",
                "MODERADO", "FGV", "Polícia Federal", "Analista", (short) 2024, "Direito Penal",
                "Crimes", b.getId(), a.getId());
        jdbc.update("INSERT INTO questao_disciplina (source_id, disciplina_id) VALUES (?, ?)",
                "src-1", s.getId());

        Optional<Question> found = questions.findByPublicId(pubId);
        assertTrue(found.isPresent());
        Question q = found.get();
        assertEquals(pubId, q.getPublicId());
        assertEquals(QuestionType.MULTIPLA_ESCOLHA, q.getTipo());
        assertEquals(Difficulty.MODERADO, q.getNivel());
        assertEquals("Opção A", q.getOpcaoA());
        assertEquals("A", q.getGabarito());
        assertEquals("FGV", q.getExamBoard().getNome());
        assertEquals("Polícia Federal", q.getAgency().getNome());
        assertNotNull(q.getSubjects());
        assertEquals(1, q.getSubjects().size());
        assertEquals("Direito Penal", q.getSubjects().get(0).getNome());
    }
}
