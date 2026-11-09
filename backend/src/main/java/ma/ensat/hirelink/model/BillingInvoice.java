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

    /** Nul pour une facture personnelle (candidat). Les factures d'entreprise restent rattachées à l'organisation. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id")
    private Organization organization;

    /** Débiteur autorisé à payer. Figé à l'émission : un autre membre de l'entreprise ne le devient pas. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "debtor_user_id")
    private User debtorUser;

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

    /**
     * NON_PAYEE, PAYEE ou ANNULEE pour les factures de cette version.
     * EMISE, REGLEE, OFFERTE et AVOIR restent lisibles : ce sont des documents déjà émis, non réécrits.
     */
    @Column(nullable = false, length = 16)
    private String status = "NON_PAYEE";

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt = Instant.now();

    /** Instantané du destinataire au moment de l'émission. Un changement de profil ultérieur ne le modifie pas. */
    @Column(name = "bill_to_name", length = 200)
    private String billToName;

    @Column(name = "bill_to_email", length = 160)
    private String billToEmail;

    @Column(name = "bill_to_extra", length = 255)
    private String billToExtra;

    /** Faux tant que le paiement confirmé n'a pas accordé les droits. Nul = facture historique déjà appliquée. */
    @Column(name = "rights_applied")
    private Boolean rightsApplied;

    /** Faux si aucun taux de TVA n'est configuré. Nul = montant de TVA déjà enregistré sur une facture historique. */
    @Column(name = "vat_configured")
    private Boolean vatConfigured;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "transaction_ref", length = 64)
    private String transactionRef;

    /** Fin de la période que cette facture ouvre une fois payée. */
    @Column(name = "period_end")
    private Instant periodEnd;

    /** Motif libre, lu à l'écran : le motif de l'avoir, la raison d'un changement de statut. */
    @Column(length = 255)
    private String note;

    /** Une facture d'avoir annule une facture d'origine : le lien rend les deux lisibles ensemble. */
    @Column(name = "cancels_invoice_id")
    private Long cancelsInvoiceId;

    public Long getId() { return id; }
    public String getNumber() { return number; }
    public void setNumber(String number) { this.number = number; }
    public Organization getOrganization() { return organization; }
    public void setOrganization(Organization organization) { this.organization = organization; }
    public User getDebtorUser() { return debtorUser; }
    public void setDebtorUser(User debtorUser) { this.debtorUser = debtorUser; }
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
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public Long getCancelsInvoiceId() { return cancelsInvoiceId; }
    public void setCancelsInvoiceId(Long cancelsInvoiceId) { this.cancelsInvoiceId = cancelsInvoiceId; }
    public String getBillToName() { return billToName; }
    public void setBillToName(String billToName) { this.billToName = billToName; }
    public String getBillToEmail() { return billToEmail; }
    public void setBillToEmail(String billToEmail) { this.billToEmail = billToEmail; }
    public String getBillToExtra() { return billToExtra; }
    public void setBillToExtra(String billToExtra) { this.billToExtra = billToExtra; }
    public Boolean getRightsApplied() { return rightsApplied; }
    public void setRightsApplied(Boolean rightsApplied) { this.rightsApplied = rightsApplied; }
    public Boolean getVatConfigured() { return vatConfigured; }
    public void setVatConfigured(Boolean vatConfigured) { this.vatConfigured = vatConfigured; }
    public Instant getPaidAt() { return paidAt; }
    public void setPaidAt(Instant paidAt) { this.paidAt = paidAt; }
    public String getTransactionRef() { return transactionRef; }
    public void setTransactionRef(String transactionRef) { this.transactionRef = transactionRef; }
    public Instant getPeriodEnd() { return periodEnd; }
    public void setPeriodEnd(Instant periodEnd) { this.periodEnd = periodEnd; }

    /** Statut affiché. Les anciens codes restent en base et sont traduits ici, sans réécriture. */
    public String canonicalStatus() {
        String s = status == null ? "" : status;
        return switch (s) {
            case "PAYEE", "REGLEE", "OFFERTE" -> "PAYEE";
            case "NON_PAYEE", "EMISE" -> "NON_PAYEE";
            case "ANNULEE" -> "ANNULEE";
            case "AVOIR" -> "AVOIR";
            default -> s;
        };
    }

    public boolean payable() {
        return "NON_PAYEE".equals(canonicalStatus()) && amountTtc > 0 && !"AVOIR".equals(status);
    }
}
