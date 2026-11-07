package ma.ensat.hirelink.model;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * Abonnement d'un candidat à une entreprise. Une seule ligne par couple : sans cette contrainte,
 * le compteur d'abonnés grossirait à chaque clic et la notification de nouvelle offre serait envoyée
 * deux fois au même candidat.
 */
@Entity
@Table(name = "company_follows",
       uniqueConstraints = @UniqueConstraint(columnNames = {"candidate_user_id", "organization_id"}),
       indexes = {
           @Index(name = "idx_follow_org", columnList = "organization_id"),
           @Index(name = "idx_follow_candidate", columnList = "candidate_user_id")
       })
public class CompanyFollow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "candidate_user_id", nullable = false)
    private Long candidateUserId;

    @Column(name = "organization_id", nullable = false)
    private Long organizationId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public Long getId() { return id; }
    public Long getCandidateUserId() { return candidateUserId; }
    public void setCandidateUserId(Long candidateUserId) { this.candidateUserId = candidateUserId; }
    public Long getOrganizationId() { return organizationId; }
    public void setOrganizationId(Long organizationId) { this.organizationId = organizationId; }
    public Instant getCreatedAt() { return createdAt; }
}
