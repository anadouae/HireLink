package ma.ensat.hirelink.service;

import ma.ensat.hirelink.model.SupportRequest;
import ma.ensat.hirelink.model.User;
import ma.ensat.hirelink.repository.SupportRequestRepository;
import ma.ensat.hirelink.security.AuthThrottle;
import ma.ensat.hirelink.security.ClientInfo;
import ma.ensat.hirelink.service.EmailTemplates.Email;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Messages de la page Support : enregistrés en base, copie facultative à l'adresse de la plateforme. */
@Service
public class SupportService {

    public static final Set<String> TOPICS = Set.of("SIGNALEMENT", "QUESTION", "COMPTE", "ENTREPRISE", "AUTRE");
    private static final Map<String, String> TOPIC_FR = Map.of(
            "SIGNALEMENT", "Signalement",
            "QUESTION", "Question sur l'application",
            "COMPTE", "Mon compte",
            "ENTREPRISE", "Espace entreprise",
            "AUTRE", "Autre demande");
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final SupportRequestRepository repo;
    private final AuthThrottle throttle;
    private final MailService mail;

    @Value("${hirelink.support.inbox:${hirelink.mail.from:}}")
    private String inbox;

    @Value("${hirelink.support.max-per-hour:5}")
    private int maxPerHour;

    public SupportService(SupportRequestRepository repo, AuthThrottle throttle, MailService mail) {
        this.repo = repo;
        this.throttle = throttle;
        this.mail = mail;
    }

    public record Input(String topic, String name, String email, String subject, String message, String reference, String website) {}

    @Transactional
    public Map<String, Object> submit(User user, Input in) {
        String ip = ClientInfo.current().ip();
        // Champ piège invisible pour les humains : un robot le remplit, on répond comme si tout allait bien.
        if (in.website() != null && !in.website().isBlank()) return ok(null);
        throttle.hit("support:" + ip, maxPerHour, 3600, "Vous avez déjà envoyé plusieurs messages. Réessayez dans une heure.");

        String topic = clean(in.topic(), 20).toUpperCase();
        if (!TOPICS.contains(topic)) topic = "AUTRE";
        String name = clean(in.name(), 120);
        String email = clean(in.email(), 160).toLowerCase();
        if (user != null) {
            if (name.isEmpty()) name = clean(user.getFullName(), 120);
            if (email.isEmpty()) email = clean(user.getEmail(), 160).toLowerCase();
        }
        String subject = clean(in.subject(), 160);
        String message = in.message() == null ? "" : in.message().trim();
        String reference = clean(in.reference(), 300);

        if (name.length() < 2) throw bad("Indiquez votre nom.");
        if (!EMAIL.matcher(email).matches()) throw bad("Indiquez une adresse e-mail valide pour recevoir la réponse.");
        if (subject.length() < 3) throw bad("Indiquez l'objet de votre message.");
        if (message.length() < 10) throw bad("Décrivez votre demande en quelques phrases (10 caractères minimum).");
        if (message.length() > 4000) throw bad("Votre message dépasse 4 000 caractères.");

        SupportRequest r = new SupportRequest();
        r.setTopic(topic);
        r.setName(name);
        r.setEmail(email);
        r.setSubject(subject);
        r.setMessage(message);
        r.setReference(reference.isEmpty() ? null : reference);
        if (user != null) {
            r.setUserId(user.getId());
            r.setUserRole(user.getRole() == null ? null : user.getRole().name());
        }
        r = repo.save(r);

        if (inbox != null && !inbox.isBlank()) {
            String heading = "Support — " + TOPIC_FR.getOrDefault(topic, "Demande") + " n° " + r.getId();
            mail.send(inbox.trim(), "HireLink — " + heading,
                    Email.of(heading,
                            "De : " + name + " <" + email + ">" + (r.getUserRole() == null ? " (visiteur)" : " (" + r.getUserRole().toLowerCase() + ")"),
                            "Objet : " + subject,
                            reference.isEmpty() ? "Élément concerné : non précisé" : "Élément concerné : " + reference,
                            message),
                    MailService.Kind.ACCOUNT, false, null);
        }
        return ok(r.getId());
    }

    private static Map<String, Object> ok(Long id) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", id);
        out.put("message", "Message envoyé. Nous vous répondrons par e-mail.");
        return out;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> list(String status, int page, int size) {
        PageRequest pr = PageRequest.of(Math.max(0, page), Math.min(100, Math.max(1, size)));
        Page<SupportRequest> p = (status == null || status.isBlank())
                ? repo.findAllByOrderByCreatedAtDesc(pr)
                : repo.findByStatusOrderByCreatedAtDesc(status.trim().toUpperCase(), pr);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("items", p.getContent());
        out.put("totalElements", p.getTotalElements());
        out.put("totalPages", p.getTotalPages());
        out.put("openCount", repo.countByStatus("NOUVEAU"));
        return out;
    }

    @Transactional
    public SupportRequest setStatus(Long id, String status) {
        String s = status == null ? "" : status.trim().toUpperCase();
        if (!s.equals("NOUVEAU") && !s.equals("TRAITE")) throw bad("Statut inconnu.");
        SupportRequest r = repo.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Demande introuvable."));
        r.setStatus(s);
        return r;
    }

    private static ResponseStatusException bad(String msg) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, msg);
    }

    private static String clean(String v, int max) {
        if (v == null) return "";
        String s = v.replaceAll("[\\r\\n\\t]+", " ").trim();
        return s.length() > max ? s.substring(0, max) : s;
    }
}
