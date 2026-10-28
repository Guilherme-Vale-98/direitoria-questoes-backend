package com.direitoria.questoes.repository;

import com.direitoria.questoes.domain.Question;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface QuestionRepository
        extends JpaRepository<Question, String>, JpaSpecificationExecutor<Question> {

    Optional<Question> findByPublicId(UUID publicId);

    /**
     * The duplicate pre-check: another questão with the same content_key, computed by
     * the database's own q_content_key (enunciado + alternativas, whitespace- and
     * case-normalised). Runs BEFORE any write — once an insert violates
     * uq_questao_content_key, Postgres aborts the transaction and this lookup could no
     * longer run. `self` excludes the questão being edited ("" on create).
     */
    @Query(value = """
            SELECT id FROM questao
            WHERE content_key = q_content_key(CAST(:statement AS text), CAST(:a AS text), CAST(:b AS text),
                                              CAST(:c AS text), CAST(:d AS text), CAST(:e AS text))
              AND source_id <> :self
            LIMIT 1
            """, nativeQuery = true)
    Optional<UUID> findDuplicate(
            @Param("statement") String statement,
            @Param("a") String a,
            @Param("b") String b,
            @Param("c") String c,
            @Param("d") String d,
            @Param("e") String e,
            @Param("self") String self);

    /**
     * Holds the questão's row until the delete commits. A concurrent tentativa insert
     * needs a share lock on this row for its foreign-key check, so it waits — the count
     * taken after this lock is the count the delete erases.
     */
    @Query(value = "SELECT source_id FROM questao WHERE source_id = :sourceId FOR UPDATE", nativeQuery = true)
    Optional<String> lockForDelete(@Param("sourceId") String sourceId);

    /**
     * Hard delete (docs/adr/0005). ON DELETE CASCADE removes the questão's tentativas,
     * disciplina links and images. Native on purpose: removing the entity through JPA
     * would try to null questao_asset.source_id (NOT NULL) first.
     */
    @Modifying
    @Query(value = "DELETE FROM questao WHERE source_id = :sourceId", nativeQuery = true)
    int hardDelete(@Param("sourceId") String sourceId);
}
