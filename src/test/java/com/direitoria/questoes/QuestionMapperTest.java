package com.direitoria.questoes;

import com.direitoria.questoes.catalog.QuestionMapper;
import com.direitoria.questoes.domain.Agency;
import com.direitoria.questoes.domain.ExamBoard;
import com.direitoria.questoes.domain.Question;
import com.direitoria.questoes.domain.Subject;
import com.direitoria.questoes.dto.QuestionResponse;
import com.direitoria.questoes.repository.AgencyRepository;
import com.direitoria.questoes.repository.ExamBoardRepository;
import com.direitoria.questoes.repository.QuestionRepository;
import com.direitoria.questoes.repository.SubjectRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class QuestionMapperTest {

    @Autowired QuestionRepository questions;
    @Autowired SubjectRepository subjects;
    @Autowired ExamBoardRepository examBoards;
    @Autowired AgencyRepository agencies;
    @Autowired JdbcTemplate jdbc;

    @Test
    void mapsEntityToEnglishDto() {
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

        Question q = questions.findByPublicId(pubId).orElseThrow();
        QuestionResponse dto = QuestionMapper.toResponse(q);

        assertEquals(pubId, dto.id());
        assertEquals("Enunciado?", dto.statement());
        assertEquals("MULTIPLA_ESCOLHA", dto.type());
        assertEquals("MODERADO", dto.difficulty());
        assertEquals(2, dto.options().size());
        assertEquals("A", dto.options().get(0).label());
        assertEquals("Opção A", dto.options().get(0).text());
        assertEquals(1, dto.subjects().size());
        assertEquals("Direito Penal", dto.subjects().get(0).name());
        assertEquals("FGV", dto.examBoard().name());
        assertEquals("Polícia Federal", dto.agency().name());
        assertEquals("Analista", dto.role());
        assertEquals((short) 2024, dto.year());
    }

    @Test
    void leavesNivelNullAndOptionsEmptyForCertoErrado() {
        ExamBoard b = examBoards.save(new ExamBoard("CEBRASPE"));
        Agency a = agencies.save(new Agency("PF"));
        UUID pubId = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO questao (id, source_id, tipo, enunciado, gabarito,"
                        + " banca, orgao, cargo, ano, materia, assunto, banca_id, orgao_id)"
                        + " VALUES (?, ?, ?::tipo_questao, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                pubId, "src-2", "CERTO_ERRADO", "Julgue o item.", "CERTO",
                "CEBRASPE", "PF", "Agente", (short) 2022, "Direito", "X", b.getId(), a.getId());

        QuestionResponse dto = QuestionMapper.toResponse(questions.findByPublicId(pubId).orElseThrow());
        assertEquals("CERTO_ERRADO", dto.type());
        assertNull(dto.difficulty());
        assertEquals(0, dto.options().size());
    }
}
