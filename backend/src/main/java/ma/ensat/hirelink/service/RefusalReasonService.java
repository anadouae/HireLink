package ma.ensat.hirelink.service;

import ma.ensat.hirelink.model.RefusalReason;
import ma.ensat.hirelink.model.Role;
import ma.ensat.hirelink.model.User;
import ma.ensat.hirelink.repository.RefusalReasonRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class RefusalReasonService {

    private final RefusalReasonRepository repository;

    public RefusalReasonService(RefusalReasonRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listAll() {
        return repository.findAll().stream().map(this::toMap).toList();
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listActive() {
        return repository.findByActiveTrue().stream().map(this::toMap).toList();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> get(Long id) {
        return toMap(require(id));
    }

    @Transactional
    public Map<String, Object> create(User actor, String code, String label, boolean active) {
        requireAdmin(actor);
        if (code == null || code.isBlank() || label == null || label.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "code et label requis");
        }
        String c = code.trim().toUpperCase();
        if (repository.findByCode(c).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Code déjà existant");
        }
        RefusalReason r = new RefusalReason();
        r.setCode(c);
        r.setLabel(label.trim());
        r.setActive(active);
        repository.save(r);
        return toMap(r);
    }

    @Transactional
    public Map<String, Object> update(User actor, Long id, String code, String label, Boolean active) {
        requireAdmin(actor);
        RefusalReason r = require(id);
        if (code != null && !code.isBlank()) {
            String c = code.trim().toUpperCase();
            repository.findByCode(c).ifPresent(other -> {
                if (!other.getId().equals(id)) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Code déjà existant");
                }
            });
            r.setCode(c);
        }
        if (label != null && !label.isBlank()) r.setLabel(label.trim());
        if (active != null) r.setActive(active);
        repository.save(r);
        return toMap(r);
    }

    @Transactional
    public void delete(User actor, Long id) {
        requireAdmin(actor);
        RefusalReason r = require(id);
        repository.delete(r);
    }

    private RefusalReason require(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Motif introuvable"));
    }

    private void requireAdmin(User u) {
        if (u == null || u.getRole() != Role.ADMINISTRATEUR) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Réservé à l'administrateur");
        }
    }

    private Map<String, Object> toMap(RefusalReason r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", r.getId());
        m.put("code", r.getCode());
        m.put("label", r.getLabel());
        m.put("active", r.isActive());
        return m;
    }
}
