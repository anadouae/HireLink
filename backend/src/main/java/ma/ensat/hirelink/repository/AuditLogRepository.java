package ma.ensat.hirelink.repository;

import ma.ensat.hirelink.model.AuditLog;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
    List<AuditLog> findAllByOrderByIdDesc(Pageable pageable);
    List<AuditLog> findByActionOrderByIdDesc(String action, Pageable pageable);

    /** L'export CSV remonte plus loin que l'écran, sans jamais vider la table en memory. */
    List<AuditLog> findByActorUserIdAndActionInOrderByIdDesc(Long actorUserId, Collection<String> actions, Pageable pageable);

    List<AuditLog> findTop2000ByOrderByIdDesc();
    List<AuditLog> findTop2000ByActionOrderByIdDesc(String action);

    /** Journal d'audit : on garde un an, pas davantage — la table ne sert qu'à relire une décision récente. */
    @Modifying
    @Query("delete from AuditLog a where a.createdAt < :limit")
    int purgeOlderThan(@Param("limit") Instant limit);
}
