package ma.ensat.hirelink.service;

import ma.ensat.hirelink.dto.ApiDtos.InterviewRequest;
import ma.ensat.hirelink.model.Application;
import ma.ensat.hirelink.model.JobOffer;
import ma.ensat.hirelink.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Notifications d'entretien : convocation, report, annulation et rappel J-1.
 * Aucun message ne demande de réponse au candidat (décision D1).
 */
class EventNotifierInterviewTest {

    private NotificationService notifications;
    private OrganizationService organizations;
    private final InterviewService interviews = new InterviewService();
    private EventNotifier notifier;
    private Application app;

    @BeforeEach
    void setUp() {
        notifications = mock(NotificationService.class);
        organizations = mock(OrganizationService.class);
        notifier = new EventNotifier(notifications, organizations, interviews);
        ReflectionTestUtils.setField(notifier, "frontendBase", "http://localhost:5500/");

        User candidate = new User();
        candidate.setId(12L);
        candidate.setFullName("Sara Bennani");
        User recruiter = new User();
        recruiter.setId(7L);
        JobOffer offer = new JobOffer();
        offer.setId(4L);
        offer.setTitle("Développeur Java");
        offer.setRecruiter(recruiter);
        app = new Application();
        app.setId(9L);
        app.setJobOffer(offer);
        app.setCandidate(candidate);
        interviews.schedule(app, new InterviewRequest("VISIO", "TECHNICAL", LocalDateTime.of(2030, 3, 4, 9, 0),
                "Africa/Casablanca", 60, null, null, "https://meet.example.com/abc", "Mme Alaoui", null));
        when(organizations.offerNotificationTargets(any())).thenReturn(Set.of());
    }

    private static ArgumentCaptor<EmailTemplates.Email> email() {
        return ArgumentCaptor.forClass(EmailTemplates.Email.class);
    }

    @Test
    void planifierEnvoieUneConvocationAuCandidatAvecLeFichierCalendrier() {
        ArgumentCaptor<EmailTemplates.Email> email = email();
        ArgumentCaptor<MailService.Attachment> piece = ArgumentCaptor.forClass(MailService.Attachment.class);

        notifier.interviewScheduled(app, false);

        verify(notifications).pushAndMail(eq("interview:9"), eq(12L), eq("Entretien planifié"),
                anyString(), eq("INTERVIEW"), eq(MailService.Kind.INTERVIEW), anyString(),
                email.capture(), piece.capture());
        assertEquals("Entretien planifié", email.getValue().heading());
        assertTrue(email.getValue().paragraphs().get(0).contains("« Développeur Java »"));
        assertEquals("Voir ma convocation", email.getValue().ctaLabel());
        assertEquals("http://localhost:5500/app/candidate-applications.html?id=9", email.getValue().ctaUrl());
        assertEquals("entretien-hirelink.ics", piece.getValue().filename());
        assertTrue(new String(piece.getValue().data(), java.nio.charset.StandardCharsets.UTF_8)
                .contains("STATUS:CONFIRMED"));
    }

    @Test
    void reporterAnnonceLeReportEtNonUneNouvelleProposition() {
        ArgumentCaptor<EmailTemplates.Email> email = email();

        notifier.interviewScheduled(app, true);

        verify(notifications).pushAndMail(eq("interview:9"), eq(12L), eq("Entretien reporté"), anyString(),
                eq("INTERVIEW"), eq(MailService.Kind.INTERVIEW), anyString(), email.capture(), any());
        assertTrue(email.getValue().paragraphs().get(0).contains("a été reporté"));
        assertEquals("Voir ma convocation", email.getValue().ctaLabel());
    }

    @Test
    void lequipeQuiSuitLeDossierEstPrevenueEnMemeTempsQueLeCandidat() {
        when(organizations.offerNotificationTargets(any())).thenReturn(Set.of(21L));
        app.setAssignedHmUserId(8L);

        notifier.interviewScheduled(app, false);

        verify(notifications, times(3)).pushAndMail(anyString(), anyLong(), anyString(), anyString(),
                anyString(), any(MailService.Kind.class), anyString(), any(EmailTemplates.Email.class), any());
        verify(notifications).pushAndMail(anyString(), eq(21L), eq("Entretien planifié"), anyString(),
                anyString(), any(MailService.Kind.class), anyString(), any(EmailTemplates.Email.class), any());
        verify(notifications).pushAndMail(anyString(), eq(8L), eq("Entretien planifié"), anyString(),
                anyString(), any(MailService.Kind.class), anyString(), any(EmailTemplates.Email.class), any());
    }

    @Test
    void annulerDonneLeMotifAuCandidat() {
        ArgumentCaptor<EmailTemplates.Email> email = email();
        interviews.cancel(app, "Poste pourvu en interne.");

        notifier.interviewCancelled(app);

        verify(notifications).pushAndMail(eq("interview:9"), eq(12L), eq("Entretien annulé"),
                eq("« Développeur Java » — Poste pourvu en interne."), eq("INTERVIEW"),
                eq(MailService.Kind.INTERVIEW), anyString(), email.capture(), any());
        assertTrue(email.getValue().facts().stream()
                .anyMatch(f -> "Motif".equals(f.label()) && "Poste pourvu en interne.".equals(f.value())));
    }

    @Test
    void leRappelAnnonceUnEntretienDansMoinsDeVingtQuatreHeures() {
        ArgumentCaptor<EmailTemplates.Email> email = email();

        notifier.interviewReminder(app);

        verify(notifications).pushAndMail(eq("interview:9"), eq(12L), eq("Rappel d'entretien"), anyString(),
                eq("INTERVIEW"), eq(MailService.Kind.INTERVIEW), anyString(), email.capture(), any());
        assertTrue(email.getValue().paragraphs().get(0).contains("moins de 24 heures"));
        assertEquals("Voir les détails", email.getValue().ctaLabel());
        assertEquals("Rappel : votre entretien approche", email.getValue().heading());
    }

    @Test
    void leTableauDeFaitsPorteLeCreneauLaNatureEtLeMode() {
        ArgumentCaptor<EmailTemplates.Email> email = email();

        notifier.interviewScheduled(app, false);

        verify(notifications).pushAndMail(anyString(), eq(12L), anyString(), anyString(), anyString(),
                any(MailService.Kind.class), anyString(), email.capture(), any());
        List<String> faits = email.getValue().facts().stream().map(f -> f.label() + "=" + f.value()).toList();
        assertTrue(faits.stream().anyMatch(f -> f.startsWith("Quand=") && f.contains("lundi 4 mars 2030 à 09h00")),
                faits.toString());
        assertTrue(faits.contains("Nature=Entretien technique"), faits.toString());
        assertTrue(faits.contains("Mode=Visioconférence"), faits.toString());
        assertTrue(faits.contains("Lien de visio=https://meet.example.com/abc"), faits.toString());
        assertTrue(faits.contains("Avec=Mme Alaoui"), faits.toString());
    }

    @Test
    void aucunMessageDentretienNattendDeReponseDuCandidat() {
        notifier.interviewScheduled(app, false);
        notifier.interviewScheduled(app, true);
        notifier.interviewReminder(app);
        interviews.cancel(app, "Poste pourvu.");
        notifier.interviewCancelled(app);

        ArgumentCaptor<String> titres = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> corps = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> sujets = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<EmailTemplates.Email> emails = email();
        verify(notifications, times(4)).pushAndMail(anyString(), eq(12L), titres.capture(), corps.capture(),
                anyString(), any(MailService.Kind.class), sujets.capture(), emails.capture(), any());

        List<String> textes = new ArrayList<>(titres.getAllValues());
        textes.addAll(corps.getAllValues());
        textes.addAll(sujets.getAllValues());
        for (EmailTemplates.Email e : emails.getAllValues()) {
            textes.add(e.heading());
            textes.addAll(e.paragraphs());
            if (e.highlight() != null) textes.add(e.highlight());
            if (e.ctaLabel() != null) textes.add(e.ctaLabel());
            e.facts().forEach(f -> textes.add(f.label() + " " + f.value()));
        }
        for (String texte : textes) {
            String bas = texte.toLowerCase();
            assertFalse(bas.contains("confirmer"), texte);
            assertFalse(bas.contains("confirmation"), texte);
            assertFalse(bas.contains("indisponib"), texte);
            assertFalse(bas.contains("votre réponse"), texte);
            assertFalse(bas.contains("proposer un"), texte);
        }
    }
}
