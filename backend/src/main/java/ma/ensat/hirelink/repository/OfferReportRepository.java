package ma.ensat.hirelink.repository;

import ma.ensat.hirelink.model.OfferReport;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OfferReportRepository extends JpaRepository<OfferReport, Long> {
    long countByJobOfferId(Long jobOfferId);
    Optional<OfferReport> findByJobOfferIdAndReporterUserId(Long jobOfferId, Long reporterUserId);
    List<OfferReport> findByJobOfferIdOrderByCreatedAtAsc(Long jobOfferId);
    List<OfferReport> findByStatusOrderByIdDesc(String status);

    /** Les derniers signalements tranchés, pour que la décision d'un administrateur reste consultable. */
    List<OfferReport> findByStatusInOrderByResolvedAtDesc(java.util.Collection<String> statuses);
    long countByStatus(String status);
}
