package ma.ensat.hirelink.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "organizations")
public class Organization {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 200)
    private String name;
    @Column(length = 32)
    private String ice;
    @Column(nullable = false)
    private boolean verified = false;
    /** Même sémantique que User.verificationStatus (LinkedIn-like a posteriori). */
    @Column(name = "verification_status", length = 48)
    private String verificationStatus = "PENDING";
    /** AUTO_REGISTRY | ADMIN | PENDING */
    @Column(name = "verification_method", length = 24)
    private String verificationMethod = "PENDING";
    @Column(length = 180)
    private String website;
    @Column(name = "logo_url", length = 400)
    private String logoUrl;
    @Column(length = 80)
    private String sector;
    @Column(length = 80)
    private String city;
    @Column(name = "employee_range", length = 40)
    private String employeeRange;
    @Column(length = 1200)
    private String presentation;
    @Enumerated(EnumType.STRING)
    @Column(name = "plan_tier", nullable = false, length = 24)
    private PlanTier planTier = PlanTier.ACCES;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_user_id")
    private User owner;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    /** Membres de l'organisation (@OneToMany via Membership). */
    @OneToMany(mappedBy = "organization")
    private List<Membership> memberships = new ArrayList<>();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getIce() { return ice; }
    public void setIce(String ice) { this.ice = ice; }
    public boolean isVerified() { return verified; }
    public void setVerified(boolean verified) { this.verified = verified; }
    public String getVerificationStatus() { return verificationStatus; }
    public void setVerificationStatus(String verificationStatus) { this.verificationStatus = verificationStatus; }
    public String getVerificationMethod() { return verificationMethod; }
    public void setVerificationMethod(String verificationMethod) { this.verificationMethod = verificationMethod; }
    public String getWebsite() { return website; }
    public void setWebsite(String website) { this.website = website; }
    public String getLogoUrl() { return logoUrl; }
    public void setLogoUrl(String logoUrl) { this.logoUrl = logoUrl; }
    public String getSector() { return sector; }
    public void setSector(String sector) { this.sector = sector; }
    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }
    public String getEmployeeRange() { return employeeRange; }
    public void setEmployeeRange(String employeeRange) { this.employeeRange = employeeRange; }
    public String getPresentation() { return presentation; }
    public void setPresentation(String presentation) { this.presentation = presentation; }
    public PlanTier getPlanTier() { return planTier; }
    public void setPlanTier(PlanTier planTier) { this.planTier = planTier; }
    public User getOwner() { return owner; }
    public void setOwner(User owner) { this.owner = owner; }
    public Instant getCreatedAt() { return createdAt; }
    public List<Membership> getMemberships() { return memberships; }
    public void setMemberships(List<Membership> memberships) { this.memberships = memberships; }
}
