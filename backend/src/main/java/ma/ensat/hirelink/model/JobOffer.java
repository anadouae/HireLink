package ma.ensat.hirelink.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Entity
@Table(name = "job_offers", indexes = {
        @Index(name = "idx_offer_published", columnList = "published"),
        @Index(name = "idx_offer_recruiter", columnList = "recruiter_id"),
        @Index(name = "idx_offer_created", columnList = "created_at")
})
public class JobOffer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "recruiter_id", nullable = false)
    private User recruiter;

    /** Candidatures liées à cette offre (@OneToMany). */
    @OneToMany(mappedBy = "jobOffer", cascade = CascadeType.ALL, orphanRemoval = false)
    private List<Application> applications = new ArrayList<>();

    /** Tags N–N (@ManyToMany via table job_offer_tags). */
    @ManyToMany
    @JoinTable(
            name = "job_offer_tags",
            joinColumns = @JoinColumn(name = "job_offer_id"),
            inverseJoinColumns = @JoinColumn(name = "tag_id")
    )
    private Set<Tag> tags = new HashSet<>();

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    /** Compétences séparées par virgule (ex: Java,Spring,MySQL). */
    @Column(name = "required_skills", length = 500)
    private String requiredSkills;

    @Column(length = 120)
    private String location;

    @Column(name = "contract_type", length = 60)
    private String contractType;

    /** Délai d'engagement de réponse en jours (règle métier HireLink). */
    @Column(name = "response_sla_days", nullable = false)
    private int responseSlaDays = 7;

    @Column(nullable = false)
    private boolean published = false;

    /** Suspendue après signalements communautaires (≥ 3). */
    @Column(nullable = false)
    private boolean suspended = false;

    @Column(name = "report_count", nullable = false)
    private int reportCount = 0;

    /** Motif quarantaine douce (scan soft) ou suspension. */
    @Column(name = "quarantine_reason", length = 500)
    private String quarantineReason;

    /** Rémunération annoncée ; {@code null} pour une offre créée avant ce champ. */
    @Enumerated(EnumType.STRING)
    @Column(name = "compensation_type", length = 24)
    private CompensationType compensationType;

    /** Montant minimum (ou montant unique), dans la devise et la période ci-dessous. */
    @Column(name = "salary_min")
    private Integer salaryMin;

    /** Montant maximum (fourchette) ; {@code null} si montant unique. */
    @Column(name = "salary_max")
    private Integer salaryMax;

    @Enumerated(EnumType.STRING)
    @Column(name = "salary_period", length = 12)
    private PayPeriod salaryPeriod;

    @Enumerated(EnumType.STRING)
    @Column(name = "salary_basis", length = 12)
    private PayBasis salaryBasis;

    /** Code ISO de la devise (MAD par défaut). */
    @Column(name = "currency", length = 3)
    private String currency;

    /** Durée en mois (stage, CDD, alternance, mission freelance). */
    @Column(name = "duration_months")
    private Integer durationMonths;

    @Column(name = "start_date")
    private LocalDate startDate;

    /** Démarrage dès que possible (sans date précise). */
    @Column(name = "start_asap")
    private Boolean startAsap;

    /** Convention de stage signée par l'établissement exigée. */
    @Column(name = "agreement_required")
    private Boolean agreementRequired;

    /** Possibilité d'embauche à l'issue du stage. */
    @Column(name = "hiring_possible")
    private Boolean hiringPossible;

    /** Période d'essai en mois (CDI, CDD). */
    @Column(name = "probation_months")
    private Integer probationMonths;

    /** Rythme ou volume horaire (alternance : « 3 j entreprise / 2 j école », temps partiel : « 20 h / semaine »). */
    @Column(name = "schedule", length = 120)
    private String schedule;

    @Enumerated(EnumType.STRING)
    @Column(name = "work_mode", length = 12)
    private WorkMode workMode;

    @Enumerated(EnumType.STRING)
    @Column(name = "experience_level", length = 16)
    private ExperienceLevel experienceLevel;

    @Enumerated(EnumType.STRING)
    @Column(name = "education_level", length = 16)
    private EducationLevel educationLevel;

    /** Nombre de postes à pourvoir. */
    @Column(name = "positions")
    private Integer positions;

    /** Date limite de candidature. */
    @Column(name = "deadline")
    private LocalDate deadline;

    /** Avantages séparés par des virgules (transport, mutuelle, tickets restaurant…). */
    @Column(name = "benefits", length = 400)
    private String benefits;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public User getRecruiter() { return recruiter; }
    public void setRecruiter(User recruiter) { this.recruiter = recruiter; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getRequiredSkills() { return requiredSkills; }
    public void setRequiredSkills(String requiredSkills) { this.requiredSkills = requiredSkills; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    public String getContractType() { return contractType; }
    public void setContractType(String contractType) { this.contractType = contractType; }

    public int getResponseSlaDays() { return responseSlaDays; }
    public void setResponseSlaDays(int responseSlaDays) { this.responseSlaDays = responseSlaDays; }

    public boolean isPublished() { return published; }
    public void setPublished(boolean published) { this.published = published; }

    public boolean isSuspended() { return suspended; }
    public void setSuspended(boolean suspended) { this.suspended = suspended; }

    public int getReportCount() { return reportCount; }
    public void setReportCount(int reportCount) { this.reportCount = reportCount; }

    public String getQuarantineReason() { return quarantineReason; }
    public void setQuarantineReason(String quarantineReason) { this.quarantineReason = quarantineReason; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public CompensationType getCompensationType() { return compensationType; }
    public void setCompensationType(CompensationType compensationType) { this.compensationType = compensationType; }

    public Integer getSalaryMin() { return salaryMin; }
    public void setSalaryMin(Integer salaryMin) { this.salaryMin = salaryMin; }

    public Integer getSalaryMax() { return salaryMax; }
    public void setSalaryMax(Integer salaryMax) { this.salaryMax = salaryMax; }

    public PayPeriod getSalaryPeriod() { return salaryPeriod; }
    public void setSalaryPeriod(PayPeriod salaryPeriod) { this.salaryPeriod = salaryPeriod; }

    public PayBasis getSalaryBasis() { return salaryBasis; }
    public void setSalaryBasis(PayBasis salaryBasis) { this.salaryBasis = salaryBasis; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public Integer getDurationMonths() { return durationMonths; }
    public void setDurationMonths(Integer durationMonths) { this.durationMonths = durationMonths; }

    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }

    public Boolean getStartAsap() { return startAsap; }
    public void setStartAsap(Boolean startAsap) { this.startAsap = startAsap; }

    public Boolean getAgreementRequired() { return agreementRequired; }
    public void setAgreementRequired(Boolean agreementRequired) { this.agreementRequired = agreementRequired; }

    public Boolean getHiringPossible() { return hiringPossible; }
    public void setHiringPossible(Boolean hiringPossible) { this.hiringPossible = hiringPossible; }

    public Integer getProbationMonths() { return probationMonths; }
    public void setProbationMonths(Integer probationMonths) { this.probationMonths = probationMonths; }

    public String getSchedule() { return schedule; }
    public void setSchedule(String schedule) { this.schedule = schedule; }

    public WorkMode getWorkMode() { return workMode; }
    public void setWorkMode(WorkMode workMode) { this.workMode = workMode; }

    public ExperienceLevel getExperienceLevel() { return experienceLevel; }
    public void setExperienceLevel(ExperienceLevel experienceLevel) { this.experienceLevel = experienceLevel; }

    public EducationLevel getEducationLevel() { return educationLevel; }
    public void setEducationLevel(EducationLevel educationLevel) { this.educationLevel = educationLevel; }

    public Integer getPositions() { return positions; }
    public void setPositions(Integer positions) { this.positions = positions; }

    public LocalDate getDeadline() { return deadline; }
    public void setDeadline(LocalDate deadline) { this.deadline = deadline; }

    public String getBenefits() { return benefits; }
    public void setBenefits(String benefits) { this.benefits = benefits; }

    public List<Application> getApplications() { return applications; }
    public void setApplications(List<Application> applications) { this.applications = applications; }

    public Set<Tag> getTags() { return tags; }
    public void setTags(Set<Tag> tags) { this.tags = tags; }
}
