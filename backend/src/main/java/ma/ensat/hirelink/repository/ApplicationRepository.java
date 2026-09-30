package ma.ensat.hirelink.repository;

import ma.ensat.hirelink.model.Application;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ApplicationRepository extends JpaRepository<Application, Long> {
    List<Application> findByCandidateId(Long candidateId);
    List<Application> findByJobOfferId(Long jobOfferId);

    @Query("SELECT a FROM Application a JOIN FETCH a.jobOffer o WHERE o.recruiter.id = :recruiterId")
    List<Application> findByJobOfferRecruiterId(@Param("recruiterId") Long recruiterId);

    Optional<Application> findByCandidateIdAndJobOfferId(Long candidateId, Long jobOfferId);
}
