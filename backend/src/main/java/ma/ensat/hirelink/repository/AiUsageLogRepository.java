package ma.ensat.hirelink.repository;

import ma.ensat.hirelink.model.AiUsageLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AiUsageLogRepository extends JpaRepository<AiUsageLog, Long> {
    
}
