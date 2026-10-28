package com.direitoria.questoes.admin;

import com.direitoria.questoes.admin.dto.LookupRef;
import com.direitoria.questoes.domain.Agency;
import com.direitoria.questoes.domain.ExamBoard;
import com.direitoria.questoes.domain.NamedLookupEntity;
import com.direitoria.questoes.domain.Subject;
import com.direitoria.questoes.repository.AgencyRepository;
import com.direitoria.questoes.repository.ExamBoardRepository;
import com.direitoria.questoes.repository.SubjectRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Component;

/**
 * Turns the form's {id} / {name} picks into canonical rows. A typed name reuses an
 * existing row when it matches ignoring case, accents and repeated whitespace;
 * otherwise a row is inserted — inside the caller's transaction, so a later failure
 * rolls it back. The tables are small (≈ 100–220 rows), so matching scans them.
 */
@Component
public class CanonicalResolver {

    private final SubjectRepository subjects;
    private final ExamBoardRepository examBoards;
    private final AgencyRepository agencies;

    public CanonicalResolver(SubjectRepository subjects, ExamBoardRepository examBoards, AgencyRepository agencies) {
        this.subjects = subjects;
        this.examBoards = examBoards;
        this.agencies = agencies;
    }

    public ExamBoard examBoard(LookupRef ref) {
        return resolve(ref, examBoards, ExamBoard::new, "examBoard", "Informe a banca.", "Banca não encontrada.");
    }

    public Agency agency(LookupRef ref) {
        return resolve(ref, agencies, Agency::new, "agency", "Informe o órgão.", "Órgão não encontrado.");
    }

    /**
     * Resolves every pick and drops repeats: the same disciplina chosen twice, or a new
     * name that matches an existing one, must become ONE questao_disciplina row — the
     * table's primary key would reject the second.
     */
    public List<Subject> subjects(List<LookupRef> refs) {
        Map<Integer, Subject> byId = new LinkedHashMap<>();
        for (LookupRef ref : refs) {
            Subject s = resolve(ref, subjects, Subject::new, "subjects", "Disciplina inválida.", "Disciplina não encontrada.");
            byId.putIfAbsent(s.getId(), s);
        }
        return List.copyOf(byId.values());
    }

    private static <T extends NamedLookupEntity> T resolve(
            LookupRef ref, JpaRepository<T, Integer> repo, Function<String, T> create,
            String field, String missing, String notFound) {
        if (ref == null || (ref.id() == null && (ref.name() == null || ref.name().isBlank()))) {
            throw AdminApiException.validation(Map.of(field, missing));
        }
        if (ref.id() != null) {
            return repo.findById(ref.id())
                    .orElseThrow(() -> AdminApiException.validation(Map.of(field, notFound)));
        }
        String key = CanonicalNames.key(ref.name());
        return repo.findAll().stream()
                .filter(e -> CanonicalNames.key(e.getNome()).equals(key))
                .findFirst()
                .orElseGet(() -> repo.save(create.apply(CanonicalNames.display(ref.name()))));
    }
}
