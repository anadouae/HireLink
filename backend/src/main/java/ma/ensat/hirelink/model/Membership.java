package ma.ensat.hirelink.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "memberships", uniqueConstraints = @UniqueConstraint(columnNames = {"organization_id", "user_id"}))
public class Membership {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    @Enumerated(EnumType.STRING)
    @Column(name = "org_role", nullable = false, length = 24)
    private OrgRole orgRole;
    @Column(nullable = false)
    private boolean active = true;
    @Column(name = "invited_at")
    private Instant invitedAt = Instant.now();
    @Column(name = "accepted_at")
    private Instant acceptedAt = Instant.now();
    /** Une mise en pause reste visible et réversible ; un retrait ne l'est pas. Sans colonne commune, les deux se confondraient. */
    @Column(name = "suspended_at")
    private Instant suspendedAt;

    public Long getId() { return id; }
    public Organization getOrganization() { return organization; }
    public void setOrganization(Organization organization) { this.organization = organization; }
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
    public OrgRole getOrgRole() { return orgRole; }
    public void setOrgRole(OrgRole orgRole) { this.orgRole = orgRole; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public Instant getInvitedAt() { return invitedAt; }
    public void setInvitedAt(Instant invitedAt) { this.invitedAt = invitedAt; }
    public Instant getAcceptedAt() { return acceptedAt; }
    public void setAcceptedAt(Instant acceptedAt) { this.acceptedAt = acceptedAt; }
    public Instant getSuspendedAt() { return suspendedAt; }
    public void setSuspendedAt(Instant suspendedAt) { this.suspendedAt = suspendedAt; }
    public boolean isSuspended() { return suspendedAt != null; }
}
