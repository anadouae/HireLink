package ma.ensat.hirelink.service;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import ma.ensat.hirelink.model.SentEmail;
import ma.ensat.hirelink.model.User;
import ma.ensat.hirelink.repository.SentEmailRepository;
import ma.ensat.hirelink.service.EmailTemplates.Email;
import ma.ensat.hirelink.service.MailService.Kind;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MailServiceTest {

    private final JavaMailSender smtp = mock(JavaMailSender.class);
    private final SentEmailRepository outbox = mock(SentEmailRepository.class);

    @SuppressWarnings("unchecked")
    private MailService service(String host) {
        ObjectProvider<JavaMailSender> provider = mock(ObjectProvider.class);
        when(provider.getObject()).thenReturn(smtp);
        when(provider.getIfAvailable()).thenReturn(smtp);
        when(smtp.createMimeMessage()).thenAnswer(i -> new MimeMessage(Session.getInstance(new Properties())));
        MailService service = new MailService(provider, outbox);
        ReflectionTestUtils.setField(service, "host", host);
        ReflectionTestUtils.setField(service, "from", "HireLink <noreply@hirelink.test>");
        ReflectionTestUtils.setField(service, "username", "");
        ReflectionTestUtils.setField(service, "devConsole", false);
        ReflectionTestUtils.setField(service, "frontendBase", "http://localhost:5500");
        return service;
    }

    @Test
    void messageSansPieceJointeEstEnvoyeEnHtmlEtTexte() throws Exception {
        MailService service = service("smtp.test");

        service.send("sara@example.com", "HireLink — test", Email.of("Test", "Bonjour"), Kind.APPLICATION, false, null);

        ArgumentCaptor<MimeMessage> sent = ArgumentCaptor.forClass(MimeMessage.class);
        verify(smtp, timeout(3000)).send(sent.capture());
        sent.getValue().saveChanges();
        assertThat(sent.getValue().getContentType()).startsWith("multipart/");
        ArgumentCaptor<SentEmail> row = ArgumentCaptor.forClass(SentEmail.class);
        verify(outbox, timeout(3000)).save(row.capture());
        assertThat(row.getValue().getStatus()).isEqualTo(SentEmail.Status.SENT);
    }

    @Test
    void sansServeurSmtpLeMessageEstSeulementJournalise() {
        MailService service = service("");

        service.send("sara@example.com", "HireLink — test", Email.of("Test", "Bonjour"), Kind.APPLICATION, false, null);

        ArgumentCaptor<SentEmail> row = ArgumentCaptor.forClass(SentEmail.class);
        verify(outbox, timeout(3000)).save(row.capture());
        assertThat(row.getValue().getStatus()).isEqualTo(SentEmail.Status.LOGGED);
        verify(smtp, never()).send(any(MimeMessage.class));
    }

    @Test
    void laDesinscriptionEstRespecteePourLesMessagesDeSuivi() {
        MailService service = service("smtp.test");
        User user = mock(User.class);
        when(user.isEmailNotifications()).thenReturn(false);
        when(user.getEmail()).thenReturn("sara@example.com");

        service.notify(user, "HireLink — suivi", Email.of("Suivi", "Bonjour"), Kind.APPLICATION, null);
        service.notify(user, "HireLink — sécurité", Email.of("Sécurité", "Bonjour"), Kind.SECURITY, null);

        ArgumentCaptor<SentEmail> row = ArgumentCaptor.forClass(SentEmail.class);
        verify(outbox, timeout(3000)).save(row.capture());
        assertThat(row.getValue().getKind()).isEqualTo("SECURITY");
    }

    /** C4 : sans opt-in explicite, ni le code ni le lien de réinitialisation n'atteignent les journaux. */
    @Test
    void unMessageConfidentielNestJamaisJournaliseSansOptIn() {
        MailService service = service("");
        ListAppender<ILoggingEvent> logs = captureLogs();
        try {
            service.send("sara@example.com", "HireLink — code", "Votre code est 481920.", true);
            verify(outbox, timeout(3000)).save(any(SentEmail.class));
            assertThat(logs.list).isNotEmpty();
            assertThat(logs.list).noneMatch(e -> e.getFormattedMessage().contains("481920"));
        } finally {
            detach(logs);
        }
    }

    /** L'opt-in -Dhirelink.otp.log-to-console=true reste la seule porte vers l'affichage du secret. */
    @Test
    void lOptInConsoleAfficheLeCodeQuandAucunSmtpNePeutLeLivrer() {
        MailService service = service("");
        ReflectionTestUtils.setField(service, "devConsole", true);
        ListAppender<ILoggingEvent> logs = captureLogs();
        try {
            service.send("sara@example.com", "HireLink — code", "Votre code est 730115.", true);
            verify(outbox, timeout(3000)).save(any(SentEmail.class));
            assertThat(logs.list).anyMatch(e -> e.getFormattedMessage().contains("730115"));
        } finally {
            detach(logs);
        }
    }

    /** Le corps d'un message confidentiel n'est pas conservé dans la boîte d'envoi consultable. */
    @Test
    void leCorpsConfidentielNestEnregistreEnBase() {
        MailService service = service("");

        service.send("sara@example.com", "HireLink — réinitialisation", "Lien : /auth.html?mode=reset&token=abc123", true);

        ArgumentCaptor<SentEmail> row = ArgumentCaptor.forClass(SentEmail.class);
        verify(outbox, timeout(3000)).save(row.capture());
        assertThat(row.getValue().getBodyHtml()).isNull();
    }

    private Level previousLevel;

    /** Les tests partagent une JVM : un contexte Spring peut avoir fixé le niveau à WARN, on le rouvre ici. */
    private ListAppender<ILoggingEvent> captureLogs() {
        Logger logger = (Logger) LoggerFactory.getLogger(MailService.class);
        previousLevel = logger.getLevel();
        logger.setLevel(Level.INFO);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        return appender;
    }

    private void detach(ListAppender<ILoggingEvent> appender) {
        Logger logger = (Logger) LoggerFactory.getLogger(MailService.class);
        logger.detachAppender(appender);
        logger.setLevel(previousLevel);
    }
}
