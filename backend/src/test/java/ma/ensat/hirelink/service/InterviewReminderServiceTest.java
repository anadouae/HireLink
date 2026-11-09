package ma.ensat.hirelink.service;

import ma.ensat.hirelink.model.Application;
import ma.ensat.hirelink.repository.ApplicationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Rappel J-1 : une seule notification, dans les 24 heures qui précèdent un entretien non annulé. */
class InterviewReminderServiceTest {

    private ApplicationRepository applications;
    private EventNotifier notifier;
    private InterviewReminderService service;

    @BeforeEach
    void setUp() {
        applications = mock(ApplicationRepository.class);
        notifier = mock(EventNotifier.class);
        service = new InterviewReminderService(applications, notifier);
        when(applications.claimInterviewReminder(anyLong(), any(), any())).thenReturn(1);
    }

    private static Application entretien() {
        Application app = new Application();
        app.setId(9L);
        app.setInterviewAt(Instant.now().plus(Duration.ofHours(20)));
        app.setInterviewStatus(InterviewService.SCHEDULED);
        return app;
    }

    @Test
    void leRappelEstEnvoyePuisMarqueCommeEnvoye() {
        Application app = entretien();
        when(applications.findInterviewsToRemind(any(), any(), any())).thenReturn(List.of(app));

        service.remindUpcoming();

        verify(notifier, times(1)).interviewReminder(app);
        assertEquals(Boolean.TRUE, app.getInterviewReminderSent());
    }

    @Test
    void plusieursEntretiensSontRappelesChacunUneFois() {
        Application un = entretien();
        Application deux = entretien();
        when(applications.findInterviewsToRemind(any(), any(), any())).thenReturn(List.of(un, deux));

        service.remindUpcoming();

        verify(notifier).interviewReminder(un);
        verify(notifier).interviewReminder(deux);
        assertEquals(Boolean.TRUE, un.getInterviewReminderSent());
        assertEquals(Boolean.TRUE, deux.getInterviewReminderSent());
    }

    @Test
    void leSondageExclutLesEntretiensAnnules() {
        when(applications.findInterviewsToRemind(any(), any(), any())).thenReturn(List.of());

        service.remindUpcoming();

        verify(applications).findInterviewsToRemind(any(Instant.class), any(Instant.class),
                eq(InterviewService.CANCELLED));
    }

    @Test
    void laFenetreDuRappelEstDeVingtQuatreHeures() {
        when(applications.findInterviewsToRemind(any(), any(), any())).thenReturn(List.of());
        ArgumentCaptor<Instant> debut = ArgumentCaptor.forClass(Instant.class);
        ArgumentCaptor<Instant> fin = ArgumentCaptor.forClass(Instant.class);

        service.remindUpcoming();

        verify(applications).findInterviewsToRemind(debut.capture(), fin.capture(), any());
        assertEquals(Duration.ofHours(24), Duration.between(debut.getValue(), fin.getValue()));
        assertTrue(debut.getValue().isAfter(Instant.now().minusSeconds(30)));
    }

    @Test
    void sansEntretienApprochantAucuneNotificationNestEnvoyee() {
        when(applications.findInterviewsToRemind(any(), any(), any())).thenReturn(List.of());

        service.remindUpcoming();

        verify(notifier, never()).interviewReminder(any());
        verify(notifier, never()).interviewScheduled(any(), anyBoolean());
        verify(notifier, never()).interviewCancelled(any());
    }

    @Test
    void uneReservationRefuseeNeDeclencheAucunEnvoi() {
        Application app = entretien();
        when(applications.findInterviewsToRemind(any(), any(), any())).thenReturn(List.of(app));
        when(applications.claimInterviewReminder(anyLong(), any(), any())).thenReturn(0);

        service.remindUpcoming();

        verify(notifier, never()).interviewReminder(any());
        assertEquals(null, app.getInterviewReminderSent());
    }

    @Test
    void unEchecDEnvoiLibereLaReservationPourReessai() {
        Application app = entretien();
        when(applications.findInterviewsToRemind(any(), any(), any())).thenReturn(List.of(app));
        org.mockito.Mockito.doThrow(new IllegalStateException("SMTP indisponible"))
                .when(notifier).interviewReminder(app);

        service.remindUpcoming();

        verify(applications).releaseInterviewReminder(9L);
        assertEquals(null, app.getInterviewReminderSent());
        assertEquals(null, app.getInterviewReminderClaimedAt());
    }
}
