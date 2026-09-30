package ma.ensat.hirelink.model;

import jakarta.persistence.*;
import java.time.Instant;

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
    @Enumerated(EnumType.STRING)
    @Column(name = "plan_tier", nullable = false, length = 24)
    private PlanTier planTier = PlanTier.ACCES;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_user_id")
    private User owner;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getIce() { return ice; }
    public void setIce(String ice) { this.ice = ice; }
    public boolean isVerified() { return verified; }
    public void setVerified(boolean verified) { this.verified = verified; }
    public PlanTier getPlanTier() { return planTier; }
    public void setPlanTier(PlanTier planTier) { this.planTier = planTier; }
    public User getOwner() { return owner; }
    public void setOwner(User owner) { this.owner = owner; }
    public Instant getCreatedAt() { return createdAt; }
}
