package ma.ensat.hirelink.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "scorecard_entries")
public class ScorecardEntry {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "application_id", nullable = false)
    private Long applicationId;
    @Column(name = "criterion_id", nullable = false)
    private Long criterionId;
    @Column(name = "evaluator_user_id", nullable = false)
    private Long evaluatorUserId;
    @Column(nullable = false)
    private int score;
    @Column(length = 500)
    private String comment;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public Long getId() { return id; }
    public Long getApplicationId() { return applicationId; }
    public void setApplicationId(Long applicationId) { this.applicationId = applicationId; }
    public Long getCriterionId() { return criterionId; }
    public void setCriterionId(Long criterionId) { this.criterionId = criterionId; }
    public Long getEvaluatorUserId() { return evaluatorUserId; }
    public void setEvaluatorUserId(Long evaluatorUserId) { this.evaluatorUserId = evaluatorUserId; }
    public int getScore() { return score; }
    public void setScore(int score) { this.score = score; }
    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }
    public Instant getCreatedAt() { return createdAt; }
}
