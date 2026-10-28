package com.direitoria.questoes.admin;

import com.direitoria.questoes.TestcontainersConfiguration;
import com.direitoria.questoes.domain.Agency;
import com.direitoria.questoes.domain.ExamBoard;
import com.direitoria.questoes.domain.Question;
import com.direitoria.questoes.domain.QuestionContent;
import com.direitoria.questoes.domain.QuestionType;
import com.direitoria.questoes.domain.Subject;
import com.direitoria.questoes.repository.AgencyRepository;
import com.direitoria.questoes.repository.ExamBoardRepository;
import com.direitoria.questoes.repository.SubjectRepository;
import jakarta.persistence.EntityManager;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class ContentKeyViolationTest {

    @Autowired EntityManager em;
    @Autowired SubjectRepository subjects;
    @Autowired ExamBoardRepository boards;
    @Autowired AgencyRepository agencies;

    @Test
    void recognisesTheViolationRaisedWhenARaceSlipsPastThePreCheck() {
        Subject s = subjects.save(new Subject("Disciplina CKV"));
        ExamBoard b = boards.save(new ExamBoard("Banca CKV"));
        Agency a = agencies.save(new Agency("Órgão CKV"));
        em.persist(Question.createManual(content("Mesmo enunciado CKV", s, b, a)));
        em.flush();

        em.persist(Question.createManual(content("  mesmo   ENUNCIADO ckv ", s, b, a)));
        Throwable t = catchThrowable(() -> em.flush());

        assertThat(t).isNotNull();
        assertThat(ContentKeyViolation.matches(t)).isTrue();
    }

    @Test
    void ignoresUnrelatedFailures() {
        assertThat(ContentKeyViolation.matches(new IllegalStateException("outra coisa"))).isFalse();
    }

    private static QuestionContent content(String statement, Subject s, ExamBoard b, Agency a) {
        return new QuestionContent(QuestionType.CERTO_ERRADO, null, statement, null, null, null, null, null,
                "CERTO", null, List.of(s), b, a, (short) 2024, "", null);
    }
}
