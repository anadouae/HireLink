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

    /**
     * Liste d'équipe : membres en cours, comptes mis en pause et invitations qui n'ont pas encore de réponse.
     * Ces trois états se ressemblent en base (active = false) et doivent rester distincts à l'écran.
     */
    @Query("""
            SELECT m FROM Membership m JOIN FETCH m.organization JOIN FETCH m.user
            WHERE m.organization.id = :orgId AND (m.active = true OR m.suspendedAt IS NOT NULL
              OR EXISTS (SELECT 1 FROM OrganizationInvitation i
                         WHERE i.organization.id = m.organization.id AND i.user.id = m.user.id
                           AND i.acceptedAt IS NULL AND i.revokedAt IS NULL AND i.expiresAt > CURRENT_TIMESTAMP))
            """)
    List<Membership> findTeamRows(@Param("orgId") Long organizationId);

    /**
     * Le plafond de sièges ne compte pas une invitation sans réponse : tant que la personne n'a pas accepté,
     * elle n'occupe pas de place dans l'équipe.
     */
    @Query("""
            SELECT COUNT(m) FROM Membership m
            WHERE m.organization.id = :orgId AND (m.active = true OR m.suspendedAt IS NOT NULL)
            """)
    long countTeamSeats(@Param("orgId") Long organizationId);

    @Query("SELECT m FROM Membership m JOIN FETCH m.organization JOIN FETCH m.user WHERE m.user.id = :userId AND m.active = true")
    List<Membership> findByUserIdAndActiveTrue(@Param("userId") Long userId);

    @Query("SELECT m FROM Membership m JOIN FETCH m.organization JOIN FETCH m.user WHERE m.active = true")
    List<Membership> findAllActiveWithOrganization();
}
