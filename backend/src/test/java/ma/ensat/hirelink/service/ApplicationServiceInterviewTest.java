package ma.ensat.hirelink.service;

import ma.ensat.hirelink.dto.ApiDtos;
import ma.ensat.hirelink.model.Application;
import ma.ensat.hirelink.model.ApplicationStatus;
import ma.ensat.hirelink.model.JobOffer;
import ma.ensat.hirelink.model.Membership;
import ma.ensat.hirelink.model.OrgRole;
import ma.ensat.hirelink.model.Role;
import ma.ensat.hirelink.model.User;
import ma.ensat.hirelink.repository.ApplicationRepository;
import ma.ensat.hirelink.repository.JobOfferRepository;
import ma.ensat.hirelink.repository.MembershipRepository;
import ma.ensat.hirelink.repository.RefusalReasonRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pipeline des entretiens : droits du recruteur et du responsable du poste (RG-12), journal d'audit,
 * notifications et fichier calendrier. L'entretien est une convocation : le candidat ne répond pas.
 */
class ApplicationServiceInterviewTest {

    private static final LocalDateTime CRENEAU = LocalDateTime.of(2030, 3, 4, 9, 0);

    private final InterviewService interviews = new InterviewService();
    private ApplicationRepository applicationRepository;
    private OrganizationService organizationService;
    private AuditService audit;
    private EventNotifier notifier;
    private ApplicationService service;
    private Application app;
    private User candidat;
    private User recruteur;

    @BeforeEach
    void setUp() {
        applicationRepository = mock(ApplicationRepository.class);
        organizationService = mock(OrganizationService.class);
        audit = mock(AuditService.class);
        notifier = mock(EventNotifier.class);
        SkillMatchingService matching = mock(SkillMatchingService.class);
        ResponsivenessService responsiveness = mock(ResponsivenessService.class);
        service = new ApplicationService(applicationRepository, mock(JobOfferRepository.class), matching,
                mock(TimelineService.class), mock(RefusalReasonRepository.class), mock(NotificationService.class),
                organizationService, mock(MembershipRepository.class), mock(CvStorage.class), audit,
                interviews, notifier, responsiveness);

        candidat = utilisateur(12L, Role.CANDIDAT, "Sara Bennani");
        recruteur = utilisateur(7L, Role.RECRUTEUR, "Karim Idrissi");
        recruteur.setAssignedOrgId(26L);

        JobOffer offre = new JobOffer();
        offre.setId(4L);
        offre.setTitle("Développeur Java");
        offre.setRecruiter(recruteur);
        offre.setResponseSlaDays(5);

        app = new Application();
        app.setId(9L);
        app.setCandidate(candidat);
        app.setJobOffer(offre);
        app.setStatus(ApplicationStatus.INTERVIEW);

        when(applicationRepository.findById(9L)).thenReturn(Optional.of(app));
        when(applicationRepository.save(any(Application.class))).thenAnswer(i -> i.getArgument(0));
        when(matching.evaluate(any(), any())).thenReturn(
                new SkillMatchingService.Compatibility(0.42, false, 0.42, List.of(), List.of(), List.of()));
        when(matching.evaluateIfAvailable(any(), any())).thenReturn(
                new SkillMatchingService.Compatibility(0.42, false, 0.42, List.of(), List.of(), List.of()));
        when(responsiveness.signalOf(anyLong())).thenReturn(ResponsivenessService.Signal.NONE);
    }

    private static User utilisateur(Long id, Role role, String nom) {
        User u = new User();
        u.setId(id);
        u.setRole(role);
        u.setFullName(nom);
        u.setEmail("user" + id + "@example.com");
        return u;
    }

    private void membreDe(User acteur, OrgRole role) {
        Membership m = new Membership();
        m.setUser(acteur);
        m.setOrgRole(role);
        when(organizationService.requireMembership(acteur)).thenReturn(m);
    }

    private static ApiDtos.InterviewRequest visio() {
        return new ApiDtos.InterviewRequest("VISIO", "TECHNICAL", CRENEAU, "Africa/Casablanca", 60,
                null, null, "https://meet.example.com/abc", "Mme Alaoui", null);
    }

    @Test
    void planifierUnEntretienJournaliseEtNotifie() {
        membreDe(recruteur, OrgRole.RECRUTEUR);

        ApiDtos.ApplicationResponse reponse = service.rescheduleInterview(recruteur, 9L, visio());

        assertEquals(InterviewService.SCHEDULED, reponse.interviewStatus());
        assertEquals("SCHEDULED", app.getInterviewStatus());
        verify(organizationService).assertSameOrg(recruteur, app.getJobOffer());
        verify(audit).ok(eq("INTERVIEW_PLANNED"), eq(recruteur), eq("APPLICATION"), eq(9L), any());
        verify(notifier).interviewScheduled(app, false);
        verify(notifier, never()).interviewCancelled(any());
    }

    @Test
    void reporterUnEntretienJournaliseLeReportEtLannonce() {
        membreDe(recruteur, OrgRole.RECRUTEUR);
        service.rescheduleInterview(recruteur, 9L, visio());

        ApiDtos.ApplicationResponse reponse = service.rescheduleInterview(recruteur, 9L,
                new ApiDtos.InterviewRequest("ONSITE", "FINAL", CRENEAU.plusDays(3), "Europe/Paris", 90,
                        "12 rue des Fleurs, Paris", null, null, null, null));

        assertEquals("ONSITE", reponse.interviewType());
        verify(audit).ok(eq("INTERVIEW_RESCHEDULED"), eq(recruteur), eq("APPLICATION"), eq(9L), any());
        verify(notifier).interviewScheduled(app, true);
    }

    @Test
    void annulerUnEntretienJournaliseLeMotifEtPreviensLeCandidat() {
        membreDe(recruteur, OrgRole.RECRUTEUR);
        service.rescheduleInterview(recruteur, 9L, visio());

        ApiDtos.ApplicationResponse reponse = service.cancelInterview(recruteur, 9L, "Poste pourvu en interne.");

        assertEquals(InterviewService.CANCELLED, reponse.interviewStatus());
        assertEquals("Poste pourvu en interne.", reponse.interviewCancelReason());
        verify(audit).ok("INTERVIEW_CANCELLED", recruteur, "APPLICATION", 9L, "Poste pourvu en interne.");
        verify(notifier).interviewCancelled(app);
    }

    @Test
    void laReponseDApiNePorteAucuneReponseDuCandidat() {
        membreDe(recruteur, OrgRole.RECRUTEUR);

        ApiDtos.ApplicationResponse reponse = service.rescheduleInterview(recruteur, 9L, visio());

        List<String> champs = java.util.Arrays.stream(ApiDtos.ApplicationResponse.class.getRecordComponents())
                .map(java.lang.reflect.RecordComponent::getName).toList();
        assertTrue(champs.contains("interviewStatus"), champs.toString());
        assertTrue(champs.contains("interviewCancelReason"), champs.toString());
        assertTrue(champs.stream().noneMatch(c -> c.toLowerCase().contains("confirmation")), champs.toString());
        assertTrue(champs.stream().noneMatch(c -> c.toLowerCase().contains("candidatenote")), champs.toString());
        assertEquals(reponse.interviewStatus(), app.getInterviewStatus());
    }

    @Test
    void unResponsableDePosteNonAssigneNePeutPasPlanifier() {
        User hm = utilisateur(8L, Role.RECRUTEUR, "Nadia El Amrani");
        membreDe(hm, OrgRole.HIRING_MANAGER);
        app.setAssignedHmUserId(99L);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.rescheduleInterview(hm, 9L, visio()));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        assertEquals("HM : dossier non assigné (R14)", ex.getReason());
        verify(notifier, never()).interviewScheduled(any(), anyBoolean());
        verify(applicationRepository, never()).save(any());
    }

    @Test
    void leResponsableDePosteAssignePeutReporterEtAnnuler() {
        User hm = utilisateur(8L, Role.RECRUTEUR, "Nadia El Amrani");
        app.setAssignedHmUserId(8L);

        service.rescheduleInterview(hm, 9L, visio());

        verify(organizationService).assertSameOrg(hm, app.getJobOffer());
        verify(organizationService, never()).requireMembership(any());
        verify(notifier).interviewScheduled(app, false);
        assertEquals(InterviewService.SCHEDULED, app.getInterviewStatus());

        service.cancelInterview(hm, 9L, "Emploi du temps du responsable du poste.");
        verify(notifier).interviewCancelled(app);
    }

    @Test
    void lAdministrateurPlanifieSansControleDOrganisation() {
        User admin = utilisateur(2L, Role.ADMINISTRATEUR, "Admin HireLink");

        service.rescheduleInterview(admin, 9L, visio());

        verify(organizationService, never()).assertSameOrg(any(), any());
        verify(organizationService, never()).requireMembership(any());
        verify(notifier).interviewScheduled(app, false);
    }

    @Test
    void unRecruteurDuneAutreOrganisationEstRefuse() {
        membreDe(recruteur, OrgRole.RECRUTEUR);
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "Organisation différente"))
                .when(organizationService).assertSameOrg(any(), any());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.rescheduleInterview(recruteur, 9L, visio()));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        verify(notifier, never()).interviewScheduled(any(), anyBoolean());
        verify(audit, never()).ok(any(), any(), any(), any(), any());
    }

    @Test
    void planifierSurUnDossierQuiNestPasALetapeEntretienEstRefuse() {
        membreDe(recruteur, OrgRole.RECRUTEUR);
        app.setStatus(ApplicationStatus.SHORTLISTED);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.rescheduleInterview(recruteur, 9L, visio()));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertEquals("Ce dossier n'est pas à l'étape entretien.", ex.getReason());
        verify(notifier, never()).interviewScheduled(any(), anyBoolean());
    }

    @Test
    void annulerUnEntretienDejaAnnuleEstRefuse() {
        membreDe(recruteur, OrgRole.RECRUTEUR);
        service.rescheduleInterview(recruteur, 9L, visio());
        service.cancelInterview(recruteur, 9L, "Poste pourvu.");

        assertThrows(ResponseStatusException.class,
                () -> service.cancelInterview(recruteur, 9L, "Autre motif"));
    }

    @Test
    void leCandidatTelechargeLeFichierCalendrierDeSonEntretien() {
        membreDe(recruteur, OrgRole.RECRUTEUR);
        service.rescheduleInterview(recruteur, 9L, visio());

        ApplicationService.CalendarFile fichier = service.interviewCalendar(candidat, 9L);

        assertEquals("entretien-hirelink-9.ics", fichier.filename());
        assertTrue(new String(fichier.content(), java.nio.charset.StandardCharsets.UTF_8)
                .contains("STATUS:CONFIRMED"));
        verify(organizationService, never()).assertCanAccessApplication(any(), any());
    }

    @Test
    void unEntretienAnnuleNeDonnePlusDeFichierCalendrier() {
        membreDe(recruteur, OrgRole.RECRUTEUR);
        service.rescheduleInterview(recruteur, 9L, visio());
        service.cancelInterview(recruteur, 9L, "Poste pourvu.");

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.interviewCalendar(candidat, 9L));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        assertEquals("Cet entretien a été annulé.", ex.getReason());
    }

    @Test
    void unDossierSansEntretienNeDonnePasDeFichierCalendrier() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.interviewCalendar(candidat, 9L));

        assertEquals("Aucun entretien planifié", ex.getReason());
    }

    @Test
    void unTiersNePeutPasTelechargerLeFichierCalendrier() {
        User etranger = utilisateur(30L, Role.RECRUTEUR, "Autre Recruteur");
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "Organisation différente"))
                .when(organizationService).assertCanAccessApplication(any(), any());

        assertThrows(ResponseStatusException.class, () -> service.interviewCalendar(etranger, 9L));
    }
}
