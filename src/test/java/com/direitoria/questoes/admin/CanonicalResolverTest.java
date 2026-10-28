package com.direitoria.questoes.admin;

import com.direitoria.questoes.TestcontainersConfiguration;
import com.direitoria.questoes.admin.dto.LookupRef;
import com.direitoria.questoes.domain.Agency;
import com.direitoria.questoes.domain.ExamBoard;
import com.direitoria.questoes.domain.Subject;
import com.direitoria.questoes.repository.AgencyRepository;
import com.direitoria.questoes.repository.ExamBoardRepository;
import com.direitoria.questoes.repository.SubjectRepository;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class CanonicalResolverTest {

    @Autowired CanonicalResolver resolver;
    @Autowired SubjectRepository subjects;
    @Autowired ExamBoardRepository boards;
    @Autowired AgencyRepository agencies;

    @SuppressWarnings("unchecked")
    private static Map<String, String> fieldErrors(Runnable call) {
        try {
            call.run();
        } catch (AdminApiException e) {
            assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
            return (Map<String, String>) e.getBody().get("fieldErrors");
        }
        throw new AssertionError("expected a validation error");
    }

    @Test
    void reusesAnExistingRowById() {
        ExamBoard fgv = boards.save(new ExamBoard("FGV R1"));
        assertThat(resolver.examBoard(new LookupRef(fgv.getId(), null)).getId()).isEqualTo(fgv.getId());
    }

    @Test
    void aNameMatchesIgnoringCaseAccentsAndRepeatedSpaces() {
        Agency tce = agencies.save(new Agency("Tribunal de Contas do Acre R2"));
        ExamBoard fgv = boards.save(new ExamBoard("Fundação Getúlio Vargas R2"));
        long before = agencies.count();

        assertThat(resolver.agency(new LookupRef(null, "  tribunal de  contas do ACRE r2 ")).getId())
                .isEqualTo(tce.getId());
        assertThat(resolver.examBoard(new LookupRef(null, "fundacao getulio vargas r2")).getId())
                .isEqualTo(fgv.getId());
        assertThat(agencies.count()).isEqualTo(before);
    }

    @Test
    void anUnknownNameIsInsertedTidied() {
        long before = agencies.count();
        Agency created = resolver.agency(new LookupRef(null, "  Órgão   Novo R3 "));
        assertThat(created.getId()).isNotNull();
        assertThat(created.getNome()).isEqualTo("Órgão Novo R3");
        assertThat(agencies.count()).isEqualTo(before + 1);
    }

    @Test
    void anUnknownIdIsAFieldError() {
        assertThat(fieldErrors(() -> resolver.agency(new LookupRef(-1, null)))).containsKey("agency");
        assertThat(fieldErrors(() -> resolver.subjects(List.of(new LookupRef(-1, null))))).containsKey("subjects");
    }

    @Test
    void aBlankNameIsAFieldError() {
        assertThat(fieldErrors(() -> resolver.examBoard(new LookupRef(null, "   ")))).containsKey("examBoard");
    }

    @Test
    void subjectsCollapseRepeatsAndEquivalentNames() {
        Subject penal = subjects.save(new Subject("Direito Penal R6"));
        List<Subject> resolved = resolver.subjects(List.of(
                new LookupRef(penal.getId(), null),
                new LookupRef(null, "direito penal r6"),
                new LookupRef(null, "Nova Disciplina R6"),
                new LookupRef(null, "nova disciplina r6")));
        assertThat(resolved).extracting(Subject::getNome)
                .containsExactly("Direito Penal R6", "Nova Disciplina R6");
    }
}
