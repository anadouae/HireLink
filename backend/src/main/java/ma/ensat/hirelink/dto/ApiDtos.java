package ma.ensat.hirelink.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import ma.ensat.hirelink.model.ApplicationStatus;

import java.time.Instant;

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
            String soughtRoles,
            String preferredLocations,
            String preferredContracts,
            String experienceLevel,
            Instant createdAt
    ) {}

    public record UpdateProfile(
            String fullName,
            String skills,
            String company,
            String companyIce,
            String soughtRoles,
            String preferredLocations,
            String preferredContracts,
            String experienceLevel
    ) {}

    public record JobOfferRequest(
            @NotBlank String title,
            @NotBlank String description,
            String requiredSkills,
            String location,
            String contractType,
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
            Long recruiterId,
            String recruiterName,
            Instant createdAt,
            Double matchScore
    ) {}

    public record ApplyRequest(
            Long jobOfferId,
            String cvSource,
            String cvFileName,
            String coverLetter,
            String photoFileName
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
            String cvSource,
            String cvFileName,
            String coverLetter,
            String photoFileName,
            String interviewType,
            Instant interviewAt,
            String interviewLocation,
            String interviewNotes,
            String advanceNote,
            Instant submittedAt,
            Instant updatedAt,
            Integer slaDays,
            Long slaRemainingDays
    ) {}

    public record AdvanceRequest(
            @NotNull ApplicationStatus status,
            String rejectionReason,
            String advanceNote,
            String interviewType,
            Instant interviewAt,
            String interviewLocation,
            String interviewNotes,
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
