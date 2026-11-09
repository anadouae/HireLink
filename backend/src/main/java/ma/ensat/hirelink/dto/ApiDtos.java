package ma.ensat.hirelink.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import ma.ensat.hirelink.model.ApplicationStatus;
import ma.ensat.hirelink.model.CompensationType;
import ma.ensat.hirelink.model.EducationLevel;
import ma.ensat.hirelink.model.ExperienceLevel;
import ma.ensat.hirelink.model.PayBasis;
import ma.ensat.hirelink.model.PayPeriod;
import ma.ensat.hirelink.model.WorkMode;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public class ApiDtos {

    public record UserProfile(
            Long id,
            String email,
            String fullName,
            String role,
            boolean mustSetPassword,
            boolean emailVerified,
            boolean profileComplete,
            String skills,
            String company,
            String companyIce,
            boolean companyVerified,
            String verificationStatus,
            String soughtRoles,
            String preferredLocations,
            String preferredContracts,
            String experienceLevel,
            Instant createdAt,
            String cvFileName,
            String cvSections,
            String companyPresentation,
            String firstName,
            String lastName,
            String phone,
            String city,
            String nationality,
            LocalDate birthDate,
            String jobTitle,
            String phoneCountry,
            String gender,
            String availability,
            String school,
            String educationDomain,
            Integer graduationYear,
            String workMode,
            boolean emailNotifications
    ) {}

    public record UpdateProfile(
            @Size(max = 120) String fullName,
            @Size(max = 500) String skills,
            @Size(max = 120) String company,
            @Size(max = 20) String companyIce,
            @Size(max = 400) String soughtRoles,
            @Size(max = 300) String preferredLocations,
            @Size(max = 120) String preferredContracts,
            @Size(max = 40) String experienceLevel,
            @Size(max = 800) String companyPresentation,
            @Size(max = 80) String firstName,
            @Size(max = 80) String lastName,
            @Size(max = 30) String phone,
            @Size(max = 8) String phoneCountry,
            @Size(max = 80) String city,
            @Size(max = 60) String nationality,
            @Size(max = 10) String birthDate,
            @Size(max = 160) String school,
            @Size(max = 120) String educationDomain,
            @Min(1950) @Max(2100) Integer graduationYear,
            @Size(max = 40) String workMode,
            @Size(max = 20) String gender,
            @Size(max = 40) String availability,
            Boolean emailNotifications
    ) {}

    public record JobOfferRequest(
            @NotBlank @Size(min = 3, max = 160) String title,
            @NotBlank @Size(max = 10000) String description,
            @Size(max = 500) String requiredSkills,
            @Size(max = 120) String location,
            @NotBlank @Size(max = 40) String contractType,
            /** Suivi interne uniquement : le candidat ne voit plus de délai promis. Absent = valeur en place. */
            Integer responseSlaDays,
            boolean published,
            CompensationType compensationType,
            @Min(0) @Max(10_000_000) Integer salaryMin,
            @Min(0) @Max(10_000_000) Integer salaryMax,
            PayPeriod salaryPeriod,
            PayBasis salaryBasis,
            @Pattern(regexp = "MAD|EUR|USD") String currency,
            @Min(1) @Max(60) Integer durationMonths,
            LocalDate startDate,
            Boolean startAsap,
            Boolean agreementRequired,
            Boolean hiringPossible,
            @Min(0) @Max(12) Integer probationMonths,
            @Size(max = 120) String schedule,
            WorkMode workMode,
            ExperienceLevel experienceLevel,
            EducationLevel educationLevel,
            @Min(1) @Max(500) Integer positions,
            LocalDate deadline,
            @Size(max = 400) String benefits,
            @Size(max = 500) String niceToHaveSkills
    ) {}

    public record JobOfferResponse(
            Long id,
            String title,
            String description,
            String requiredSkills,
            String location,
            String contractType,
            int responseSlaDays,
            boolean published,
            boolean suspended,
            int reportCount,
            String quarantineReason,
            Long recruiterId,
            String recruiterName,
            Instant createdAt,
            Double matchScore,
            String companyName,
            String companyCity,
            boolean companyVerified,
            String companyLogo,
            String companyWebsite,
            String companySector,
            /** Vélocité mesurée de l'entreprise (jamais une promesse) : médiane en jours et distinction. */
            Double responseMedianDays,
            String responsiveness,
            CompensationType compensationType,
            Integer salaryMin,
            Integer salaryMax,
            PayPeriod salaryPeriod,
            PayBasis salaryBasis,
            String currency,
            Integer durationMonths,
            LocalDate startDate,
            Boolean startAsap,
            Boolean agreementRequired,
            Boolean hiringPossible,
            Integer probationMonths,
            String schedule,
            WorkMode workMode,
            ExperienceLevel experienceLevel,
            EducationLevel educationLevel,
            Integer positions,
            LocalDate deadline,
            String benefits,
            /** Compétences demandées que le candidat possède / n'a pas ; vides pour les autres lecteurs. */
            List<String> matchedSkills,
            List<String> missingSkills,
            boolean matchesProfile,
            String niceToHaveSkills,
            /** Détail du score par critère (lecteur candidat uniquement), sinon {@code null}. */
            ma.ensat.hirelink.service.SkillMatchingService.Compatibility matchBreakdown,
            /** Cycle de vie côté entreprise : {@code null} tant que l'offre n'est ni clôturée ni archivée. */
            Instant closedAt,
            Instant archivedAt
    ) {}

    public record ApplyRequest(
            Long jobOfferId,
            @Size(max = 20) String cvSource,
            @Size(max = 160) String cvFileName,
            @Size(max = 5000) String coverLetter,
            @Size(max = 20000) String cvSections,
            /** Disponibilité, préavis, prétentions salariales, mobilité… (JSON, facultatif). */
            @Size(max = 4000) String details
    ) {}

    public record ApplicationResponse(
            Long id,
            Long jobOfferId,
            String jobTitle,
            Long candidateId,
            String candidateName,
            String candidateEmail,
            String candidateSkills,
            ApplicationStatus status,
            Double matchScore,
            String rejectionReason,
            String refusalReasonCode,
            String cvSource,
            String cvFileName,
            String coverLetter,
            String interviewType,
            Instant interviewAt,
            String interviewLocation,
            String interviewNotes,
            String interviewKind,
            Integer interviewDurationMinutes,
            String interviewTimeZone,
            String interviewVideoUrl,
            String interviewMapUrl,
            String interviewParticipants,
            String interviewStatus,
            String interviewCancelReason,
            String advanceNote,
            Long assignedHmUserId,
            Instant submittedAt,
            Instant updatedAt,
            Instant responseDeadline,
            Integer slaDays,
            Long slaRemainingDays,
            boolean slaBreached,
            /** Vélocité mesurée de l'entreprise — ce que le candidat voit, à la place du délai promis. */
            Double responseMedianDays,
            String responsiveness,
            String cvSections,
            boolean hasCvFile,
            /** Détail du score par critère, recalculé sur le profil et l'offre actuels. */
            ma.ensat.hirelink.service.SkillMatchingService.Compatibility matchBreakdown,
            /** Réponses du candidat au moment de postuler (JSON) ; {@code null} pour les anciennes candidatures. */
            String applicationDetails,
            /** Valeur écrite au dépôt. Elle n'est pas recalculée. Elle peut provenir d'un ancien calcul. */
            Double matchScoreAtSubmission,
            /** Même valeur que {@code matchScore} : score actuel, ou {@code null} si le matching n'est pas disponible. */
            Double currentMatchScore,
            boolean matchingAvailable
    ) {}

    /** Vélocité mesurée du recruteur sur ses propres offres ; {@code badge} vide sous l'échantillon minimum. */
    public record PaceSignal(int answered, double medianDays, String badge) {}

    /** Planification ou report d'un entretien. {@code startsAt} est l'heure locale du fuseau {@code timeZone}. */
    public record InterviewRequest(
            @NotBlank @Size(max = 16) String type,
            @NotBlank @Size(max = 16) String kind,
            @NotNull LocalDateTime startsAt,
            @Size(max = 40) String timeZone,
            @Min(10) @Max(480) Integer durationMinutes,
            @Size(max = 255) String location,
            @Size(max = 300) @Pattern(regexp = "^$|https?://\\S+") String mapUrl,
            @Size(max = 300) @Pattern(regexp = "^$|https?://\\S+") String videoUrl,
            @Size(max = 300) String participants,
            @Size(max = 1000) String instructions
    ) {}

    public record CancelInterviewRequest(@NotBlank @Size(max = 300) String reason) {}

    public record AdvanceRequest(
            @NotNull ApplicationStatus status,
            @Size(max = 600) String rejectionReason,
            /** Note d’étape, ou message de refus envoyé au candidat (refus). */
            @Size(max = 4000) String advanceNote,
            Boolean confirmIdentity,
            Boolean confirmCvReviewed,
            Boolean confirmSlaAware
    ) {}

    public record AdminStats(
            long users,
            long candidates,
            long recruiters,
            long offers,
            long applications,
            long publishedOffers
    ) {}
}
