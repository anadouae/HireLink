package ma.ensat.hirelink.repository;

import ma.ensat.hirelink.model.AiUsageLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface AiUsageLogRepository extends JpaRepository<AiUsageLog, Long> {

    /**
     * Crédits déjà débités sur la période. Le filtre porte sur la colonne débitée, pas sur « success » :
     * une réservation en cours (credit = 1, succès pas encore connu) occupe déjà le quota, sinon N appels
     * simultanés lisent le même solde et dépassent le plafond. Un échec est liquidé à 0 crédit.
     */
    @Query("""
            SELECT COALESCE(SUM(a.creditsSpent), 0) FROM AiUsageLog a
            WHERE a.userId = :userId
              AND a.createdAt >= :from AND a.createdAt < :to
            """)
    Integer sumCreditsSpent(
            @Param("userId") Long userId,
            @Param("from") Instant from,
            @Param("to") Instant to
    );

    /** Réservations jamais liquidées (processus arrêté en plein appel) : à libérer pour ne pas perdre un crédit. */
    @Query("""
            SELECT a FROM AiUsageLog a
            WHERE a.success = false AND a.creditsSpent > 0 AND a.createdAt < :before
            """)
    List<AiUsageLog> findUnsettledReservations(@Param("before") Instant before);

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
