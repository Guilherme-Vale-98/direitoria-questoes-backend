package com.direitoria.questoes.repository;

import com.direitoria.questoes.domain.Question;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface QuestionRepository
        extends JpaRepository<Question, String>, JpaSpecificationExecutor<Question> {

    Optional<Question> findByPublicId(UUID publicId);
}
