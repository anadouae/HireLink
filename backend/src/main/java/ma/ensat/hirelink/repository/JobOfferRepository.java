package ma.ensat.hirelink.repository;

import ma.ensat.hirelink.model.JobOffer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface JobOfferRepository extends JpaRepository<JobOffer, Long> {

    /**
     * Vitrine, fiches entreprise et listes recruteur : recruteur et organisation sont lus dans la
     * même requête. Sans ce chargement, chaque offre relançait deux lectures (D1).
     */
    @Query("""
            SELECT DISTINCT o FROM JobOffer o
            JOIN FETCH o.recruiter
            LEFT JOIN FETCH o.organization
            WHERE o.published = true AND o.suspended = false
            """)
    List<JobOffer> findByPublishedTrueAndSuspendedFalse();

    @Query("""
            SELECT DISTINCT o FROM JobOffer o
            JOIN FETCH o.recruiter
            LEFT JOIN FETCH o.organization
            WHERE o.recruiter.id = :recruiterId
            """)
    List<JobOffer> findByRecruiterId(@Param("recruiterId") Long recruiterId);

    @Query("""
            SELECT DISTINCT o FROM JobOffer o
            JOIN FETCH o.recruiter
            LEFT JOIN FETCH o.organization
            WHERE o.organization.id = :organizationId
            """)
    List<JobOffer> findByOrganizationId(@Param("organizationId") Long organizationId);

    @Query("""
            SELECT DISTINCT o FROM JobOffer o
            JOIN FETCH o.recruiter
            LEFT JOIN FETCH o.organization
            WHERE o.recruiter.id IN :recruiterIds
            """)
    List<JobOffer> findByRecruiterIdIn(@Param("recruiterIds") java.util.Collection<Long> recruiterIds);

    long countByPublishedTrue();
    long countBySuspendedTrue();

    /** Liste d'administration : le recruteur et l'entreprise sont déjà chargés (D2). */
    @Query("""
            SELECT DISTINCT o FROM JobOffer o
            JOIN FETCH o.recruiter
            LEFT JOIN FETCH o.organization
            """)
    List<JobOffer> findAllWithRecruiter();
    List<JobOffer> findTop10ByTitleContainingIgnoreCaseOrLocationContainingIgnoreCase(String title, String location);

    /**
     * Rattache les offres antérieures à la colonne organization_id à l'organisation active de leur auteur.
     * Idempotent (ne touche que les lignes NULL) et non destructif : les historiques sont conservés. Une
     * offre dont l'auteur a deux appartenances actives (donnée incohérente, R02) est laissée en attente
     * d'une décision d'administration plutôt que d'être attribuée au hasard.
     */
    @Modifying
    @Query(value = """
            UPDATE job_offers o
            JOIN memberships m ON m.user_id = o.recruiter_id AND m.active = 1
            SET o.organization_id = m.organization_id
            WHERE o.organization_id IS NULL
              AND (SELECT COUNT(*) FROM memberships m2
                   WHERE m2.user_id = o.recruiter_id AND m2.active = 1) = 1
            """, nativeQuery = true)
    int backfillMissingOrganization();

    @Query("SELECT COUNT(o) FROM JobOffer o WHERE o.organization IS NULL")
    long countWithoutOrganization();
}
