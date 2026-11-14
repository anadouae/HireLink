package ma.ensat.hirelink.model;

import jakarta.persistence.*;
import java.time.Instant;

/** Board ATS allowliste (Greenhouse / Lever / Ashby). Pas un compte entreprise HireLink. */
@Entity
@Table(name = "external_job_boards",
        uniqueConstraints = @UniqueConstraint(columnNames = {"provider", "board_key"}))
public class ExternalJobBoard {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 40)
    private String provider;

    @Column(name = "board_key", nullable = false, length = 80)
    private String boardKey;

    @Column(name = "company_name", nullable = false, length = 200)
    private String companyName;

    @Column(name = "reference_url", length = 500)
    private String referenceUrl;

    @Column(name = "region_hint", length = 80)
    private String regionHint;

    @Column(nullable = false)
    private boolean enabled = false;

    @Column(name = "attribution_notes", length = 500)
    private String attributionNotes;

    @Column(name = "verification_notes", length = 500)
    private String verificationNotes;

    @Column(name = "last_success_at")
    private Instant lastSuccessAt;

    @Column(name = "last_error", length = 300)
    private String lastError;

    public Long getId() { return id; }
    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
    public String getBoardKey() { return boardKey; }
    public void setBoardKey(String boardKey) { this.boardKey = boardKey; }
    public String getCompanyName() { return companyName; }
    public void setCompanyName(String companyName) { this.companyName = companyName; }
    public String getReferenceUrl() { return referenceUrl; }
    public void setReferenceUrl(String referenceUrl) { this.referenceUrl = referenceUrl; }
    public String getRegionHint() { return regionHint; }
    public void setRegionHint(String regionHint) { this.regionHint = regionHint; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getAttributionNotes() { return attributionNotes; }
    public void setAttributionNotes(String attributionNotes) { this.attributionNotes = attributionNotes; }
    public String getVerificationNotes() { return verificationNotes; }
    public void setVerificationNotes(String verificationNotes) { this.verificationNotes = verificationNotes; }
    public Instant getLastSuccessAt() { return lastSuccessAt; }
    public void setLastSuccessAt(Instant lastSuccessAt) { this.lastSuccessAt = lastSuccessAt; }
    public String getLastError() { return lastError; }
    public void setLastError(String lastError) { this.lastError = lastError; }
}