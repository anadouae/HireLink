package ma.ensat.hirelink.service;

import ma.ensat.hirelink.model.*;
import ma.ensat.hirelink.repository.OrganizationEventRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;
import java.util.List;

@Service
public class OrganizationEventService {
    private static final int MAX_DURATION = 24 * 60;
    private final OrganizationEventRepository events;
    private final OrganizationService organizations;

    public OrganizationEventService(OrganizationEventRepository events, OrganizationService organizations) {
        this.events = events;
        this.organizations = organizations;
    }

    @Transactional(readOnly = true)
    public List<OrganizationEvent> list(User user) {
        Membership membership = organizations.requireMembership(user);
        return events.findAllByOrganizationIdOrderByStartsAt(membership.getOrganization().getId());
    }

    @Transactional
    public OrganizationEvent create(User user, String title, String description, Instant startsAt,
                                    Integer durationMinutes, String location, String videoUrl,
                                    String category) {
        Membership membership = requireEditor(user);
        OrganizationEvent event = new OrganizationEvent();
        event.setOrganization(membership.getOrganization());
        event.setCreatedBy(user);
        apply(event, title, description, startsAt, durationMinutes, location, videoUrl, category);
        return events.save(event);
    }

    @Transactional
    public OrganizationEvent update(User user, Long id, String title, String description, Instant startsAt,
                                    Integer durationMinutes, String location, String videoUrl,
                                    String category) {
        Membership membership = requireEditor(user);
        OrganizationEvent event = events.findByIdAndOrganizationId(id, membership.getOrganization().getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Événement introuvable"));
        apply(event, title, description, startsAt, durationMinutes, location, videoUrl, category);
        return events.save(event);
    }

    @Transactional
    public void delete(User user, Long id) {
        Membership membership = requireEditor(user);
        OrganizationEvent event = events.findByIdAndOrganizationId(id, membership.getOrganization().getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Événement introuvable"));
        events.delete(event);
    }

    private Membership requireEditor(User user) {
        Membership membership = organizations.requireMembership(user);
        if (membership.getOrgRole() == OrgRole.HIRING_MANAGER) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Les Responsables du poste consultent l'agenda mais ne créent pas d'événements");
        }
        return membership;
    }

    private void apply(OrganizationEvent event, String title, String description, Instant startsAt,
                       Integer durationMinutes, String location, String videoUrl, String category) {
        if (title == null || title.isBlank()) throw bad("Le titre est obligatoire.");
        if (startsAt == null || startsAt.isBefore(Instant.now())) throw bad("L'événement doit commencer dans le futur.");
        int duration = durationMinutes == null ? 60 : durationMinutes;
        if (duration < 5 || duration > MAX_DURATION) throw bad("La durée doit être comprise entre 5 minutes et 24 heures.");
        event.setTitle(clean(title, 180));
        event.setDescription(clean(description, 2000));
        event.setStartsAt(startsAt);
        event.setDurationMinutes(duration);
        event.setLocation(clean(location, 120));
        event.setVideoUrl(clean(videoUrl, 500));
        event.setCategory(normalize(category, "OTHER"));
    }

    private static String normalize(String value, String fallback) {
        String s = clean(value, 32);
        return s == null || s.isBlank() ? fallback : s.toUpperCase();
    }

    private static String clean(String value, int max) {
        if (value == null) return null;
        String s = value.trim();
        return s.isBlank() ? null : s.substring(0, Math.min(max, s.length()));
    }

    private static ResponseStatusException bad(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
