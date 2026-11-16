package ma.ensat.hirelink.model;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "billing_promotions")
public class BillingPromotion {

    public enum DiscountType { PERCENT, FIXED }
    public enum StackingPolicy { NONE, WITH_YEARLY_ONLY }
    public enum RenewalPolicy { FIRST_PERIOD_ONLY, EVERY_RENEWAL }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 40)
    private String code;

    @Column(nullable = false, length = 120)
    private String label;

    /** Plan cible (PROFESSIONNEL, ENTREPRISE, PERFORMANCE…). */
    @Column(name = "plan_code", nullable = false, length = 32)
    private String planCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "discount_type", nullable = false, length = 16)
    private DiscountType discountType;

    /** Pourcentage 1–100 ou montant fixe MAD TTC. */
    @Column(name = "discount_value", nullable = false)
    private int discountValue;

    @Column(name = "valid_from")
    private Instant validFrom;

    @Column(name = "valid_to")
    private Instant validTo;

    @Column(nullable = false)
    private boolean active = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "stacking_policy", nullable = false, length = 32)
    private StackingPolicy stackingPolicy = StackingPolicy.NONE;

    @Enumerated(EnumType.STRING)
    @Column(name = "renewal_policy", nullable = false, length = 32)
    private RenewalPolicy renewalPolicy = RenewalPolicy.FIRST_PERIOD_ONLY;

    @Column(name = "created_by_user_id")
    private Long createdByUserId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @Column(length = 500)
    private String note;

    public Long getId() { return id; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public String getPlanCode() { return planCode; }
    public void setPlanCode(String planCode) { this.planCode = planCode; }
    public DiscountType getDiscountType() { return discountType; }
    public void setDiscountType(DiscountType discountType) { this.discountType = discountType; }
    public int getDiscountValue() { return discountValue; }
    public void setDiscountValue(int discountValue) { this.discountValue = discountValue; }
    public Instant getValidFrom() { return validFrom; }
    public void setValidFrom(Instant validFrom) { this.validFrom = validFrom; }
    public Instant getValidTo() { return validTo; }
    public void setValidTo(Instant validTo) { this.validTo = validTo; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public StackingPolicy getStackingPolicy() { return stackingPolicy; }
    public void setStackingPolicy(StackingPolicy stackingPolicy) { this.stackingPolicy = stackingPolicy; }
    public RenewalPolicy getRenewalPolicy() { return renewalPolicy; }
    public void setRenewalPolicy(RenewalPolicy renewalPolicy) { this.renewalPolicy = renewalPolicy; }
    public Long getCreatedByUserId() { return createdByUserId; }
    public void setCreatedByUserId(Long createdByUserId) { this.createdByUserId = createdByUserId; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
}
