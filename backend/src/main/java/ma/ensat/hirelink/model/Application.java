package ma.ensat.hirelink.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "applications",
       uniqueConstraints = @UniqueConstraint(columnNames = {"candidate_id", "job_offer_id"}),
       indexes = {
           @Index(name = "idx_app_status", columnList = "status"),
           @Index(name = "idx_app_submitted", columnList = "submitted_at"),
           @Index(name = "idx_app_candidate", columnList = "candidate_id"),
           @Index(name = "idx_app_hm", columnList = "assigned_hm_user_id"),
           @Index(name = "idx_app_deadline", columnList = "response_deadline")
       })
public class Application {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "candidate_id", nullable = false)
    private User candidate;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "job_offer_id", nullable = false)
    private JobOffer jobOffer;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ApplicationStatus status = ApplicationStatus.SUBMITTED;

    /** Score d'appariement local (0–100), sans API tierce. */
    @Column(name = "match_score")
    private Double matchScore;

    /** Motif de refus obligatoire si status = REJECTED. */
    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    /** PROFILE = compétences du profil · UPLOAD = nouveau CV joint. */
    @Column(name = "cv_source", length = 24)
    private String cvSource;

    @Column(name = "cv_file_name", length = 255)
    private String cvFileName;

    @Column(name = "cv_storage_name", length = 255)
    private String cvStorageName;

    /** JSON des sections confirmées ou lues sur le fichier joint. */
    @Column(name = "cv_sections", columnDefinition = "TEXT")
    private String cvSections;

    @Column(name = "cover_letter", columnDefinition = "TEXT")
    private String coverLetter;

    /** PHONE | VISIO | ONSITE | ASYNC */
    @Column(name = "interview_type", length = 32)
    private String interviewType;

    @Column(name = "interview_at")
    private Instant interviewAt;

    @Column(name = "interview_location", length = 255)
    private String interviewLocation;

    @Column(name = "interview_notes", length = 1000)
    private String interviewNotes;

    /** Note interne RH lors d’une avancée d’étape */
    @Column(name = "advance_note", length = 1000)
    private String advanceNote;

    /** Code catalogue refus (R04) — ex. PROFILE_MISMATCH */
    @Column(name = "refusal_reason_code", length = 64)
    private String refusalReasonCode;

    /** Hiring Manager assigné sur ce dossier (R14). */
    @Column(name = "assigned_hm_user_id")
    private Long assignedHmUserId;

    /** Deadline SLA calculée à la soumission (submitted_at + response_sla_days). */
    @Column(name = "response_deadline")
    private Instant responseDeadline;

    /** Première décision explicite (ACCEPT / REJECT) — mesure fiabilité. */
    @Column(name = "first_decision_at")
    private Instant firstDecisionAt;

    /** Évite le spam de notifications SLA (une alerte max). */
    @Column(name = "sla_breach_notified", nullable = false)
    private boolean slaBreachNotified = false;

    @Column(name = "submitted_at", nullable = false, updatable = false)
    private Instant submittedAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @PreUpdate
    public void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public User getCandidate() { return candidate; }
    public void setCandidate(User candidate) { this.candidate = candidate; }

    public JobOffer getJobOffer() { return jobOffer; }
    public void setJobOffer(JobOffer jobOffer) { this.jobOffer = jobOffer; }

    public ApplicationStatus getStatus() { return status; }
    public void setStatus(ApplicationStatus status) { this.status = status; }

    public Double getMatchScore() { return matchScore; }
    public void setMatchScore(Double matchScore) { this.matchScore = matchScore; }

    public String getRejectionReason() { return rejectionReason; }
    public void setRejectionReason(String rejectionReason) { this.rejectionReason = rejectionReason; }

    public String getCvSource() { return cvSource; }
    public void setCvSource(String cvSource) { this.cvSource = cvSource; }

    public String getCvFileName() { return cvFileName; }
    public void setCvFileName(String cvFileName) { this.cvFileName = cvFileName; }

    public String getCvStorageName() { return cvStorageName; }
    public void setCvStorageName(String cvStorageName) { this.cvStorageName = cvStorageName; }

    public String getCvSections() { return cvSections; }
    public void setCvSections(String cvSections) { this.cvSections = cvSections; }

    public String getCoverLetter() { return coverLetter; }
    public void setCoverLetter(String coverLetter) { this.coverLetter = coverLetter; }


    public String getInterviewType() { return interviewType; }
    public void setInterviewType(String interviewType) { this.interviewType = interviewType; }

    public Instant getInterviewAt() { return interviewAt; }
    public void setInterviewAt(Instant interviewAt) { this.interviewAt = interviewAt; }

    public String getInterviewLocation() { return interviewLocation; }
    public void setInterviewLocation(String interviewLocation) { this.interviewLocation = interviewLocation; }

    public String getInterviewNotes() { return interviewNotes; }
    public void setInterviewNotes(String interviewNotes) { this.interviewNotes = interviewNotes; }

    public String getAdvanceNote() { return advanceNote; }
    public void setAdvanceNote(String advanceNote) { this.advanceNote = advanceNote; }

    public String getRefusalReasonCode() { return refusalReasonCode; }
    public void setRefusalReasonCode(String refusalReasonCode) { this.refusalReasonCode = refusalReasonCode; }

    public Long getAssignedHmUserId() { return assignedHmUserId; }
    public void setAssignedHmUserId(Long assignedHmUserId) { this.assignedHmUserId = assignedHmUserId; }

    public Instant getResponseDeadline() { return responseDeadline; }
    public void setResponseDeadline(Instant responseDeadline) { this.responseDeadline = responseDeadline; }

    public Instant getFirstDecisionAt() { return firstDecisionAt; }
    public void setFirstDecisionAt(Instant firstDecisionAt) { this.firstDecisionAt = firstDecisionAt; }

    public boolean isSlaBreachNotified() { return slaBreachNotified; }
    public void setSlaBreachNotified(boolean slaBreachNotified) { this.slaBreachNotified = slaBreachNotified; }

    public Instant getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(Instant submittedAt) { this.submittedAt = submittedAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
