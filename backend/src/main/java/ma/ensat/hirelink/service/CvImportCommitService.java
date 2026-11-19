package ma.ensat.hirelink.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import ma.ensat.hirelink.model.Role;
import ma.ensat.hirelink.model.User;
import ma.ensat.hirelink.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

/**
 * Commits the CV file, extracted sections, profile choices and career data as one unit.
 */
@Service
public class CvImportCommitService {

    private static final int MAX_ITEMS = 100;
    private final UserRepository users;
    private final CandidateCareerService careerService;
    private final CvStorage cvStorage;
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper = new ObjectMapper();

    public CvImportCommitService(
            UserRepository users,
            CandidateCareerService careerService,
            CvStorage cvStorage,
            JdbcTemplate jdbc
    ) {
        this.users = users;
        this.careerService = careerService;
        this.cvStorage = cvStorage;
        this.jdbc = jdbc;
    }

    @Transactional
    public Map<String, Object> commit(User user, MultipartFile file, String payload) {
        requireCandidate(user);
        JsonNode root = readPayload(payload);
        JsonNode profile = root.path("profile");
        JsonNode career = root.path("career");
        JsonNode sections = root.path("sections");
        JsonNode items = root.path("items");
        validateItems(items);
        String sectionsJson = sections.isObject() ? sections.toString() : null;

        String newStorage = null;
        try {
            ObjectNode snapshot = snapshot(user);
            jdbc.update("""
                    INSERT INTO profile_versions (user_id, reason, snapshot_json, expires_at)
                    VALUES (?, 'CV_IMPORT', ?, DATE_ADD(CURRENT_TIMESTAMP(6), INTERVAL 30 DAY))
                    """, user.getId(), snapshot.toString());

            if (file != null && !file.isEmpty()) {
                newStorage = cvStorage.save("profiles", file.getOriginalFilename(), file.getBytes());
                user.setCvStorageName(newStorage);
                user.setCvFileName(FileSignature.safeName(file.getOriginalFilename()));
            } else if (user.getCvStorageName() == null || user.getCvStorageName().isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choisissez un CV à enregistrer");
            }

            applyProfile(user, profile);
            if (sectionsJson != null) user.setCvSections(sectionsJson);
            if (career.isObject()) careerService.save(user, mapper.convertValue(career, Map.class));
            boolean complete = user.getFullName() != null && !user.getFullName().isBlank()
                    && user.getSkills() != null && !user.getSkills().isBlank()
                    && user.getSoughtRoles() != null && !user.getSoughtRoles().isBlank();
            user.setProfileComplete(complete);
            users.save(user);
            replaceItems(user, root.path("draftId"), items);
            return Map.of("saved", true, "profileVersionCreated", true);
        } catch (ResponseStatusException e) {
            deleteNewFile(newStorage);
            throw e;
        } catch (Exception e) {
            deleteNewFile(newStorage);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Enregistrement unique du CV impossible");
        }
    }

    private void applyProfile(User user, JsonNode p) {
        if (!p.isObject()) return;
        set(user::setFirstName, p, "firstName");
        set(user::setLastName, p, "lastName");
        set(user::setPhone, p, "phone");
        set(user::setCity, p, "city");
        set(user::setCountry, p, "country");
        set(user::setJobTitle, p, "jobTitle");
        set(user::setCompanyPresentation, p, "companyPresentation");
        set(user::setAvailability, p, "availability");
        set(user::setWorkMode, p, "workMode");
        set(user::setSkills, p, "skills");
        set(user::setSoughtRoles, p, "soughtRoles");
        set(user::setPreferredLocations, p, "preferredLocations");
        set(user::setPreferredContracts, p, "preferredContracts");
        set(user::setExperienceLevel, p, "experienceLevel");
        set(user::setNationality, p, "nationality");
        set(user::setGender, p, "gender");
        set(user::setSchool, p, "school");
        set(user::setEducationDomain, p, "educationDomain");
        if (p.has("graduationYear")) {
            user.setGraduationYear(p.get("graduationYear").isNull() ? null : p.get("graduationYear").asInt());
        }
        if (p.has("birthDate")) {
            try {
                String value = p.get("birthDate").asText("").trim();
                user.setBirthDate(value.isBlank() ? null : java.time.LocalDate.parse(value));
            } catch (java.time.format.DateTimeParseException e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Date de naissance invalide");
            }
        }
        String first = user.getFirstName() == null ? "" : user.getFirstName().trim();
        String last = user.getLastName() == null ? "" : user.getLastName().trim();
        if (!first.isBlank() && !last.isBlank()) user.setFullName(first + " " + last);
    }

    private void set(java.util.function.Consumer<String> setter, JsonNode node, String key) {
        if (node.has(key) && !node.get(key).isNull()) setter.accept(node.get(key).asText("").trim());
    }

    private void replaceItems(User user, JsonNode draftId, JsonNode items) {
        if (!draftId.canConvertToLong() || !items.isArray()) return;
        long id = draftId.asLong();
        jdbc.update("DELETE FROM cv_import_items WHERE draft_id = ?", id);
        for (JsonNode item : items) {
            jdbc.update("""
                    INSERT INTO cv_import_items
                    (draft_id, section_type, item_key, source_text, target_field,
                     proposed_value_json, current_value_json, state)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    id,
                    text(item, "sectionType", "profile"),
                    text(item, "id", null),
                    text(item, "sourceText", null),
                    text(item, "targetField", null),
                    jsonText(item.get("proposedValue"), item.get("proposed")),
                    jsonText(item.get("currentValue"), item.get("current")),
                    state(item)
            );
        }
        jdbc.update("UPDATE cv_import_drafts SET status = 'COMMITTED', updated_at = CURRENT_TIMESTAMP(6) WHERE id = ? AND user_id = ?", id, user.getId());
    }

    private void validateItems(JsonNode items) {
        if (!items.isMissingNode() && !items.isArray()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Éléments d’import invalides");
        }
        if (items.isArray() && items.size() > MAX_ITEMS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Trop d’éléments d’import");
        }
        if (items.isArray()) {
            for (JsonNode item : items) {
                String state = text(item, "status", "DETECTED").toUpperCase();
                if (!java.util.Set.of("DETECTED", "ACCEPTED", "MODIFIED", "REFUSED", "MISSING", "VERIFY").contains(state)) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "État d’import invalide");
                }
            }
        }
    }

    private String state(JsonNode item) {
        String value = text(item, "status", "DETECTED").toUpperCase();
        return "VERIFY".equals(value) ? "VERIFY" : value;
    }

    private String text(JsonNode node, String key, String fallback) {
        JsonNode value = node.get(key);
        return value == null || value.isNull() ? fallback : value.asText("").trim();
    }

    private String jsonText(JsonNode preferred, JsonNode fallback) {
        JsonNode value = preferred == null || preferred.isNull() ? fallback : preferred;
        return value == null || value.isNull() ? null : (value.isTextual() ? value.asText() : value.toString());
    }

    private ObjectNode snapshot(User user) {
        ObjectNode out = mapper.createObjectNode();
        out.put("firstName", user.getFirstName());
        out.put("lastName", user.getLastName());
        out.put("fullName", user.getFullName());
        out.put("email", user.getEmail());
        out.put("phone", user.getPhone());
        out.put("city", user.getCity());
        out.put("country", user.getCountry());
        out.put("birthDate", user.getBirthDate() == null ? null : user.getBirthDate().toString());
        out.put("gender", user.getGender());
        out.put("availability", user.getAvailability());
        out.put("nationality", user.getNationality());
        out.put("jobTitle", user.getJobTitle());
        out.put("skills", user.getSkills());
        out.put("soughtRoles", user.getSoughtRoles());
        out.put("preferredLocations", user.getPreferredLocations());
        out.put("preferredContracts", user.getPreferredContracts());
        out.put("experienceLevel", user.getExperienceLevel());
        out.put("school", user.getSchool());
        out.put("educationDomain", user.getEducationDomain());
        if (user.getGraduationYear() != null) out.put("graduationYear", user.getGraduationYear());
        out.put("workMode", user.getWorkMode());
        out.put("cvFileName", user.getCvFileName());
        out.put("cvSections", user.getCvSections());
        out.put("profileExperiences", user.getProfileExperiences());
        out.put("profileEducations", user.getProfileEducations());
        return out;
    }

    private JsonNode readPayload(String payload) {
        try {
            JsonNode root = mapper.readTree(payload == null ? "{}" : payload);
            if (root == null || !root.isObject()) throw new IllegalArgumentException();
            return root;
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Données de confirmation invalides");
        }
    }

    private void deleteNewFile(String storage) {
        if (storage != null) cvStorage.delete(storage);
    }

    private void requireCandidate(User user) {
        if (user == null || user.getRole() != Role.CANDIDAT) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Réservé aux candidats");
        }
    }
}
