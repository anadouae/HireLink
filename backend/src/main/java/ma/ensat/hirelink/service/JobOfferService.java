package ma.ensat.hirelink.service;

import ma.ensat.hirelink.dto.ApiDtos;
import ma.ensat.hirelink.model.JobOffer;
import ma.ensat.hirelink.model.Role;
import ma.ensat.hirelink.model.User;
import ma.ensat.hirelink.repository.JobOfferRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

@Service
public class JobOfferService {

    private final JobOfferRepository jobOfferRepository;
    private final SkillMatchingService matchingService;

    public JobOfferService(JobOfferRepository jobOfferRepository, SkillMatchingService matchingService) {
        this.jobOfferRepository = jobOfferRepository;
        this.matchingService = matchingService;
    }

    @Transactional(readOnly = true)
    public List<ApiDtos.JobOfferResponse> listPublic(User viewer) {
        Stream<ApiDtos.JobOfferResponse> stream = jobOfferRepository.findByPublishedTrue().stream()
                .map(o -> toDto(o, viewer));
        if (viewer != null && viewer.getRole() == Role.CANDIDAT) {
            return stream
                    .sorted(Comparator.comparingDouble((ApiDtos.JobOfferResponse o) ->
                            o.matchScore() != null ? o.matchScore() : 0.0).reversed())
                    .toList();
        }
        return stream.toList();
    }

    @Transactional(readOnly = true)
    public List<ApiDtos.JobOfferResponse> listMine(User recruiter) {
        return jobOfferRepository.findByRecruiterId(recruiter.getId()).stream()
                .map(o -> toDto(o, null))
                .toList();
    }

    @Transactional(readOnly = true)
    public ApiDtos.JobOfferResponse get(Long id, User viewer) {
        JobOffer o = jobOfferRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Offre introuvable"));
        boolean owner = viewer != null && viewer.getId().equals(o.getRecruiter().getId());
        boolean admin = viewer != null && viewer.getRole() == Role.ADMINISTRATEUR;
        if (!o.isPublished() && !owner && !admin) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Offre non publique");
        }
        return toDto(o, viewer);
    }

    @Transactional
    public ApiDtos.JobOfferResponse create(User recruiter, ApiDtos.JobOfferRequest req) {
        requireRecruiter(recruiter);
        if (req.published() && !recruiter.isCompanyVerified()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Entreprise en cours de vérification — publication impossible jusqu'à validation");
        }
        if (req.responseSlaDays() < 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "SLA ≥ 1 jour requis");
        }
        JobOffer o = new JobOffer();
        o.setRecruiter(recruiter);
        apply(o, req);
        jobOfferRepository.save(o);
        return toDto(o, null);
    }

    @Transactional
    public ApiDtos.JobOfferResponse update(User recruiter, Long id, ApiDtos.JobOfferRequest req) {
        JobOffer o = jobOfferRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Offre introuvable"));
        if (!o.getRecruiter().getId().equals(recruiter.getId()) && recruiter.getRole() != Role.ADMINISTRATEUR) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Pas propriétaire");
        }
        if (req.published() && !recruiter.isCompanyVerified() && recruiter.getRole() != Role.ADMINISTRATEUR) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Entreprise en cours de vérification — publication impossible jusqu'à validation");
        }
        apply(o, req);
        jobOfferRepository.save(o);
        return toDto(o, null);
    }

    private void apply(JobOffer o, ApiDtos.JobOfferRequest req) {
        o.setTitle(req.title().trim());
        o.setDescription(req.description().trim());
        o.setRequiredSkills(req.requiredSkills());
        o.setLocation(req.location());
        o.setContractType(req.contractType());
        o.setResponseSlaDays(req.responseSlaDays());
        o.setPublished(req.published());
    }

    private void requireRecruiter(User u) {
        if (u.getRole() != Role.RECRUTEUR && u.getRole() != Role.ADMINISTRATEUR) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Réservé aux recruteurs");
        }
    }

    private ApiDtos.JobOfferResponse toDto(JobOffer o, User viewer) {
        Double match = null;
        if (viewer != null && viewer.getRole() == Role.CANDIDAT) {
            double base = matchingService.computeMatchScore(viewer.getSkills(), o.getRequiredSkills());
            double boost = preferenceBoost(viewer, o);
            match = Math.min(100.0, Math.round((base + boost) * 100.0) / 100.0);
        }
        return new ApiDtos.JobOfferResponse(
                o.getId(),
                o.getTitle(),
                o.getDescription(),
                o.getRequiredSkills(),
                o.getLocation(),
                o.getContractType(),
                o.getResponseSlaDays(),
                o.isPublished(),
                o.getRecruiter().getId(),
                o.getRecruiter().getFullName(),
                o.getCreatedAt(),
                match
        );
    }

    /** Bonus local (sans API) si lieu / contrat / titre collent aux préférences candidat. */
    private double preferenceBoost(User viewer, JobOffer o) {
        double boost = 0;
        String hayTitle = safe(o.getTitle()) + " " + safe(o.getDescription());
        if (tokenOverlap(viewer.getSoughtRoles(), hayTitle)) {
            boost += 8;
        }
        if (tokenOverlap(viewer.getPreferredLocations(), safe(o.getLocation()))) {
            boost += 6;
        }
        if (tokenOverlap(viewer.getPreferredContracts(), safe(o.getContractType()))) {
            boost += 6;
        }
        return boost;
    }

    private boolean tokenOverlap(String csv, String haystack) {
        if (csv == null || csv.isBlank() || haystack == null || haystack.isBlank()) {
            return false;
        }
        String hay = haystack.toLowerCase(Locale.ROOT);
        return Arrays.stream(csv.split("[,;|/]+"))
                .map(String::trim)
                .filter(s -> s.length() >= 2)
                .map(s -> s.toLowerCase(Locale.ROOT))
                .anyMatch(hay::contains);
    }

    private String safe(String s) {
        return s == null ? "" : s;
    }
}
