package ma.ensat.hirelink.service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import ma.ensat.hirelink.model.Role;
import ma.ensat.hirelink.model.SentEmail;
import ma.ensat.hirelink.model.User;
import ma.ensat.hirelink.repository.SentEmailRepository;
import ma.ensat.hirelink.service.EmailTemplates.Email;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Envoi des e-mails transactionnels (HTML + texte, en français).
 * <ul>
 *   <li>Voie principale : SMTP ({@code spring.mail.*} / variables {@code HIRELINK_MAIL_*}, STARTTLS).</li>
 *   <li>Sans serveur SMTP configuré, l'application fonctionne : le message est journalisé et reste consultable
 *       dans Administration › E-mails envoyés.</li>
 *   <li>Envoi asynchrone, après validation de la transaction métier ; un échec n'interrompt jamais l'action.</li>
 *   <li>Les messages de sécurité et de compte sont toujours envoyés ; les autres respectent la préférence de l'utilisateur.</li>
 * </ul>
 */
@Service
public class MailService {

    /** Sécurité et compte : jamais désactivables. */
    public enum Kind {
        SECURITY(true), ACCOUNT(true), APPLICATION(false), INTERVIEW(false);

        private final boolean mandatory;
        Kind(boolean mandatory) { this.mandatory = mandatory; }
    }

    public record Attachment(String filename, String contentType, byte[] data) {}

    private static final Logger log = LoggerFactory.getLogger(MailService.class);
    private static final String SUBJECT_PREFIX = "HireLink — ";
    private static final int RETENTION_DAYS = 30;

    private final ObjectProvider<JavaMailSender> sender;
    private final SentEmailRepository outbox;
    private final ExecutorService pool = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "hirelink-mail");
        t.setDaemon(true);
        return t;
    });

    @Value("${spring.mail.host:}")
    private String host;

    @Value("${spring.mail.port:587}")
    private int port;

    @Value("${spring.mail.username:}")
    private String username;

    @Value("${hirelink.mail.from:}")
    private String from;

    @Value("${hirelink.otp.log-to-console:true}")
    private boolean devConsole;

    @Value("${hirelink.frontend.base-url:http://localhost:5500}")
    private String frontendBase;

    public MailService(ObjectProvider<JavaMailSender> sender, SentEmailRepository outbox) {
        this.sender = sender;
        this.outbox = outbox;
    }

    @PostConstruct
    void announce() {
        if (smtpConfigured()) {
            log.info("E-mails : envoi SMTP via {}:{} (expéditeur {})", host, port, sender());
        } else {
            log.info("E-mails : aucun serveur SMTP configuré — les messages sont journalisés et consultables dans Administration › E-mails envoyés");
        }
    }

    @PreDestroy
    void shutdown() {
        pool.shutdown();
    }

    /** Message de sécurité en texte simple (paragraphes séparés par une ligne vide). {@code sensitive} : code ou lien secret. */
    public void send(String to, String subject, String body, boolean sensitive) {
        String heading = subject.startsWith(SUBJECT_PREFIX) ? subject.substring(SUBJECT_PREFIX.length()) : subject;
        send(to, subject, Email.of(heading, body.split("\n\n")), Kind.SECURITY, sensitive, null);
    }

    /** Message de sécurité ou de compte avec mise en forme ; {@code sensitive} : le corps n'est pas conservé dans la boîte d'envoi. */
    public void send(String to, String subject, Email email, Kind kind, boolean sensitive, Attachment attachment) {
        dispatch(to, subject, email, kind, sensitive, attachment, footer(kind, null));
    }

    /** Message de suivi à un utilisateur connu : ignoré s'il a désactivé les e-mails (sauf messages obligatoires). */
    public void notify(User user, String subject, Email email, Kind kind, Attachment attachment) {
        if (!kind.mandatory && !user.isEmailNotifications()) return;
        dispatch(user.getEmail(), subject, email, kind, false, attachment, footer(kind, user));
    }

    private String footer(Kind kind, User user) {
        if (kind.mandatory || user == null) {
            return "Ce message concerne la sécurité ou l'accès à votre compte : il est toujours envoyé.";
        }
        String settings = user.getRole() == Role.RECRUTEUR ? "recruiter-settings.html#alertes" : "candidate-settings.html#privacy";
        return "Vous recevez cet e-mail car vous avez un compte HireLink.\nGérer mes e-mails : "
                + frontendBase.replaceAll("/$", "") + "/app/" + settings;
    }

    private void dispatch(String to, String subject, Email email, Kind kind, boolean sensitive, Attachment attachment, String footer) {
        String html = EmailTemplates.html(email, footer);
        String text = EmailTemplates.text(email, footer);
        Runnable task = () -> pool.execute(() -> deliver(to, subject, kind, sensitive, html, text, attachment));
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() { task.run(); }
            });
        } else {
            task.run();
        }
    }

    private void deliver(String to, String subject, Kind kind, boolean sensitive, String html, String text, Attachment attachment) {
        SentEmail record = new SentEmail();
        record.setRecipient(to);
        record.setSubject(subject.length() > 200 ? subject.substring(0, 200) : subject);
        record.setKind(kind.name());
        record.setBodyHtml(sensitive ? null : html);
        if (smtpConfigured()) {
            try {
                MimeMessage message = sender.getObject().createMimeMessage();
                MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
                helper.setFrom(new InternetAddress(sender(), true));
                helper.setTo(to);
                helper.setSubject(subject);
                helper.setText(text, html);
                if (attachment != null) {
                    helper.addAttachment(attachment.filename(), new ByteArrayResource(attachment.data()), attachment.contentType());
                }
                sender.getObject().send(message);
                record.setStatus(SentEmail.Status.SENT);
            } catch (Exception e) {
                record.setStatus(SentEmail.Status.FAILED);
                record.setError(e.getClass().getSimpleName());
                log.warn("Envoi d'e-mail impossible ({}) : {}", subject, e.toString());
            }
        } else {
            record.setStatus(SentEmail.Status.LOGGED);
            log.info("[MAIL non envoyé : SMTP absent] à {} — {}{}", to, subject, sensitive && devConsole ? "\n" + text : "");
        }
        try {
            outbox.save(record);
        } catch (Exception e) {
            log.warn("Boîte d'envoi : enregistrement impossible ({})", e.getClass().getSimpleName());
        }
    }

    private boolean smtpConfigured() {
        return !host.isBlank() && sender.getIfAvailable() != null;
    }

    /** Adresse d'expéditeur : {@code hirelink.mail.from}, à défaut l'identifiant SMTP. */
    private String sender() {
        return from.isBlank() ? username : from;
    }

    @Scheduled(cron = "0 30 3 * * *")
    @Transactional
    public void purge() {
        int n = outbox.deleteOlderThan(Instant.now().minus(Duration.ofDays(RETENTION_DAYS)));
        if (n > 0) log.info("Boîte d'envoi : {} message(s) de plus de {} jours supprimé(s)", n, RETENTION_DAYS);
    }
}
