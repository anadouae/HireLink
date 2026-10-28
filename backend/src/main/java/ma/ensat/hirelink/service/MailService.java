package ma.ensat.hirelink.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * Envoi d'e-mails de sécurité (réinitialisation, changement de mot de passe, nouvel appareil).
 * Désactivé par défaut (démo locale) : en développement seulement, le message est alors affiché dans la console du serveur
 * ({@code hirelink.otp.log-to-console=true}) ; en production l'affichage doit rester désactivé.
 */
@Service
public class MailService {

    private static final Logger log = LoggerFactory.getLogger(MailService.class);
    private final ObjectProvider<JavaMailSender> sender;

    @Value("${hirelink.mail.enabled:false}")
    private boolean enabled;

    @Value("${hirelink.mail.from:noreply@hirelink.local}")
    private String from;

    @Value("${hirelink.otp.log-to-console:true}")
    private boolean devConsole;

    public MailService(ObjectProvider<JavaMailSender> sender) {
        this.sender = sender;
    }

    /** {@code sensitive=true} : le corps contient un lien/code secret, jamais journalisé hors mode développement. */
    public void send(String to, String subject, String body, boolean sensitive) {
        if (enabled && sender.getIfAvailable() != null) {
            try {
                SimpleMailMessage m = new SimpleMailMessage();
                m.setFrom(from);
                m.setTo(to);
                m.setSubject(subject);
                m.setText(body);
                sender.getObject().send(m);
                return;
            } catch (Exception e) {
                log.warn("Envoi d'e-mail impossible ({}) : {}", subject, e.getClass().getSimpleName());
            }
        }
        if (devConsole) {
            log.info("[MAIL simulé] à {} — {}{}", to, subject, sensitive ? "\n" + body : "");
        } else {
            log.info("[MAIL non envoyé] {}", subject);
        }
    }
}
