package com.direitoria.questoes.repository;

import com.direitoria.questoes.domain.QuestionAsset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
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

    /** Current ordens of one questão, ascending. Cast: pgjdbc returns smallint as Integer anyway. */
    @Query(value = "SELECT CAST(ordem AS integer) FROM questao_asset WHERE source_id = :sourceId ORDER BY ordem",
            nativeQuery = true)
    List<Integer> ordensOf(@Param("sourceId") String sourceId);

    @Modifying
    @Query(value = "DELETE FROM questao_asset WHERE source_id = :sourceId AND ordem = :ordem", nativeQuery = true)
    void deleteOne(@Param("sourceId") String sourceId, @Param("ordem") int ordem);

    /**
     * Moves every asset of the questão to ordem -1 - ordem (0 → -1, 1 → -2 …).
     * uq_questao_asset_source_ordem is checked per statement, so renumbering in place
     * would collide; parked rows never can.
     */
    @Modifying
    @Query(value = "UPDATE questao_asset SET ordem = -1 - ordem WHERE source_id = :sourceId AND ordem >= 0",
            nativeQuery = true)
    void park(@Param("sourceId") String sourceId);

    @Modifying
    @Query(value = "UPDATE questao_asset SET ordem = :to WHERE source_id = :sourceId AND ordem = :parked",
            nativeQuery = true)
    void moveParked(@Param("sourceId") String sourceId, @Param("parked") int parked, @Param("to") int to);

    /** The one place bytes are written. The entity never maps them (see QuestionAsset). */
    @Modifying
    @Query(value = """
            INSERT INTO questao_asset (source_id, ordem, content_type, bytes, origem_url)
            VALUES (:sourceId, :ordem, :contentType, :bytes, :origemUrl)
            """, nativeQuery = true)
    void insert(
            @Param("sourceId") String sourceId,
            @Param("ordem") int ordem,
            @Param("contentType") String contentType,
            @Param("bytes") byte[] bytes,
            @Param("origemUrl") String origemUrl);
}
