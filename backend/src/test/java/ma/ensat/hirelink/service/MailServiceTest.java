package ma.ensat.hirelink.service;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import ma.ensat.hirelink.model.SentEmail;
import ma.ensat.hirelink.model.User;
import ma.ensat.hirelink.repository.SentEmailRepository;
import ma.ensat.hirelink.service.EmailTemplates.Email;
import ma.ensat.hirelink.service.MailService.Kind;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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
}
