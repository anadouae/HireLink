package ma.ensat.hirelink.model;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * Projection denormalisee pour le catalogue unifie (pagination SQL Phase 3).
 * Ne remplace ni job_offers ni external_job_offers.
 */
@Entity
@Table(name = "catalog_listings",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_catalog_origin_ref", columnNames = {"origin", "ref_id"}),
                @UniqueConstraint(name = "uk_catalog_public_id", columnNames = {"public_id"})
        },
        indexes = {
                @Index(name = "idx_catalog_sort", columnList = "sort_at, id"),
                @Index(name = "idx_catalog_country_city", columnList = "country_code, city"),
                @Index(name = "idx_catalog_source", columnList = "source_code"),
                @Index(name = "idx_catalog_dedupe", columnList = "dedupe_key"),
                // Filtres de la recherche candidat (ddl-auto=update crée les index manquants).
                @Index(name = "idx_catalog_origin", columnList = "origin"),
                @Index(name = "idx_catalog_city_fold", columnList = "city_fold"),
                @Index(name = "idx_catalog_contract", columnList = "contract_label"),
                @Index(name = "idx_catalog_work_mode", columnList = "work_mode"),
                @Index(name = "idx_catalog_published", columnList = "published_at"),
                @Index(name = "idx_catalog_company", columnList = "company_name")
        })
public class CatalogListing {

    public static final String ORIGIN_INTERNAL = "INTERNAL";
    public static final String ORIGIN_EXTERNAL = "EXTERNAL";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 16)
    private String origin;

    @Column(name = "ref_id", nullable = false)
    private Long refId;

    @Column(name = "public_id", nullable = false, length = 40)
    private String publicId;

    @Column(name = "source_code", nullable = false, length = 40)
    private String sourceCode;

    @Column(name = "board_key", length = 80)
    private String boardKey;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(name = "company_name", nullable = false, length = 200)
    private String companyName;

    @Column(name = "location_label", length = 200)
    private String locationLabel;

    @Column(length = 120)
    private String city;

    /** Ville repliee (sans accents) pour filtres accent-insensibles. */
    @Column(name = "city_fold", length = 120)
    private String cityFold;

    @Column(name = "country_code", length = 8)
    private String countryCode;

    @Column(name = "contract_label", length = 60)
    private String contractLabel;

    @Column(name = "work_mode", length = 24)
    private String workMode;

    /** Plafond / plancher de rémunération (MAD) pour filtres serveur. */
    @Column(name = "salary_min")
    private Integer salaryMin;

    @Column(name = "salary_max")
    private Integer salaryMax;

    @Column(name = "experience_level", length = 16)
    private String experienceLevel;

    @Column(length = 120)
    private String sector;

    @Column(name = "skills_text", length = 500)
    private String skillsText;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "origin_url", length = 500)
    private String originUrl;

    @Column(name = "apply_url", length = 500)
    private String applyUrl;

    @Column(name = "url_quality", length = 20)
    private String urlQuality;

    @Column(name = "link_reliable", nullable = false)
    private boolean linkReliable;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "published_trusted", nullable = false)
    private boolean publishedTrusted;

    @Column(name = "sort_at", nullable = false)
    private Instant sortAt;

    @Column(name = "dedupe_key", length = 64)
    private String dedupeKey;

    @Column(name = "search_blob", nullable = false, columnDefinition = "TEXT")
    private String searchBlob;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getOrigin() { return origin; }
    public void setOrigin(String origin) { this.origin = origin; }
    public Long getRefId() { return refId; }
    public void setRefId(Long refId) { this.refId = refId; }
    public String getPublicId() { return publicId; }
    public void setPublicId(String publicId) { this.publicId = publicId; }
    public String getSourceCode() { return sourceCode; }
    public void setSourceCode(String sourceCode) { this.sourceCode = sourceCode; }
    public String getBoardKey() { return boardKey; }
    public void setBoardKey(String boardKey) { this.boardKey = boardKey; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getCompanyName() { return companyName; }
    public void setCompanyName(String companyName) { this.companyName = companyName; }
    public String getLocationLabel() { return locationLabel; }
    public void setLocationLabel(String locationLabel) { this.locationLabel = locationLabel; }
    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }
    public String getCityFold() { return cityFold; }
    public void setCityFold(String cityFold) { this.cityFold = cityFold; }
    public String getCountryCode() { return countryCode; }
    public void setCountryCode(String countryCode) { this.countryCode = countryCode; }
    public String getContractLabel() { return contractLabel; }
    public void setContractLabel(String contractLabel) { this.contractLabel = contractLabel; }
    public String getWorkMode() { return workMode; }
    public void setWorkMode(String workMode) { this.workMode = workMode; }
    public Integer getSalaryMin() { return salaryMin; }
    public void setSalaryMin(Integer salaryMin) { this.salaryMin = salaryMin; }
    public Integer getSalaryMax() { return salaryMax; }
    public void setSalaryMax(Integer salaryMax) { this.salaryMax = salaryMax; }
    public String getExperienceLevel() { return experienceLevel; }
    public void setExperienceLevel(String experienceLevel) { this.experienceLevel = experienceLevel; }
    public String getSector() { return sector; }
    public void setSector(String sector) { this.sector = sector; }
    public String getSkillsText() { return skillsText; }
    public void setSkillsText(String skillsText) { this.skillsText = skillsText; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getOriginUrl() { return originUrl; }
    public void setOriginUrl(String originUrl) { this.originUrl = originUrl; }
    public String getApplyUrl() { return applyUrl; }
    public void setApplyUrl(String applyUrl) { this.applyUrl = applyUrl; }
    public String getUrlQuality() { return urlQuality; }
    public void setUrlQuality(String urlQuality) { this.urlQuality = urlQuality; }
    public boolean isLinkReliable() { return linkReliable; }
    public void setLinkReliable(boolean linkReliable) { this.linkReliable = linkReliable; }
    public Instant getPublishedAt() { return publishedAt; }
    public void setPublishedAt(Instant publishedAt) { this.publishedAt = publishedAt; }
    public boolean isPublishedTrusted() { return publishedTrusted; }
    public void setPublishedTrusted(boolean publishedTrusted) { this.publishedTrusted = publishedTrusted; }
    public Instant getSortAt() { return sortAt; }
    public void setSortAt(Instant sortAt) { this.sortAt = sortAt; }
    public String getDedupeKey() { return dedupeKey; }
    public void setDedupeKey(String dedupeKey) { this.dedupeKey = dedupeKey; }
    public String getSearchBlob() { return searchBlob; }
    public void setSearchBlob(String searchBlob) { this.searchBlob = searchBlob; }
}