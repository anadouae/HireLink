package ma.ensat.hirelink.repository;

import ma.ensat.hirelink.model.OfferReport;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OfferReportRepository extends JpaRepository<OfferReport, Long> {
    long countByJobOfferId(Long jobOfferId);
    Optional<OfferReport> findByJobOfferIdAndReporterUserId(Long jobOfferId, Long reporterUserId);
}
