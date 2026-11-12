package ma.ensat.hirelink.repository;

import ma.ensat.hirelink.model.ExternalSyncRun;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ExternalSyncRunRepository extends JpaRepository<ExternalSyncRun, Long> {
    Optional<ExternalSyncRun> findFirstBySourceOrderByFinishedAtDesc(String source);
}
