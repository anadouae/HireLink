package ma.ensat.hirelink.repository;

import ma.ensat.hirelink.model.UserSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface UserSessionRepository extends JpaRepository<UserSession, String> {
    Optional<UserSession> findByTokenHash(String tokenHash);
    Optional<UserSession> findByPreviousHash(String previousHash);
    List<UserSession> findByUserIdAndRevokedAtIsNullOrderByLastUsedAtDesc(Long userId);
    boolean existsByUserIdAndDeviceHash(Long userId, String deviceHash);
    long countByUserId(Long userId);

    @Modifying
    @Query("delete from UserSession s where s.absoluteExpiresAt < :limit or (s.revokedAt is not null and s.revokedAt < :limit)")
    int purgeOlderThan(@Param("limit") Instant limit);
}
