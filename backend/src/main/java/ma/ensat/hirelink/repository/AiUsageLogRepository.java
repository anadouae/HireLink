package ma.ensat.hirelink.repository;

import ma.ensat.hirelink.model.AiUsageLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

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

    /** (fonctionnalité, actions, crédits) — le détail du mois en cours, pour le relevé affiché. */
    @Query("""
            SELECT a.feature, COUNT(a), SUM(a.creditsSpent) FROM AiUsageLog a
            WHERE a.userId = :userId AND a.success = true
              AND a.createdAt >= :from AND a.createdAt < :to
            GROUP BY a.feature
            """)
    List<Object[]> sumByFeature(
            @Param("userId") Long userId,
            @Param("from") Instant from,
            @Param("to") Instant to
    );

    /** (organisation, utilisateur, actions, crédits) — la matière du relevé de consommation par entreprise. */
    @Query("""
            SELECT a.organizationId, a.userId, COUNT(a), SUM(a.creditsSpent) FROM AiUsageLog a
            WHERE a.success = true AND a.createdAt >= :from
            GROUP BY a.organizationId, a.userId
            """)
    List<Object[]> spentByUserSince(@Param("from") Instant from);
}
