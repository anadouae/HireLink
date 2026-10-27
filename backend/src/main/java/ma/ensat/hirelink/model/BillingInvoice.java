package ma.ensat.hirelink.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "billing_invoices")
public class BillingInvoice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 40)
    private String number;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @Column(name = "plan_code", nullable = false, length = 32)
    private String planCode;

    @Column(name = "plan_label", nullable = false, length = 80)
    private String planLabel;

    @Column(nullable = false, length = 12)
    private String cycle;

    @Column(name = "amount_ht", nullable = false)
    private double amountHt;

    @Column(nullable = false)
    private double tva;

    @Column(name = "amount_ttc", nullable = false)
    private double amountTtc;

    /** EMISE au moment du changement de plan. */
    @Column(nullable = false, length = 16)
    private String status = "EMISE";

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt = Instant.now();

    public Long getId() { return id; }
    public String getNumber() { return number; }
    public void setNumber(String number) { this.number = number; }
    public Organization getOrganization() { return organization; }
    public void setOrganization(Organization organization) { this.organization = organization; }
    public String getPlanCode() { return planCode; }
    public void setPlanCode(String planCode) { this.planCode = planCode; }
    public String getPlanLabel() { return planLabel; }
    public void setPlanLabel(String planLabel) { this.planLabel = planLabel; }
    public String getCycle() { return cycle; }
    public void setCycle(String cycle) { this.cycle = cycle; }
    public double getAmountHt() { return amountHt; }
    public void setAmountHt(double amountHt) { this.amountHt = amountHt; }
    public double getTva() { return tva; }
    public void setTva(double tva) { this.tva = tva; }
    public double getAmountTtc() { return amountTtc; }
    public void setAmountTtc(double amountTtc) { this.amountTtc = amountTtc; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Instant getIssuedAt() { return issuedAt; }
    public void setIssuedAt(Instant issuedAt) { this.issuedAt = issuedAt; }
}
