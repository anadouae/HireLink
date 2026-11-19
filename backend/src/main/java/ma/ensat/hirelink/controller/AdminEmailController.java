package ma.ensat.hirelink.controller;

import ma.ensat.hirelink.model.SentEmail;
import ma.ensat.hirelink.repository.SentEmailRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;

/** Boîte d'envoi : les derniers e-mails émis (ou seulement journalisés sans SMTP), consultables par l'administration. */
@PreAuthorize("hasRole('ADMINISTRATEUR')")
@RestController
@RequestMapping("/api/v1/admin/emails")
public class AdminEmailController {

    private static final int PAGE_SIZE = 100;

    public record Row(Long id, String recipient, String subject, String kind, String status, String error, Instant at, boolean hasBody) {}

    public record Body(Long id, String subject, String html) {}

    private final SentEmailRepository outbox;

    public AdminEmailController(SentEmailRepository outbox) {
        this.outbox = outbox;
    }

    public record PageOut(List<Row> rows, long total, int page, int size, int pages, List<String> kinds,
                          java.util.Map<String, Long> counts) {}

    /**
     * Journal des e-mails, filtré côté serveur. Les dates (AAAA-MM-JJ) sont des jours locaux
     * Africa/Casablanca ; « to » est inclus.
     */
    @GetMapping
    public PageOut latest(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String kind,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size
    ) {
        Instant fromAt = AdminDates.dayStart(from);
        Instant toAt = AdminDates.dayEndExclusive(to);
        SentEmail.Status st = null;
        if (status != null && !status.isBlank()) {
            try {
                st = SentEmail.Status.valueOf(status.trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Statut inconnu");
            }
        }
        String k = kind == null || kind.isBlank() ? null : kind.trim().toUpperCase();
        String like = q == null || q.isBlank() ? null : "%" + q.trim().toLowerCase() + "%";
        int safe = Math.min(PAGE_SIZE, Math.max(5, size));
        var result = outbox.search(fromAt, toAt, st, k, like, PageRequest.of(Math.max(0, page), safe));
        List<Row> rows = result.getContent().stream()
                .map(e -> new Row(e.getId(), e.getRecipient(), e.getSubject(), e.getKind(), e.getStatus().name(), e.getError(),
                        e.getCreatedAt(), e.getBodyHtml() != null))
                .toList();
        java.util.Map<String, Long> counts = new java.util.LinkedHashMap<>();
        for (SentEmail.Status s : SentEmail.Status.values()) {
            counts.put(s.name(), outbox.search(fromAt, toAt, s, k, like, PageRequest.of(0, 1)).getTotalElements());
        }
        return new PageOut(rows, result.getTotalElements(), result.getNumber(), safe, result.getTotalPages(),
                outbox.distinctKinds(), counts);
    }

    @GetMapping("/{id}")
    public Body body(@PathVariable Long id) {
        SentEmail e = outbox.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "E-mail introuvable"));
        if (e.getBodyHtml() == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Contenu confidentiel : non conservé");
        }
        return new Body(e.getId(), e.getSubject(), e.getBodyHtml());
    }
}
