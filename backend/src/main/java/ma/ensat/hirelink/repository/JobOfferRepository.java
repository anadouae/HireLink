package ma.ensat.hirelink.repository;

import ma.ensat.hirelink.model.JobOffer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface JobOfferRepository extends JpaRepository<JobOffer, Long> {
    List<JobOffer> findByPublishedTrueAndSuspendedFalse();
    List<JobOffer> findByRecruiterId(Long recruiterId);

    List<JobOffer> findByRecruiterIdIn(java.util.Collection<Long> recruiterIds);
    long countByPublishedTrue();
}
