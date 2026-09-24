package com.direitoria.questoes.repository;

import com.direitoria.questoes.domain.QuestionAsset;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface QuestionAssetRepository extends JpaRepository<QuestionAsset, Integer> {

    /**
     * The only place image bytes are loaded. Native because the entity deliberately
     * does not map the `bytes` column — see QuestionAsset.
     */
    @Query(
            value =
                    "SELECT a.bytes FROM questao_asset a "
                            + "JOIN questao q ON q.source_id = a.source_id "
                            + "WHERE q.id = :publicId AND a.ordem = :ordem",
            nativeQuery = true)
    Optional<byte[]> findBytes(@Param("publicId") UUID publicId, @Param("ordem") short ordem);

    @Query(
            value =
                    "SELECT a.content_type FROM questao_asset a "
                            + "JOIN questao q ON q.source_id = a.source_id "
                            + "WHERE q.id = :publicId AND a.ordem = :ordem",
            nativeQuery = true)
    Optional<String> findContentType(@Param("publicId") UUID publicId, @Param("ordem") short ordem);
}
