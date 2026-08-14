package com.direitoria.questoes.repository;

import com.direitoria.questoes.domain.ExamBoard;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExamBoardRepository extends JpaRepository<ExamBoard, Integer> {
    List<ExamBoard> findAllByOrderByNomeAsc();
}
