package ma.ensat.hirelink.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "matching_analyses")
public class MatchingAnalysis {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "application_id", nullable = false)
    private Long applicationId;
    @Column(nullable = false)
    private double score;
    @Column(name = "covered_skills", length = 1000)
    private String coveredSkills;
    @Column(name = "missing_skills", length = 1000)
    private String missingSkills;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public Long getId() { return id; }
    public void setApplicationId(Long applicationId) { this.applicationId = applicationId; }
    public void setScore(double score) { this.score = score; }
    public void setCoveredSkills(String coveredSkills) { this.coveredSkills = coveredSkills; }
    public void setMissingSkills(String missingSkills) { this.missingSkills = missingSkills; }
    public double getScore() { return score; }
    public String getCoveredSkills() { return coveredSkills; }
    public String getMissingSkills() { return missingSkills; }
    public Instant getCreatedAt() { return createdAt; }
}
