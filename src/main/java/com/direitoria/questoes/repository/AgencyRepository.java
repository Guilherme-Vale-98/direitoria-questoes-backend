package com.direitoria.questoes.repository;

import com.direitoria.questoes.domain.Agency;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgencyRepository extends JpaRepository<Agency, Integer> {
    List<Agency> findAllByOrderByNomeAsc();
}
