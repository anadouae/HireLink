package ma.ensat.hirelink.repository;

import ma.ensat.hirelink.model.InAppNotification;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface InAppNotificationRepository extends JpaRepository<InAppNotification, Long> {
    List<InAppNotification> findByUserIdOrderByCreatedAtDescIdDesc(Long userId);

    /**
     * Filtres appliqués par la base, pas par le JavaScript : un compte ancien ne doit pas télécharger
     * tout son historique pour afficher « les 7 derniers jours ». Un paramètre null = « pas de filtre » ;
     * pour l'état lu, null = les deux, false = non lues seules, true = lues seules.
     */
    @Query("""
            select n from InAppNotification n
            where n.userId = :userId
              and (:kind is null or n.kind = :kind)
              and (:since is null or n.createdAt >= :since)
              and (:readFlag is null or n.readFlag = :readFlag)
            order by n.createdAt desc, n.id desc
            """)
    List<InAppNotification> findFiltered(@Param("userId") Long userId, @Param("kind") String kind,
                                         @Param("since") Instant since, @Param("readFlag") Boolean readFlag,
                                         Pageable pageable);

    @Modifying
    @Query("delete from InAppNotification n where n.readFlag = true and n.createdAt < :limit")
    int purgeReadBefore(@Param("limit") Instant limit);

    @Modifying
    @Query("delete from InAppNotification n where n.createdAt < :limit")
    int purgeBefore(@Param("limit") Instant limit);
}
