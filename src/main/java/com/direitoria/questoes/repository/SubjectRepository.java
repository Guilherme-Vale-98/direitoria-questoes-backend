package com.direitoria.questoes.repository;

import com.direitoria.questoes.domain.Subject;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubjectRepository extends JpaRepository<Subject, Integer> {
    List<Subject> findAllByOrderByNomeAsc();
}
