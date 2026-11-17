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

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from CatalogListing c where c.origin = :origin")
    void deleteByOrigin(@Param("origin") String origin);

    /**
     * Predicats communs : q, geo, contrat, mode, source, origin, date, entreprise,
     * plus salaire min (plafond ou plancher de l'offre), experience exacte, secteur LIKE.
     */
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
              AND (:minSalary IS NULL OR IFNULL(c.salary_max, c.salary_min) >= :minSalary)
              AND (:experience IS NULL OR UPPER(c.experience_level) = UPPER(:experience))
              AND (:sector IS NULL OR LOWER(IFNULL(c.sector, '')) LIKE CONCAT('%', LOWER(:sector), '%'))
              AND (:company IS NULL OR LOWER(c.company_name) LIKE CONCAT('%', LOWER(:company), '%'))
            """, nativeQuery = true)
    long countFiltered(
            @Param("q") String q,
            @Param("country") String country,
            @Param("city") String city,
            @Param("contract") String contract,
            @Param("workMode") String workMode,
            @Param("source") String source,
            @Param("origin") String origin,
            @Param("since") java.time.Instant since,
            @Param("minSalary") Integer minSalary,
            @Param("experience") String experience,
            @Param("sector") String sector,
            @Param("company") String company);

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
              AND (:minSalary IS NULL OR IFNULL(c.salary_max, c.salary_min) >= :minSalary)
              AND (:experience IS NULL OR UPPER(c.experience_level) = UPPER(:experience))
              AND (:sector IS NULL OR LOWER(IFNULL(c.sector, '')) LIKE CONCAT('%', LOWER(:sector), '%'))
              AND (:company IS NULL OR LOWER(c.company_name) LIKE CONCAT('%', LOWER(:company), '%'))
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
            @Param("minSalary") Integer minSalary,
            @Param("experience") String experience,
            @Param("sector") String sector,
            @Param("company") String company,
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
              AND (:minSalary IS NULL OR IFNULL(c.salary_max, c.salary_min) >= :minSalary)
              AND (:experience IS NULL OR UPPER(c.experience_level) = UPPER(:experience))
              AND (:sector IS NULL OR LOWER(IFNULL(c.sector, '')) LIKE CONCAT('%', LOWER(:sector), '%'))
              AND (:company IS NULL OR LOWER(c.company_name) LIKE CONCAT('%', LOWER(:company), '%'))
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
            @Param("minSalary") Integer minSalary,
            @Param("experience") String experience,
            @Param("sector") String sector,
            @Param("company") String company,
            @Param("limit") int limit);
}
