package ma.ensat.hirelink.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import ma.ensat.hirelink.model.Role;
import ma.ensat.hirelink.model.User;
import ma.ensat.hirelink.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.Map;

/** Expériences / formations structurées stockées en JSON sur le compte candidat. */
@Service
public class CandidateCareerService {

    private static final int MAX_ITEMS = 30;
    private final UserRepository users;
    private final ObjectMapper mapper = new ObjectMapper();

    public CandidateCareerService(UserRepository users) {
        this.users = users;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> get(User user) {
        requireCandidate(user);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("experiences", parseArray(user.getProfileExperiences()));
        out.put("educations", parseArray(user.getProfileEducations()));
        out.put("presentation", user.getCompanyPresentation() == null ? "" : user.getCompanyPresentation());
        out.put("jobTitle", user.getJobTitle() == null ? "" : user.getJobTitle());
        out.put("country", user.getCountry() == null ? "" : user.getCountry());
        out.put("city", user.getCity() == null ? "" : user.getCity());
        return out;
    }

    @Transactional
    public Map<String, Object> save(User user, Map<String, Object> body) {
        requireCandidate(user);
        if (body.containsKey("experiences")) {
            user.setProfileExperiences(normalizeArray(body.get("experiences"), "experience"));
        }
        if (body.containsKey("educations")) {
            user.setProfileEducations(normalizeArray(body.get("educations"), "education"));
        }
        if (body.containsKey("presentation")) {
            String bio = String.valueOf(body.get("presentation") == null ? "" : body.get("presentation")).trim();
            user.setCompanyPresentation(bio.length() > 800 ? bio.substring(0, 800) : bio);
        }
        if (body.containsKey("jobTitle")) {
            String t = String.valueOf(body.get("jobTitle") == null ? "" : body.get("jobTitle")).trim();
            user.setJobTitle(t.isBlank() ? null : t.substring(0, Math.min(160, t.length())));
        }
        if (body.containsKey("country")) {
            String c = String.valueOf(body.get("country") == null ? "" : body.get("country")).trim();
            user.setCountry(c.isBlank() ? null : c.substring(0, Math.min(80, c.length())));
        }
        users.save(user);
        return get(user);
    }

    private void requireCandidate(User user) {
        if (user.getRole() != Role.CANDIDAT) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Réservé aux candidats");
        }
    }

    private Object parseArray(String raw) {
        if (raw == null || raw.isBlank()) return mapper.createArrayNode();
        try {
            JsonNode n = mapper.readTree(raw);
            return n.isArray() ? n : mapper.createArrayNode();
        } catch (Exception e) {
            return mapper.createArrayNode();
        }
    }

    private String normalizeArray(Object raw, String kind) {
        try {
            JsonNode node = mapper.valueToTree(raw);
            if (!node.isArray()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Liste " + kind + " invalide.");
            }
            if (node.size() > MAX_ITEMS) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Maximum " + MAX_ITEMS + " éléments.");
            }
            ArrayNode clean = mapper.createArrayNode();
            for (JsonNode item : node) {
                if (!item.isObject()) continue;
                ObjectNode row = mapper.createObjectNode();
                if ("experience".equals(kind)) {
                    copy(row, item, "title", 120);
                    copy(row, item, "company", 120);
                    copy(row, item, "location", 120);
                    copy(row, item, "startDate", 10);
                    copy(row, item, "endDate", 10);
                    row.put("current", item.path("current").asBoolean(false));
                    copy(row, item, "missions", 2000);
                    copy(row, item, "achievements", 2000);
                } else {
                    copy(row, item, "school", 160);
                    copy(row, item, "degree", 120);
                    copy(row, item, "domain", 120);
                    copy(row, item, "startDate", 10);
                    copy(row, item, "endDate", 10);
                    copy(row, item, "description", 1000);
                }
                clean.add(row);
            }
            return mapper.writeValueAsString(clean);
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Données " + kind + " illisibles.");
        }
    }

    private static void copy(ObjectNode row, JsonNode src, String field, int max) {
        String v = src.path(field).asText("").trim();
        if (v.length() > max) v = v.substring(0, max);
        row.put(field, v);
    }
}
