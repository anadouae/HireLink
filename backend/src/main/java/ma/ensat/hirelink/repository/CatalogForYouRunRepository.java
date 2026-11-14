package ma.ensat.hirelink.repository;

import ma.ensat.hirelink.model.CatalogForYouRun;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface CatalogForYouRunRepository extends JpaRepository<CatalogForYouRun, CatalogForYouRun.Pk> {

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from CatalogForYouRun r where r.runId = :runId")
    void deleteByRunId(@Param("runId") String runId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from CatalogForYouRun r where r.createdAt < :before")
    void deleteOlderThan(@Param("before") Instant before);

    @Query(value = """
            SELECT COUNT(*) FROM catalog_for_you_run r
            WHERE r.run_id = :runId AND r.match_score >= :minScore
            """, nativeQuery = true)
    long countEligible(@Param("runId") String runId, @Param("minScore") double minScore);

    @Query(value = """
            SELECT c.id FROM catalog_listings c
            INNER JOIN catalog_for_you_run r ON r.listing_id = c.id AND r.run_id = :runId
            WHERE r.match_score >= :minScore
            ORDER BY r.match_score DESC, c.sort_at DESC, c.id DESC
            LIMIT :limit OFFSET :offset
            """, nativeQuery = true)
    List<Long> findPageIds(
            @Param("runId") String runId,
            @Param("minScore") double minScore,
            @Param("limit") int limit,
            @Param("offset") int offset);

    @Query(value = """
            SELECT r.listing_id, r.match_score FROM catalog_for_you_run r
            WHERE r.run_id = :runId AND r.match_score >= :minScore
            """, nativeQuery = true)
    List<Object[]> scoresForRun(@Param("runId") String runId, @Param("minScore") double minScore);
}