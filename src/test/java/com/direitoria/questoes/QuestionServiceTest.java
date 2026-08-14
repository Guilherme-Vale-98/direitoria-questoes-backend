package com.direitoria.questoes;

import com.direitoria.questoes.catalog.QuestionService;
import com.direitoria.questoes.domain.Difficulty;
import com.direitoria.questoes.domain.ExamBoard;
import com.direitoria.questoes.domain.QuestionType;
import com.direitoria.questoes.domain.Subject;
import com.direitoria.questoes.dto.QuestionResponse;
import com.direitoria.questoes.repository.ExamBoardRepository;
import com.direitoria.questoes.repository.SubjectRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class QuestionServiceTest {

    @Autowired QuestionService service;
    @Autowired SubjectRepository subjects;
    @Autowired ExamBoardRepository examBoards;
    @Autowired JdbcTemplate jdbc;

    private void insertQuestion(String src, String tipo, String banca, Integer bancaId, Short year) {
        jdbc.update(
                "INSERT INTO questao (id, source_id, tipo, enunciado, gabarito, banca, orgao, cargo,"
                        + " ano, materia, assunto, banca_id) VALUES (?, ?, ?::tipo_questao, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(), src, tipo, "Enunciado " + src, "A", banca, "Org", "Cargo", year, "M", "A", bancaId);
    }

    @Test
    void filtersByExamBoardAndType() {
        ExamBoard fgv = examBoards.save(new ExamBoard("FGV"));
        ExamBoard ceb = examBoards.save(new ExamBoard("CEBRASPE"));
        insertQuestion("q1", "MULTIPLA_ESCOLHA", "FGV", fgv.getId(), (short) 2024);
        insertQuestion("q2", "CERTO_ERRADO", "FGV", fgv.getId(), (short) 2024);
        insertQuestion("q3", "MULTIPLA_ESCOLHA", "CEBRASPE", ceb.getId(), (short) 2024);

        Page<QuestionResponse> onlyFgvMc = service.search(
                null, fgv.getId(), null, null, QuestionType.MULTIPLA_ESCOLHA, null, null,
                PageRequest.of(0, 20));
        assertEquals(1, onlyFgvMc.getTotalElements());
        assertEquals("FGV", onlyFgvMc.getContent().get(0).examBoard().name());
        assertEquals("MULTIPLA_ESCOLHA", onlyFgvMc.getContent().get(0).type());
    }

    @Test
    void filtersBySubject() {
        Subject penal = subjects.save(new Subject("Direito Penal"));
        ExamBoard fgv = examBoards.save(new ExamBoard("FGV"));
        insertQuestion("q1", "MULTIPLA_ESCOLHA", "FGV", fgv.getId(), (short) 2024);
        insertQuestion("q2", "MULTIPLA_ESCOLHA", "FGV", fgv.getId(), (short) 2024);
        jdbc.update("INSERT INTO questao_disciplina (source_id, disciplina_id) VALUES (?, ?)",
                "q1", penal.getId());

        Page<QuestionResponse> byPenal = service.search(
                penal.getId(), null, null, null, null, null, null, PageRequest.of(0, 20));
        assertEquals(1, byPenal.getTotalElements());
    }

    @Test
    void subjectFilterDoesNotDuplicateMultiSubjectQuestion() {
        // A question linked to TWO subjects must appear ONCE when filtered by one of them
        // (the join sets distinct(true); the count query must agree).
        Subject penal = subjects.save(new Subject("Direito Penal"));
        Subject processual = subjects.save(new Subject("Direito Processual Penal"));
        ExamBoard fgv = examBoards.save(new ExamBoard("FGV"));
        insertQuestion("q1", "MULTIPLA_ESCOLHA", "FGV", fgv.getId(), (short) 2024);
        jdbc.update("INSERT INTO questao_disciplina (source_id, disciplina_id) VALUES (?, ?), (?, ?)",
                "q1", penal.getId(), "q1", processual.getId());

        Page<QuestionResponse> byPenal = service.search(
                penal.getId(), null, null, null, null, null, null, PageRequest.of(0, 20));
        assertEquals(1, byPenal.getTotalElements());
        assertEquals(1, byPenal.getContent().size());
    }

    @Test
    void noFiltersReturnsAll() {
        ExamBoard fgv = examBoards.save(new ExamBoard("FGV"));
        insertQuestion("q1", "MULTIPLA_ESCOLHA", "FGV", fgv.getId(), (short) 2024);
        insertQuestion("q2", "CERTO_ERRADO", "FGV", fgv.getId(), (short) 2021);

        Page<QuestionResponse> all = service.search(
                null, null, null, null, null, null, null, PageRequest.of(0, 20));
        assertEquals(2, all.getTotalElements());
    }

    @Test
    void filtersBySearchTermInStatementCaseInsensitive() {
        ExamBoard fgv = examBoards.save(new ExamBoard("FGV"));
        jdbc.update(
                "INSERT INTO questao (id, source_id, tipo, enunciado, gabarito, banca, orgao, cargo,"
                        + " ano, materia, assunto, banca_id) VALUES (?, ?, ?::tipo_questao, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(), "s1", "MULTIPLA_ESCOLHA", "Sobre o princípio da LEGALIDADE penal",
                "A", "FGV", "Org", "Cargo", (short) 2024, "M", "A", fgv.getId());
        insertQuestion("s2", "MULTIPLA_ESCOLHA", "FGV", fgv.getId(), (short) 2024);

        Page<QuestionResponse> hit = service.search(
                null, null, null, null, null, null, "legalidade", PageRequest.of(0, 20));
        assertEquals(1, hit.getTotalElements());
        assertEquals("Sobre o princípio da LEGALIDADE penal", hit.getContent().get(0).statement());
    }
}
