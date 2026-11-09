package ma.ensat.hirelink.model;

import jakarta.persistence.*;
import java.time.Instant;

/** Transaction de paiement simulée. Aucun numéro de carte ni cryptogramme n'est stocké. */
@Entity
@Table(name = "payment_transactions", uniqueConstraints = {
        @UniqueConstraint(name = "uk_payment_idempotency", columnNames = "idempotency_key"),
        @UniqueConstraint(name = "uk_payment_reference", columnNames = "reference_code")
})
public class PaymentTransaction {

    public enum Status { PENDING, SUCCEEDED, FAILED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "invoice_id", nullable = false)
    private BillingInvoice invoice;

    @Column(name = "actor_user_id", nullable = false)
    private Long actorUserId;

    @Column(name = "idempotency_key", nullable = false, length = 80)
    private String idempotencyKey;

    @Column(nullable = false, length = 16)
    private String provider;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Status status = Status.PENDING;

    @Column(name = "reference_code", nullable = false, length = 64)
    private String reference;

    @Column(name = "amount_ttc", nullable = false)
    private double amountTtc;

    @Column(nullable = false, length = 8)
    private String currency = "MAD";

    @Column(nullable = false, length = 16)
    private String scenario;

    @Column(name = "failure_reason", length = 255)
    private String failureReason;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "completed_at")
    private Instant completedAt;

    public Long getId() { return id; }
    public BillingInvoice getInvoice() { return invoice; }
    public void setInvoice(BillingInvoice invoice) { this.invoice = invoice; }
    public Long getActorUserId() { return actorUserId; }
    public void setActorUserId(Long actorUserId) { this.actorUserId = actorUserId; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public String getReference() { return reference; }
    public void setReference(String reference) { this.reference = reference; }
    public double getAmountTtc() { return amountTtc; }
    public void setAmountTtc(double amountTtc) { this.amountTtc = amountTtc; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public String getScenario() { return scenario; }
    public void setScenario(String scenario) { this.scenario = scenario; }
    public String getFailureReason() { return failureReason; }
    public void setFailureReason(String failureReason) { this.failureReason = failureReason; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
}
