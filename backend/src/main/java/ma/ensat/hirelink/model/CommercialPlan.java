package ma.ensat.hirelink.model;

import jakarta.persistence.*;

@Entity
@Table(name = "commercial_plans")
public class CommercialPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 32)
    private String code;

    @Column(nullable = false, length = 80)
    private String label;

    /** RECRUTEUR ou CANDIDAT */
    @Column(nullable = false, length = 24)
    private String audience;

    @Column(name = "monthly_mad", nullable = false)
    private int monthlyMad;

    @Column(name = "yearly_mad", nullable = false)
    private int yearlyMad;

    @Column(nullable = false)
    private int credits;

    @Column(columnDefinition = "TEXT")
    private String features;

    public Long getId() { return id; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public String getAudience() { return audience; }
    public void setAudience(String audience) { this.audience = audience; }
    public int getMonthlyMad() { return monthlyMad; }
    public void setMonthlyMad(int monthlyMad) { this.monthlyMad = monthlyMad; }
    public int getYearlyMad() { return yearlyMad; }
    public void setYearlyMad(int yearlyMad) { this.yearlyMad = yearlyMad; }
    public int getCredits() { return credits; }
    public void setCredits(int credits) { this.credits = credits; }
    public String getFeatures() { return features; }
    public void setFeatures(String features) { this.features = features; }
}
