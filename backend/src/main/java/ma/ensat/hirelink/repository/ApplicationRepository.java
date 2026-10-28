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
}
