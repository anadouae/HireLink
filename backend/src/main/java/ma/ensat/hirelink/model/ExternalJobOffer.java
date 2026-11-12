package ma.ensat.hirelink.model;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * Offre lue chez un diffuseur autorisé. Elle n'est jamais une offre HireLink et n'ouvre pas de candidature.
 */
@Entity
@Table(name = "external_job_offers", uniqueConstraints = @UniqueConstraint(columnNames = {"source", "external_id"}),
        indexes = @Index(name = "idx_external_offer_available", columnList = "source, available"))
public class ExternalJobOffer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 40)
    private String source;

    @Column(name = "external_id", nullable = false, length = 80)
    private String externalId;

    @Column(name = "origin_url", nullable = false, length = 500)
    private String originUrl;

    @Column(name = "apply_url", length = 500)
    private String applyUrl;

    @Column(nullable = false, length = 200)
    private String company;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(length = 160)
    private String location;

    @Column(name = "contract_label", length = 60)
    private String contractLabel;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(length = 500)
    private String skills;

    @Column(name = "salary_min")
    private Integer salaryMin;

    @Column(name = "salary_max")
    private Integer salaryMax;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "last_synced_at", nullable = false)
    private Instant lastSyncedAt;

    /** Dernière présence dans un flux valide. Une absence isolée ne retire pas l'annonce. */
    @Column(name = "last_seen_at")
    private Instant lastSeenAt;

    @Column(nullable = false)
    private boolean available = true;

    /** Masquage choisi par l'administrateur. Une synchronisation ne le réaffiche pas. */
    @Column(name = "hidden_by_admin", nullable = false)
    private boolean hiddenByAdmin = false;

    public Long getId() { return id; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public String getExternalId() { return externalId; }
    public void setExternalId(String externalId) { this.externalId = externalId; }
    public String getOriginUrl() { return originUrl; }
    public void setOriginUrl(String originUrl) { this.originUrl = originUrl; }
    public String getApplyUrl() { return applyUrl; }
    public void setApplyUrl(String applyUrl) { this.applyUrl = applyUrl; }
    public String getCompany() { return company; }
    public void setCompany(String company) { this.company = company; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
    public String getContractLabel() { return contractLabel; }
    public void setContractLabel(String contractLabel) { this.contractLabel = contractLabel; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getSkills() { return skills; }
    public void setSkills(String skills) { this.skills = skills; }
    public Integer getSalaryMin() { return salaryMin; }
    public void setSalaryMin(Integer salaryMin) { this.salaryMin = salaryMin; }
    public Integer getSalaryMax() { return salaryMax; }
    public void setSalaryMax(Integer salaryMax) { this.salaryMax = salaryMax; }
    public Instant getPublishedAt() { return publishedAt; }
    public void setPublishedAt(Instant publishedAt) { this.publishedAt = publishedAt; }
    public Instant getLastSyncedAt() { return lastSyncedAt; }
    public void setLastSyncedAt(Instant lastSyncedAt) { this.lastSyncedAt = lastSyncedAt; }
    public Instant getLastSeenAt() { return lastSeenAt; }
    public void setLastSeenAt(Instant lastSeenAt) { this.lastSeenAt = lastSeenAt; }
    public boolean isAvailable() { return available; }
    public void setAvailable(boolean available) { this.available = available; }
    public boolean isHiddenByAdmin() { return hiddenByAdmin; }
    public void setHiddenByAdmin(boolean hiddenByAdmin) { this.hiddenByAdmin = hiddenByAdmin; }
}
