package ma.ensat.hirelink.service;

import ma.ensat.hirelink.model.*;
import ma.ensat.hirelink.repository.OrganizationNoteRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;

@Service
public class OrganizationNoteService {
    private final OrganizationNoteRepository notes;
    private final OrganizationService organizations;

    public OrganizationNoteService(OrganizationNoteRepository notes, OrganizationService organizations) {
        this.notes = notes;
        this.organizations = organizations;
    }

    @Transactional(readOnly = true)
    public List<OrganizationNote> list(User user) {
        Membership m = organizations.requireMembership(user);
        return notes.findAllByOrganizationIdAndOwnerIdOrderByUpdatedAtDesc(m.getOrganization().getId(), user.getId());
    }

    @Transactional
    public OrganizationNote save(User user, Long id, String title, String content, String inkColor,
                                 String noteColor, String drawingData, Integer x, Integer y) {
        Membership m = organizations.requireMembership(user);
        OrganizationNote note = id == null
                ? new OrganizationNote()
                : notes.findByIdAndOrganizationIdAndOwnerId(id, m.getOrganization().getId(), user.getId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Post-it introuvable"));
        note.setOrganization(m.getOrganization());
        note.setOwner(user);
        note.setTitle(clean(title, 120, "Post-it"));
        note.setContent(clean(content, 8000, ""));
        note.setInkColor(color(inkColor, "#243044"));
        note.setNoteColor(color(noteColor, "#fff4a8"));
        note.setDrawingData(clean(drawingData, 120000, null));
        note.setPositionX(clamp(x, -2000, 2000));
        note.setPositionY(clamp(y, -2000, 2000));
        note.touch();
        return notes.save(note);
    }

    @Transactional
    public void delete(User user, Long id) {
        Membership m = organizations.requireMembership(user);
        notes.delete(notes.findByIdAndOrganizationIdAndOwnerId(id, m.getOrganization().getId(), user.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Post-it introuvable")));
    }

    private static String clean(String s, int max, String fallback) {
        if (s == null || s.isBlank()) return fallback;
        return s.trim().substring(0, Math.min(max, s.trim().length()));
    }
    private static String color(String s, String fallback) {
        return s != null && s.matches("^#[0-9a-fA-F]{6}$") ? s : fallback;
    }
    private static int clamp(Integer n, int min, int max) {
        return n == null ? 0 : Math.max(min, Math.min(max, n));
    }
}
