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

    /**
     * Journal filtré côté serveur : actions (liste facultative), période [from, to[, recherche libre
     * sur le compte ou le détail, et résultat (réussi / échoué). Paginé avec total.
     */
    @Query("""
            select a from AuditLog a
            where (:allActions = true or a.action in :actions)
              and (:fromAt is null or a.createdAt >= :fromAt)
              and (:toAt is null or a.createdAt < :toAt)
              and (:q is null or lower(a.actorEmail) like :q or lower(a.detail) like :q)
              and (:success is null or a.success = :success)
            order by a.id desc
            """)
    org.springframework.data.domain.Page<AuditLog> search(
            @Param("allActions") boolean allActions,
            @Param("actions") Collection<String> actions,
            @Param("fromAt") Instant fromAt,
            @Param("toAt") Instant toAt,
            @Param("q") String q,
            @Param("success") Boolean success,
            Pageable pageable);
    List<AuditLog> findTop2000ByActionOrderByIdDesc(String action);

    /** Journal d'audit : on garde un an, pas davantage — la table ne sert qu'à relire une décision récente. */
    @Modifying
    @Query("delete from AuditLog a where a.createdAt < :limit")
    int purgeOlderThan(@Param("limit") Instant limit);
}
