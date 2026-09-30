package ma.ensat.hirelink.repository;

import ma.ensat.hirelink.model.Membership;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MembershipRepository extends JpaRepository<Membership, Long> {

    @Query("SELECT m FROM Membership m JOIN FETCH m.organization JOIN FETCH m.user WHERE m.organization.id = :orgId AND m.active = true")
    List<Membership> findByOrganizationIdAndActiveTrue(@Param("orgId") Long organizationId);

    @Query("SELECT m FROM Membership m JOIN FETCH m.organization JOIN FETCH m.user WHERE m.organization.id = :orgId AND m.user.id = :userId")
    Optional<Membership> findByOrganizationIdAndUserId(@Param("orgId") Long organizationId, @Param("userId") Long userId);

    @Query("SELECT m FROM Membership m JOIN FETCH m.organization JOIN FETCH m.user WHERE m.user.id = :userId AND m.active = true")
    List<Membership> findByUserIdAndActiveTrue(@Param("userId") Long userId);
}
