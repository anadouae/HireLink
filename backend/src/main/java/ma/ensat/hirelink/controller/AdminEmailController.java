package ma.ensat.hirelink.controller;

import ma.ensat.hirelink.model.SentEmail;
import ma.ensat.hirelink.repository.SentEmailRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
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

    @GetMapping
    public List<Row> latest() {
        return outbox.findAllByOrderByCreatedAtDescIdDesc(PageRequest.of(0, PAGE_SIZE)).stream()
                .map(e -> new Row(e.getId(), e.getRecipient(), e.getSubject(), e.getKind(), e.getStatus().name(), e.getError(),
                        e.getCreatedAt(), e.getBodyHtml() != null))
                .toList();
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
