package ma.ensat.hirelink.repository;

import ma.ensat.hirelink.model.Application;
import ma.ensat.hirelink.model.ApplicationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ApplicationRepository extends JpaRepository<Application, Long> {
    List<Application> findByCandidateId(Long candidateId);
    List<Application> findByJobOfferId(Long jobOfferId);

    @Query("SELECT a FROM Application a JOIN FETCH a.jobOffer o JOIN FETCH a.candidate")
    List<Application> findAllFetched();

    @Query("SELECT a FROM Application a JOIN FETCH a.jobOffer o JOIN FETCH a.candidate WHERE o.recruiter.id IN :recruiterIds")
    List<Application> findByJobOfferRecruiterIdIn(@Param("recruiterIds") Collection<Long> recruiterIds);

    @Query("SELECT a FROM Application a JOIN FETCH a.jobOffer o JOIN FETCH a.candidate WHERE a.assignedHmUserId = :hmId")
    List<Application> findByAssignedHmUserId(@Param("hmId") Long hmId);

    Optional<Application> findByCandidateIdAndJobOfferId(Long candidateId, Long jobOfferId);

    long countByCandidateIdAndSubmittedAtAfter(Long candidateId, Instant after);

    long countByStatus(ApplicationStatus status);

    @Query("""
            SELECT a FROM Application a JOIN FETCH a.jobOffer o JOIN FETCH a.candidate
            WHERE a.responseDeadline IS NOT NULL
              AND a.responseDeadline < :now
              AND a.slaBreachNotified = false
              AND a.status NOT IN :terminals
            """)
    List<Application> findSlaBreaches(
            @Param("now") Instant now,
            @Param("terminals") Collection<ApplicationStatus> terminals
    );

    @Query("""
            SELECT a FROM Application a JOIN FETCH a.jobOffer o JOIN FETCH a.candidate
            WHERE a.status = ma.ensat.hirelink.model.ApplicationStatus.INTERVIEW
              AND a.interviewAt > :now AND a.interviewAt <= :limit
              AND (a.interviewStatus IS NULL OR a.interviewStatus <> :cancelled)
              AND (a.interviewReminderSent IS NULL OR a.interviewReminderSent = false)
            """)
    List<Application> findInterviewsToRemind(
            @Param("now") Instant now,
            @Param("limit") Instant limit,
            @Param("cancelled") String cancelled
    );

    /** (organisation, recruteur, candidature, première réponse) — la matière première des vélocités mesurées. */
    @Query("""
            SELECT o.recruiter.assignedOrgId, o.recruiter.id, a.submittedAt, a.firstDecisionAt
            FROM Application a JOIN a.jobOffer o
            WHERE a.firstDecisionAt IS NOT NULL AND a.submittedAt >= :since
            """)
    List<Object[]> findDecidedDurations(@Param("since") Instant since);

    /** (offre, nombre de dossiers) en une requête : le tableau admin affiche 100+ offres d'un coup. */
    @Query("SELECT a.jobOffer.id, COUNT(a) FROM Application a GROUP BY a.jobOffer.id")
    List<Object[]> countGroupedByOffer();

    @Query("""
            SELECT a FROM Application a JOIN FETCH a.candidate c JOIN FETCH a.jobOffer o
            WHERE LOWER(c.fullName) LIKE :like OR LOWER(c.email) LIKE :like OR LOWER(o.title) LIKE :like
            ORDER BY a.id DESC
            """)
    List<Application> search(@Param("like") String like, org.springframework.data.domain.Pageable page);
}
