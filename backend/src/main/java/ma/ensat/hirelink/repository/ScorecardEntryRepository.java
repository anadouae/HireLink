package ma.ensat.hirelink.repository;

import ma.ensat.hirelink.model.ScorecardEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ScorecardEntryRepository extends JpaRepository<ScorecardEntry, Long> {
    List<ScorecardEntry> findByApplicationId(Long applicationId);
}
