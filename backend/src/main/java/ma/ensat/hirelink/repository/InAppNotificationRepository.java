package ma.ensat.hirelink.repository;

import ma.ensat.hirelink.model.InAppNotification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface InAppNotificationRepository extends JpaRepository<InAppNotification, Long> {
    List<InAppNotification> findByUserIdOrderByCreatedAtDescIdDesc(Long userId);

    @Modifying
    @Query("delete from InAppNotification n where n.readFlag = true and n.createdAt < :limit")
    int purgeReadBefore(@Param("limit") Instant limit);

    @Modifying
    @Query("delete from InAppNotification n where n.createdAt < :limit")
    int purgeBefore(@Param("limit") Instant limit);
}
