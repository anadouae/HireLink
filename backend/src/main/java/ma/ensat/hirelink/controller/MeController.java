package ma.ensat.hirelink.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import ma.ensat.hirelink.dto.ApiDtos;
import ma.ensat.hirelink.model.Role;
import ma.ensat.hirelink.model.User;
import ma.ensat.hirelink.repository.UserRepository;
import ma.ensat.hirelink.service.CompanyVerificationService;
import ma.ensat.hirelink.service.CvStorage;
import ma.ensat.hirelink.service.OrganizationService;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/me")
public class MeController {

    private final UserRepository userRepository;
    private final CompanyVerificationService companyVerificationService;
    private final OrganizationService organizationService;
    private final CvStorage cvStorage;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public MeController(
            UserRepository userRepository,
            CompanyVerificationService companyVerificationService,
            OrganizationService organizationService,
            CvStorage cvStorage
    ) {
        this.userRepository = userRepository;
        this.companyVerificationService = companyVerificationService;
        this.organizationService = organizationService;
        this.cvStorage = cvStorage;
    }

    /** Le candidat confirme les sections. Elles entrent dans le profil seulement ici. */
    @PostMapping(value = "/cv", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiDtos.UserProfile saveCv(
            @AuthenticationPrincipal User user,
            @RequestPart(value = "file", required = false) MultipartFile file,
            @RequestParam(value = "sections", required = false) String sections
    ) {
        if (user.getRole() != Role.CANDIDAT) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Réservé aux candidats");
        }
        String json = normalizeSections(sections);
        boolean hasFile = file != null && !file.isEmpty();
        if (!hasFile && (user.getCvStorageName() == null || user.getCvStorageName().isBlank())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choisissez un CV à confirmer");
        }
        try {
            if (hasFile) {
                String stored = cvStorage.save("profiles", file.getOriginalFilename(), file.getBytes());
                user.setCvStorageName(stored);
                user.setCvFileName(file.getOriginalFilename());
            }
            if (json != null) {
                user.setCvSections(json);
                String skillLine = skillLine(json);
                if (!skillLine.isBlank()) {
                    user.setSkills(mergeSkills(user.getSkills(), skillLine));
                }
            }
            boolean complete = user.getFullName() != null && !user.getFullName().isBlank()
                    && user.getSkills() != null && !user.getSkills().isBlank()
                    && user.getSoughtRoles() != null && !user.getSoughtRoles().isBlank();
            user.setProfileComplete(complete);
            userRepository.save(user);
            return toProfile(user);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Enregistrement du CV impossible");
        }
    }

    @GetMapping("/cv")
    public ResponseEntity<Resource> downloadCv(@AuthenticationPrincipal User user) {
        if (user.getCvStorageName() == null || user.getCvStorageName().isBlank()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Aucun CV enregistré");
        }
        Resource resource = cvStorage.load(user.getCvStorageName());
        if (resource == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Fichier CV introuvable");
        }
        String name = user.getCvFileName() == null ? "cv" : user.getCvFileName();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + name.replace("\"", "") + "\"")
                .body(resource);
    }

    @GetMapping
    public ApiDtos.UserProfile me(@AuthenticationPrincipal User user) {
        return toProfile(user);
    }

    @PatchMapping
    public ApiDtos.UserProfile update(
            @AuthenticationPrincipal User user,
            @RequestBody ApiDtos.UpdateProfile body
    ) {
        if (body.fullName() != null && !body.fullName().isBlank()) {
            user.setFullName(body.fullName().trim());
        }
        if (body.skills() != null) {
            user.setSkills(body.skills().trim());
        }
        if (body.company() != null) {
            user.setCompany(body.company().trim());
        }
        if (body.soughtRoles() != null) {
            user.setSoughtRoles(body.soughtRoles().trim());
        }
        if (body.preferredLocations() != null) {
            user.setPreferredLocations(body.preferredLocations().trim());
        }
        if (body.preferredContracts() != null) {
            user.setPreferredContracts(body.preferredContracts().trim());
        }
        if (body.experienceLevel() != null) {
            user.setExperienceLevel(body.experienceLevel().trim());
        }
        if (body.companyPresentation() != null) {
            String bio = body.companyPresentation().trim();
            user.setCompanyPresentation(bio.length() > 800 ? bio.substring(0, 800) : bio);
        }
        if (body.firstName() != null && !body.firstName().isBlank()) user.setFirstName(body.firstName().trim());
        if (body.lastName() != null && !body.lastName().isBlank()) user.setLastName(body.lastName().trim());
        if (user.getFirstName() != null && user.getLastName() != null
                && !user.getFirstName().isBlank() && !user.getLastName().isBlank()) {
            user.setFullName(user.getFirstName().trim() + " " + user.getLastName().trim());
        }
        if (body.phone() != null) user.setPhone(body.phone().trim());
        if (body.phoneCountry() != null) user.setPhoneCountry(body.phoneCountry().trim());
        if (body.city() != null && !body.city().isBlank()) {
            user.setCity(body.city().trim());
            user.setPreferredLocations(body.city().trim());
        }
        if (body.nationality() != null) user.setNationality(body.nationality().trim());
        if (body.birthDate() != null && !body.birthDate().isBlank()) {
            try {
                user.setBirthDate(java.time.LocalDate.parse(body.birthDate().trim()));
            } catch (java.time.format.DateTimeParseException ignored) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Date de naissance invalide");
            }
        }
        if (body.school() != null) user.setSchool(body.school().trim());
        if (body.workMode() != null) user.setWorkMode(body.workMode().trim());
        if (body.gender() != null) user.setGender(body.gender().trim());
        if (body.availability() != null) user.setAvailability(body.availability().trim());
        if (user.getRole() == Role.RECRUTEUR && body.companyIce() != null && !body.companyIce().isBlank()) {
            var v = companyVerificationService.verify(
                    body.companyIce(),
                    body.company() != null ? body.company() : user.getCompany(),
                    user.getId(),
                    user.getEmail()
            );
            companyVerificationService.apply(user, v);
        }
        boolean wasComplete = user.isProfileComplete();
        boolean complete = user.getFullName() != null && !user.getFullName().isBlank();
        if (user.getRole() == Role.CANDIDAT) {
            complete = complete
                    && user.getSkills() != null && !user.getSkills().isBlank()
                    && user.getSoughtRoles() != null && !user.getSoughtRoles().isBlank();
        }
        if (user.getRole() == Role.RECRUTEUR) {
            // Profil « complet » = infos renseignées ; badge vérifié = droit de publier (séparé).
            complete = complete && user.getCompanyIce() != null
                    && user.getCompany() != null && !user.getCompany().isBlank();
        }
        user.setProfileComplete(wasComplete || complete);
        userRepository.save(user);
        if (user.getRole() == Role.RECRUTEUR) {
            organizationService.syncFromRecruiter(user);
        }
        return toProfile(user);
    }

    private ApiDtos.UserProfile toProfile(User u) {
        String vs = u.getVerificationStatus();
        if (vs == null || vs.isBlank()) {
            vs = u.isCompanyVerified() ? "REGISTRY_MATCH" : "PENDING";
        }
        return new ApiDtos.UserProfile(
                u.getId(),
                u.getEmail(),
                u.getFullName(),
                u.getRole().name(),
                u.isMustSetPassword(),
                u.isEmailVerified(),
                u.isProfileComplete(),
                u.getSkills(),
                u.getCompany(),
                u.getCompanyIce(),
                u.isCompanyVerified(),
                vs,
                u.getSoughtRoles(),
                u.getPreferredLocations(),
                u.getPreferredContracts(),
                u.getExperienceLevel(),
                u.getCreatedAt(),
                u.getCvFileName(),
                u.getCvSections(),
                u.getCompanyPresentation(),
                u.getFirstName(),
                u.getLastName(),
                u.getPhone(),
                u.getCity(),
                u.getNationality(),
                u.getBirthDate(),
                u.getJobTitle(),
                u.getPhoneCountry(),
                u.getGender(),
                u.getAvailability(),
                u.getSchool(),
                u.getWorkMode()
        );
    }

    private String normalizeSections(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String trimmed = raw.trim();
        if (!trimmed.startsWith("{")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Sections illisibles");
        }
        return trimmed.length() > 8000 ? trimmed.substring(0, 8000) : trimmed;
    }

    private String skillLine(String json) {
        try {
            var skills = new com.fasterxml.jackson.databind.ObjectMapper().readTree(json).get("skills");
            return skills == null || skills.isNull() ? "" : skills.asText("");
        } catch (Exception e) {
            return "";
        }
    }

    private String mergeSkills(String current, String extra) {
        java.util.LinkedHashSet<String> set = new java.util.LinkedHashSet<>();
        for (String part : (current == null ? "" : current).split("[,;]")) {
            String item = part.trim();
            if (!item.isEmpty()) set.add(item);
        }
        for (String part : extra.split("[,;]")) {
            String item = part.trim();
            if (!item.isEmpty()) set.add(item);
        }
        return String.join(", ", set);
    }
}
