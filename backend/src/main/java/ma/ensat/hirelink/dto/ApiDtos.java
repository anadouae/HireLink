package ma.ensat.hirelink.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import ma.ensat.hirelink.model.ApplicationStatus;

import java.time.Instant;
import java.time.LocalDate;

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
            String workMode
    ) {}

    public record UpdateProfile(
            @jakarta.validation.constraints.Size(max = 120) String fullName,
            @jakarta.validation.constraints.Size(max = 500) String skills,
            @jakarta.validation.constraints.Size(max = 120) String company,
            @jakarta.validation.constraints.Size(max = 20) String companyIce,
            @jakarta.validation.constraints.Size(max = 400) String soughtRoles,
            @jakarta.validation.constraints.Size(max = 300) String preferredLocations,
            @jakarta.validation.constraints.Size(max = 120) String preferredContracts,
            @jakarta.validation.constraints.Size(max = 40) String experienceLevel,
            @jakarta.validation.constraints.Size(max = 800) String companyPresentation,
            @jakarta.validation.constraints.Size(max = 80) String firstName,
            @jakarta.validation.constraints.Size(max = 80) String lastName,
            @jakarta.validation.constraints.Size(max = 30) String phone,
            @jakarta.validation.constraints.Size(max = 8) String phoneCountry,
            @jakarta.validation.constraints.Size(max = 80) String city,
            @jakarta.validation.constraints.Size(max = 60) String nationality,
            @jakarta.validation.constraints.Size(max = 10) String birthDate,
            @jakarta.validation.constraints.Size(max = 160) String school,
            @jakarta.validation.constraints.Size(max = 40) String workMode,
            @jakarta.validation.constraints.Size(max = 20) String gender,
            @jakarta.validation.constraints.Size(max = 40) String availability
    ) {}

    public record JobOfferRequest(
            @NotBlank @jakarta.validation.constraints.Size(max = 160) String title,
            @NotBlank @jakarta.validation.constraints.Size(max = 10000) String description,
            @jakarta.validation.constraints.Size(max = 2000) String requiredSkills,
            @jakarta.validation.constraints.Size(max = 120) String location,
            @jakarta.validation.constraints.Size(max = 40) String contractType,
            @Min(1) int responseSlaDays,
            boolean published
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
            boolean companyVerified,
            String companyLogo,
            String companyWebsite,
            String companySector
    ) {}

    public record ApplyRequest(
            Long jobOfferId,
            @jakarta.validation.constraints.Size(max = 20) String cvSource,
            @jakarta.validation.constraints.Size(max = 160) String cvFileName,
            @jakarta.validation.constraints.Size(max = 5000) String coverLetter,
            @jakarta.validation.constraints.Size(max = 20000) String cvSections
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
            String advanceNote,
            Long assignedHmUserId,
            Instant submittedAt,
            Instant updatedAt,
            Instant responseDeadline,
            Integer slaDays,
            Long slaRemainingDays,
            boolean slaBreached,
            String cvSections,
            boolean hasCvFile
    ) {}

    public record AdvanceRequest(
            @NotNull ApplicationStatus status,
            @jakarta.validation.constraints.Size(max = 600) String rejectionReason,
            @jakarta.validation.constraints.Size(max = 1000) String advanceNote,
            @jakarta.validation.constraints.Size(max = 20) String interviewType,
            Instant interviewAt,
            @jakarta.validation.constraints.Size(max = 300) String interviewLocation,
            @jakarta.validation.constraints.Size(max = 2000) String interviewNotes,
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
