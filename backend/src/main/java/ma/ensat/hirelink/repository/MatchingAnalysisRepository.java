package ma.ensat.hirelink.repository;

import ma.ensat.hirelink.model.MatchingAnalysis;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MatchingAnalysisRepository extends JpaRepository<MatchingAnalysis, Long> {
    List<MatchingAnalysis> findByApplicationIdOrderByCreatedAtDesc(Long applicationId);
}
