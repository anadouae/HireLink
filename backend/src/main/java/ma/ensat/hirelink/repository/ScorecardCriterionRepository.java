package ma.ensat.hirelink.repository;

import ma.ensat.hirelink.model.ScorecardCriterion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ScorecardCriterionRepository extends JpaRepository<ScorecardCriterion, Long> {
    List<ScorecardCriterion> findByJobOfferId(Long jobOfferId);
}
