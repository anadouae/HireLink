package ma.ensat.hirelink.service;

import ma.ensat.hirelink.controller.ApplicationController;
import ma.ensat.hirelink.dto.ApiDtos;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Décision D1 : l'entretien est une convocation décidée par le recruteur. Ce test verrouille l'absence
 * de toute réponse du candidat (route, méthode, DTO, constante, notification) et la conservation des
 * fonctions gardées : planification, report, annulation, fichier calendrier et rappel automatique J-1.
 */
class EntretienConvocationTest {

    private static List<String> methodes(Class<?> type) {
        return Arrays.stream(type.getDeclaredMethods()).map(Method::getName).toList();
    }

    private static List<String> champs(Class<?> type) {
        return Arrays.stream(type.getDeclaredFields()).map(Field::getName).toList();
    }

    private static Method methode(Class<?> type, String nom) {
        return Arrays.stream(type.getDeclaredMethods()).filter(m -> m.getName().equals(nom)).findFirst().orElse(null);
    }

    @Test
    void leServiceDEntretienNAucuneMethodeDeReponse() {
        assertFalse(methodes(InterviewService.class).contains("answer"), methodes(InterviewService.class).toString());
        assertTrue(methodes(InterviewService.class).contains("schedule"));
        assertTrue(methodes(InterviewService.class).contains("cancel"));
        assertTrue(methodes(InterviewService.class).contains("ics"));
    }

    @Test
    void lesSeulsEtatsDEntretienSontPlanifieEtAnnule() {
        List<String> constantes = champs(InterviewService.class).stream()
                .filter(c -> c.equals(c.toUpperCase())).toList();
        assertTrue(constantes.contains("SCHEDULED"), constantes.toString());
        assertTrue(constantes.contains("CANCELLED"), constantes.toString());
        assertFalse(constantes.contains("PENDING"), constantes.toString());
        assertFalse(constantes.contains("UNAVAILABLE"), constantes.toString());
        assertFalse(constantes.contains("CONFIRMED"), constantes.toString());
        assertEquals("SCHEDULED", InterviewService.SCHEDULED);
        assertEquals("CANCELLED", InterviewService.CANCELLED);
    }

    @Test
    void leServiceCandidatureNeTraitePlusDeReponseDuCandidat() {
        assertFalse(methodes(ApplicationService.class).contains("answerInterview"),
                methodes(ApplicationService.class).toString());
        assertTrue(methodes(ApplicationService.class).contains("rescheduleInterview"));
        assertTrue(methodes(ApplicationService.class).contains("cancelInterview"));
        assertTrue(methodes(ApplicationService.class).contains("interviewCalendar"));
    }

    @Test
    void aucuneNotificationNeTraiteUneReponseDuCandidat() {
        assertFalse(methodes(EventNotifier.class).contains("interviewAnswered"),
                methodes(EventNotifier.class).toString());
        assertTrue(methodes(EventNotifier.class).contains("interviewScheduled"));
        assertTrue(methodes(EventNotifier.class).contains("interviewCancelled"));
        assertTrue(methodes(EventNotifier.class).contains("interviewReminder"));
    }

    @Test
    void laReponseDApiNePortePlusLaConfirmationDuCandidat() {
        List<String> champs = Arrays.stream(ApiDtos.ApplicationResponse.class.getRecordComponents())
                .map(RecordComponent::getName).toList();
        assertFalse(champs.contains("interviewConfirmation"), champs.toString());
        assertFalse(champs.contains("interviewCandidateNote"), champs.toString());
        assertTrue(champs.contains("interviewStatus"), champs.toString());
        assertTrue(champs.contains("interviewCancelReason"), champs.toString());
        assertTrue(champs.contains("interviewAt"), champs.toString());
    }

    @Test
    void laDemandeDeReponseDuCandidatNexistePlusDansLesDto() {
        Set<String> imbriques = Arrays.stream(ApiDtos.class.getDeclaredClasses())
                .map(Class::getSimpleName).collect(Collectors.toSet());
        assertFalse(imbriques.contains("InterviewAnswerRequest"), imbriques.toString());
        assertTrue(imbriques.contains("InterviewRequest"), imbriques.toString());
        assertTrue(imbriques.contains("CancelInterviewRequest"), imbriques.toString());
    }

    @Test
    void laRouteDeReponseDuCandidatNexistePlus() {
        assertFalse(methodes(ApplicationController.class).contains("answerInterview"),
                methodes(ApplicationController.class).toString());
        List<String> routes = Arrays.stream(ApplicationController.class.getDeclaredMethods())
                .flatMap(m -> Arrays.stream(m.getAnnotations()))
                .filter(a -> a instanceof PostMapping)
                .flatMap(a -> Arrays.stream(((PostMapping) a).value()))
                .toList();
        assertFalse(routes.contains("/{id}/interview/answer"), routes.toString());
        assertTrue(routes.contains("/{id}/interview/cancel"), routes.toString());
    }

    @Test
    void planifierEtAnnulerSontReservesAuRecruteur() {
        assertEquals("hasRole('RECRUTEUR')", preAuthorize(methode(ApplicationController.class, "rescheduleInterview")));
        assertEquals("hasRole('RECRUTEUR')", preAuthorize(methode(ApplicationController.class, "cancelInterview")));
    }

    @Test
    void lesRoutesDEntretienConserveesSontCellesAttendues() {
        Method planifier = methode(ApplicationController.class, "rescheduleInterview");
        Method annuler = methode(ApplicationController.class, "cancelInterview");
        Method calendrier = methode(ApplicationController.class, "interviewCalendar");
        assertNotNull(planifier);
        assertNotNull(annuler);
        assertNotNull(calendrier);
        assertArrayEquals(new String[]{"/{id}/interview"}, planifier.getAnnotation(PutMapping.class).value());
        assertArrayEquals(new String[]{"/{id}/interview/cancel"}, annuler.getAnnotation(PostMapping.class).value());
        assertArrayEquals(new String[]{"/{id}/interview.ics"}, calendrier.getAnnotation(GetMapping.class).value());
    }

    /** Le fichier .ics garde le candidat et l'équipe comme destinataires : la consultation est conservée. */
    @Test
    void leRappelAutomatiqueJMoinsUnEstConserve() throws NoSuchMethodException {
        Scheduled scheduled = InterviewReminderService.class.getMethod("remindUpcoming").getAnnotation(Scheduled.class);
        assertNotNull(scheduled);
        assertTrue(scheduled.fixedDelayString().contains("900000"), scheduled.fixedDelayString());
        assertTrue(scheduled.initialDelayString().contains("60000"), scheduled.initialDelayString());
    }

    private static String preAuthorize(Method m) {
        assertNotNull(m, "méthode absente du contrôleur");
        PreAuthorize garde = m.getAnnotation(PreAuthorize.class);
        assertNotNull(garde, "aucun garde-fou sur " + m.getName());
        return garde.value();
    }
}
