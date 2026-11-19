package ma.ensat.hirelink.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import ma.ensat.hirelink.model.Role;
import ma.ensat.hirelink.model.User;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class CvImportDraftService {

    private static final int MAX_PAYLOAD_LENGTH = 2_000_000;

    private final JdbcTemplate jdbc;
    private final CvStorage cvStorage;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public CvImportDraftService(JdbcTemplate jdbc, CvStorage cvStorage) {
        this.jdbc = jdbc;
        this.cvStorage = cvStorage;
    }

    @Transactional
    public Map<String, Object> save(User user, MultipartFile file, String payload, String step) {
        requireCandidate(user);
        String normalizedPayload = normalizePayload(payload);
        String normalizedStep = normalizeStep(step);
        String stored = null;
        String fileName = null;
        if (file != null && !file.isEmpty()) {
            try {
                stored = cvStorage.save("drafts", file.getOriginalFilename(), file.getBytes());
                fileName = FileSignature.safeName(file.getOriginalFilename());
            } catch (Exception e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Brouillon CV impossible à enregistrer");
            }
        }

        Map<String, Object> existing = findLatest(user.getId());
        if (existing == null) {
            jdbc.update("""
                    INSERT INTO cv_import_drafts
                    (user_id, cv_file_name, cv_storage_name, status, step, payload_json, expires_at)
                    VALUES (?, ?, ?, 'DRAFT', ?, ?, DATE_ADD(CURRENT_TIMESTAMP(6), INTERVAL 7 DAY))
                    """, user.getId(), fileName, stored, normalizedStep, normalizedPayload);
        } else {
            if (stored == null) {
                jdbc.update("""
                        UPDATE cv_import_drafts
                        SET step = ?, payload_json = ?, updated_at = CURRENT_TIMESTAMP(6),
                            expires_at = DATE_ADD(CURRENT_TIMESTAMP(6), INTERVAL 7 DAY)
                        WHERE id = ?
                        """, normalizedStep, normalizedPayload, existing.get("id"));
            } else {
                jdbc.update("""
                        UPDATE cv_import_drafts
                        SET cv_file_name = ?, cv_storage_name = ?, step = ?, payload_json = ?,
                            updated_at = CURRENT_TIMESTAMP(6),
                            expires_at = DATE_ADD(CURRENT_TIMESTAMP(6), INTERVAL 7 DAY)
                        WHERE id = ?
                        """, fileName, stored, normalizedStep, normalizedPayload, existing.get("id"));
            }
        }
        Map<String, Object> saved = findLatest(user.getId());
        if (saved != null) syncItems(saved.get("id"), normalizedPayload);
        return get(user);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> get(User user) {
        requireCandidate(user);
        Map<String, Object> draft = findLatest(user.getId());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("draft", draft == null ? null : toResponse(draft));
        return result;
    }

    @Transactional(readOnly = true)
    public Resource file(User user) {
        requireCandidate(user);
        Map<String, Object> draft = findLatest(user.getId());
        if (draft == null || draft.get("cv_storage_name") == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Aucun fichier de brouillon");
        }
        Resource resource = cvStorage.load(String.valueOf(draft.get("cv_storage_name")));
        if (resource == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Fichier de brouillon introuvable");
        }
        return resource;
    }

    @Transactional
    public void delete(User user) {
        requireCandidate(user);
        Map<String, Object> draft = findLatest(user.getId());
        if (draft == null) return;
        Object storage = draft.get("cv_storage_name");
        if (storage != null) cvStorage.delete(String.valueOf(storage));
        jdbc.update("DELETE FROM cv_import_drafts WHERE id = ?", draft.get("id"));
    }

    private Map<String, Object> findLatest(Long userId) {
        try {
            return jdbc.queryForMap("""
                    SELECT id, cv_file_name, cv_storage_name, status, step, payload_json,
                           created_at, updated_at, expires_at
                    FROM cv_import_drafts
                    WHERE user_id = ? AND status = 'DRAFT'
                      AND (expires_at IS NULL OR expires_at > CURRENT_TIMESTAMP(6))
                    ORDER BY updated_at DESC, id DESC
                    LIMIT 1
                    """, userId);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    private Map<String, Object> toResponse(Map<String, Object> draft) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", draft.get("id"));
        out.put("fileName", draft.get("cv_file_name"));
        out.put("step", draft.get("step"));
        out.put("updatedAt", draft.get("updated_at"));
        out.put("expiresAt", draft.get("expires_at"));
        String raw = String.valueOf(draft.get("payload_json") == null ? "{}" : draft.get("payload_json"));
        try {
            JsonNode payload = objectMapper.readTree(raw);
            out.put("payload", payload == null ? objectMapper.createObjectNode() : payload);
        } catch (Exception e) {
            out.put("payload", objectMapper.createObjectNode());
        }
        return out;
    }

    private String normalizePayload(String payload) {
        String value = payload == null || payload.isBlank() ? "{}" : payload.trim();
        if (value.length() > MAX_PAYLOAD_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Brouillon CV trop volumineux");
        }
        try {
            objectMapper.readTree(value);
            return value;
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Données de brouillon invalides");
        }
    }

    private String normalizeStep(String step) {
        String value = step == null ? "IMPORT" : step.trim().toUpperCase();
        return switch (value) {
            case "IMPORT", "READING", "REVIEW" -> value;
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Étape de brouillon invalide");
        };
    }

    private void syncItems(Object draftId, String payload) {
        if (draftId == null) return;
        try {
            JsonNode root = objectMapper.readTree(payload);
            JsonNode items = root.path("extractProposals");
            if (!items.isArray()) items = root.path("items");
            if (!items.isArray()) return;
            jdbc.update("DELETE FROM cv_import_items WHERE draft_id = ?", draftId);
            for (JsonNode item : items) {
                String state = item.path("status").asText("DETECTED").toUpperCase();
                if (!java.util.Set.of("DETECTED", "ACCEPTED", "MODIFIED", "REFUSED", "MISSING", "VERIFY").contains(state)) {
                    state = "VERIFY";
                }
                jdbc.update("""
                        INSERT INTO cv_import_items
                        (draft_id, section_type, item_key, source_text, target_field,
                         proposed_value_json, current_value_json, state)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                        draftId,
                        item.path("sectionType").asText("profile"),
                        item.path("id").asText(null),
                        item.path("sourceText").asText(null),
                        item.path("targetField").asText(null),
                        jsonValue(item.get("proposedValue"), item.get("proposed")),
                        jsonValue(item.get("currentValue"), item.get("current")),
                        state);
            }
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Éléments du brouillon impossibles à enregistrer");
        }
    }

    private String jsonValue(JsonNode preferred, JsonNode fallback) {
        JsonNode value = preferred == null || preferred.isNull() ? fallback : preferred;
        return value == null || value.isNull() ? null : (value.isTextual() ? value.asText() : value.toString());
    }

    private void requireCandidate(User user) {
        if (user == null || user.getRole() != Role.CANDIDAT) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Réservé aux candidats");
        }
    }
}
