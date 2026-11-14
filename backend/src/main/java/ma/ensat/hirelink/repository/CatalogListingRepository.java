package ma.ensat.hirelink.repository;

import ma.ensat.hirelink.model.CatalogListing;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CatalogListingRepository extends JpaRepository<CatalogListing, Long>, JpaSpecificationExecutor<CatalogListing> {
    Optional<CatalogListing> findByOriginAndRefId(String origin, Long refId);
    Optional<CatalogListing> findByDedupeKey(String dedupeKey);
    List<CatalogListing> findByIdIn(Collection<Long> ids);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from CatalogListing c")
    void deleteAllListings();

    @Query(value = """
            SELECT COUNT(*) FROM catalog_listings c
            WHERE (:q IS NULL OR LOWER(c.search_blob) LIKE CONCAT('%', LOWER(:q), '%'))
              AND (:country IS NULL OR c.country_code = :country)
              AND (:city IS NULL OR c.city_fold = :city OR LOWER(c.city) = LOWER(:city))
              AND (:contract IS NULL OR c.contract_label = :contract)
              AND (:workMode IS NULL OR c.work_mode = :workMode)
              AND (:source IS NULL OR c.source_code = :source)
              AND (:origin IS NULL OR c.origin = :origin)
              AND (:since IS NULL OR (c.published_trusted = 1 AND c.published_at IS NOT NULL AND c.published_at >= :since))
            """, nativeQuery = true)
    long countFiltered(
            @Param("q") String q,
            @Param("country") String country,
            @Param("city") String city,
            @Param("contract") String contract,
            @Param("workMode") String workMode,
            @Param("source") String source,
            @Param("origin") String origin,
            @Param("since") java.time.Instant since);

    @Query(value = """
            SELECT c.* FROM catalog_listings c
            WHERE (:q IS NULL OR LOWER(c.search_blob) LIKE CONCAT('%', LOWER(:q), '%'))
              AND (:country IS NULL OR c.country_code = :country)
              AND (:city IS NULL OR c.city_fold = :city OR LOWER(c.city) = LOWER(:city))
              AND (:contract IS NULL OR c.contract_label = :contract)
              AND (:workMode IS NULL OR c.work_mode = :workMode)
              AND (:source IS NULL OR c.source_code = :source)
              AND (:origin IS NULL OR c.origin = :origin)
              AND (:since IS NULL OR (c.published_trusted = 1 AND c.published_at IS NOT NULL AND c.published_at >= :since))
            ORDER BY c.sort_at DESC, c.id DESC
            LIMIT :limit OFFSET :offset
            """, nativeQuery = true)
    List<CatalogListing> findFilteredPage(
            @Param("q") String q,
            @Param("country") String country,
            @Param("city") String city,
            @Param("contract") String contract,
            @Param("workMode") String workMode,
            @Param("source") String source,
            @Param("origin") String origin,
            @Param("since") java.time.Instant since,
            @Param("limit") int limit,
            @Param("offset") int offset);

    @Query(value = """
            SELECT c.id, c.origin, c.ref_id, c.skills_text FROM catalog_listings c
            WHERE (:q IS NULL OR LOWER(c.search_blob) LIKE CONCAT('%', LOWER(:q), '%'))
              AND (:country IS NULL OR c.country_code = :country)
              AND (:city IS NULL OR c.city_fold = :city OR LOWER(c.city) = LOWER(:city))
              AND (:contract IS NULL OR c.contract_label = :contract)
              AND (:workMode IS NULL OR c.work_mode = :workMode)
              AND (:source IS NULL OR c.source_code = :source)
              AND (:origin IS NULL OR c.origin = :origin)
              AND (:since IS NULL OR (c.published_trusted = 1 AND c.published_at IS NOT NULL AND c.published_at >= :since))
            ORDER BY c.sort_at DESC, c.id DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<Object[]> findForYouCandidates(
            @Param("q") String q,
            @Param("country") String country,
            @Param("city") String city,
            @Param("contract") String contract,
            @Param("workMode") String workMode,
            @Param("source") String source,
            @Param("origin") String origin,
            @Param("since") java.time.Instant since,
            @Param("limit") int limit);
}