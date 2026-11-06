package ma.ensat.hirelink.repository;

import ma.ensat.hirelink.model.OrganizationInvitation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface OrganizationInvitationRepository extends JpaRepository<OrganizationInvitation, Long> {
    Optional<OrganizationInvitation> findByTokenHash(String tokenHash);

    @Modifying
    @Query("UPDATE OrganizationInvitation i SET i.revokedAt = :revokedAt " +
            "WHERE i.organization.id = :organizationId AND LOWER(i.email) = LOWER(:email) " +
            "AND i.acceptedAt IS NULL AND i.revokedAt IS NULL")
    void revokePending(@Param("organizationId") Long organizationId,
                       @Param("email") String email,
                       @Param("revokedAt") Instant revokedAt);
}
