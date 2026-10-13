package ma.ensat.hirelink.repository;

import ma.ensat.hirelink.model.AiUsageLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

public interface AiUsageLogRepository extends JpaRepository<AiUsageLog, Long> {

    @Query("""
            SELECT COALESCE(SUM(a.creditsSpent), 0) FROM AiUsageLog a
            WHERE a.userId = :userId AND a.success = true
              AND a.createdAt >= :from AND a.createdAt < :to
            """)
    Integer sumCreditsSpent(
            @Param("userId") Long userId,
            @Param("from") Instant from,
            @Param("to") Instant to
    );
}
