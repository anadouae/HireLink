package ma.ensat.hirelink.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "offer_reports",
       uniqueConstraints = @UniqueConstraint(columnNames = {"job_offer_id", "reporter_user_id"}),
       indexes = {
           @Index(name = "idx_report_offer", columnList = "job_offer_id"),
           @Index(name = "idx_report_reporter", columnList = "reporter_user_id")
       })
public class OfferReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "job_offer_id", nullable = false)
    private Long jobOfferId;

    @Column(name = "reporter_user_id", nullable = false)
    private Long reporterUserId;

    @Column(length = 500)
    private String reason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    /**
     * PENDING tant qu'aucun administrateur n'a tranché, puis FONDE (l'offre a bien été sanctionnée)
     * ou ECARTE (signalement non confirmé). Sans cette colonne, la file de modération ne se vide jamais.
     */
    @Column(nullable = false, length = 16)
    private String status = "PENDING";

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    /** Le traçable « qui a décidé » : affiché dans l'historique, il évite qu'un rétablissement soit anonyme. */
    @Column(name = "handled_by_email", length = 160)
    private String handledByEmail;

    public Long getId() { return id; }
    public Long getJobOfferId() { return jobOfferId; }
    public void setJobOfferId(Long jobOfferId) { this.jobOfferId = jobOfferId; }
    public Long getReporterUserId() { return reporterUserId; }
    public void setReporterUserId(Long reporterUserId) { this.reporterUserId = reporterUserId; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public Instant getCreatedAt() { return createdAt; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Instant getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(Instant resolvedAt) { this.resolvedAt = resolvedAt; }
    public String getHandledByEmail() { return handledByEmail; }
    public void setHandledByEmail(String handledByEmail) { this.handledByEmail = handledByEmail; }
}
