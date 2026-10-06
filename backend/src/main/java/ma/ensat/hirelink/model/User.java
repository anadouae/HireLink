package ma.ensat.hirelink.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "users", indexes = {
        @Index(name = "idx_users_role", columnList = "role")
})
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 160)
    private String email;

    /** Nullable = compte frictionless / Google sans mot de passe encore. */
    @Column(name = "password_hash", length = 255)
    private String passwordHash;

    @Column(name = "full_name", nullable = false, length = 160)
    private String fullName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private Role role;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "must_set_password", nullable = false)
    private boolean mustSetPassword = false;

    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified = false;

    @Column(name = "google_sub", length = 128, unique = true)
    private String googleSub;

    @Column(length = 500)
    private String skills;

    @Column(length = 120)
    private String company;

    /** ICE Maroc — preuve d'existence légale (recruteurs). */
    @Column(name = "company_ice", length = 32)
    private String companyIce;

    @Column(name = "company_verified", nullable = false)
    private boolean companyVerified = false;

    @Column(name = "profile_complete", nullable = false)
    private boolean profileComplete = false;

    /** Rôles / métiers recherchés (candidat) — ex: Développeur Java, Data */
    @Column(name = "sought_roles", length = 400)
    private String soughtRoles;

    /** Villes / modes préférés — ex: Tétouan, Remote, Hybride */
    @Column(name = "preferred_locations", length = 300)
    private String preferredLocations;

    /** Types de contrat — ex: CDI,Stage,Alternance */
    @Column(name = "preferred_contracts", length = 120)
    private String preferredContracts;

    /** Niveau — Junior / Confirmé / Senior */
    @Column(name = "experience_level", length = 40)
    private String experienceLevel;

    /** Palier CDC §9 : ESSENTIEL / PERFORMANCE (candidat) ou ACCES / PREMIUM (org). */
    @Enumerated(EnumType.STRING)
    @Column(name = "plan_tier", length = 24)
    private PlanTier planTier;

    /** Hiring Manager assigné (R14) — optionnel. */
    @Column(name = "assigned_org_id")
    private Long assignedOrgId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public boolean isMustSetPassword() { return mustSetPassword; }
    public void setMustSetPassword(boolean mustSetPassword) { this.mustSetPassword = mustSetPassword; }

    public boolean isEmailVerified() { return emailVerified; }
    public void setEmailVerified(boolean emailVerified) { this.emailVerified = emailVerified; }

    public String getGoogleSub() { return googleSub; }
    public void setGoogleSub(String googleSub) { this.googleSub = googleSub; }

    public String getSkills() { return skills; }
    public void setSkills(String skills) { this.skills = skills; }

    public String getCompany() { return company; }
    public void setCompany(String company) { this.company = company; }

    public String getCompanyIce() { return companyIce; }
    public void setCompanyIce(String companyIce) { this.companyIce = companyIce; }

    public boolean isCompanyVerified() { return companyVerified; }
    public void setCompanyVerified(boolean companyVerified) { this.companyVerified = companyVerified; }

    public boolean isProfileComplete() { return profileComplete; }
    public void setProfileComplete(boolean profileComplete) { this.profileComplete = profileComplete; }

    public String getSoughtRoles() { return soughtRoles; }
    public void setSoughtRoles(String soughtRoles) { this.soughtRoles = soughtRoles; }

    public String getPreferredLocations() { return preferredLocations; }
    public void setPreferredLocations(String preferredLocations) { this.preferredLocations = preferredLocations; }

    public String getPreferredContracts() { return preferredContracts; }
    public void setPreferredContracts(String preferredContracts) { this.preferredContracts = preferredContracts; }

    public String getExperienceLevel() { return experienceLevel; }
    public void setExperienceLevel(String experienceLevel) { this.experienceLevel = experienceLevel; }

    public PlanTier getPlanTier() { return planTier; }
    public void setPlanTier(PlanTier planTier) { this.planTier = planTier; }

    public Long getAssignedOrgId() { return assignedOrgId; }
    public void setAssignedOrgId(Long assignedOrgId) { this.assignedOrgId = assignedOrgId; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
