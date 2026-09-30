package ma.ensat.hirelink.model;

import jakarta.persistence.*;

@Entity
@Table(name = "scorecard_criteria")
public class ScorecardCriterion {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "job_offer_id", nullable = false)
    private Long jobOfferId;
    @Column(nullable = false, length = 120)
    private String label;
    @Column(nullable = false)
    private int weight = 1;
    @Column(nullable = false)
    private boolean veto = false;

    public Long getId() { return id; }
    public Long getJobOfferId() { return jobOfferId; }
    public void setJobOfferId(Long jobOfferId) { this.jobOfferId = jobOfferId; }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public int getWeight() { return weight; }
    public void setWeight(int weight) { this.weight = weight; }
    public boolean isVeto() { return veto; }
    public void setVeto(boolean veto) { this.veto = veto; }
}
