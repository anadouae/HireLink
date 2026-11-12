package ma.ensat.hirelink.model;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * Acceptation versionnée d'un document légal (CGU, confidentialité).
 * Distinct des consentements marketing (non implémentés tant qu'aucune finalité promo n'existe).
 */
@Entity
@Table(name = "legal_acceptances", indexes = {
        @Index(name = "idx_legal_user", columnList = "user_id"),
        @Index(name = "idx_legal_purpose", columnList = "purpose")
})
public class LegalAcceptance {

    public static final String PURPOSE_TERMS = "TERMS";
    public static final String PURPOSE_PRIVACY = "PRIVACY";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 32)
    private String purpose;

    @Column(name = "document_version", nullable = false, length = 64)
    private String documentVersion;

    @Column(nullable = false, length = 32)
    private String source = "REGISTER";

    @Column(nullable = false)
    private boolean granted = true;

    @Column(name = "accepted_at", nullable = false, updatable = false)
    private Instant acceptedAt = Instant.now();

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getPurpose() { return purpose; }
    public void setPurpose(String purpose) { this.purpose = purpose; }
    public String getDocumentVersion() { return documentVersion; }
    public void setDocumentVersion(String documentVersion) { this.documentVersion = documentVersion; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public boolean isGranted() { return granted; }
    public void setGranted(boolean granted) { this.granted = granted; }
    public Instant getAcceptedAt() { return acceptedAt; }
    public void setAcceptedAt(Instant acceptedAt) { this.acceptedAt = acceptedAt; }
}
