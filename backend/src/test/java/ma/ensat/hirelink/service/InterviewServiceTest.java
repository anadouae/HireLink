package ma.ensat.hirelink.service;

import ma.ensat.hirelink.dto.ApiDtos.InterviewRequest;
import ma.ensat.hirelink.model.Application;
import ma.ensat.hirelink.model.JobOffer;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Règles d'un entretien : convocation décidée par le recruteur, report, annulation et fichier calendrier. */
class InterviewServiceTest {

    private final InterviewService service = new InterviewService();

    /** Créneau futur fixe : deux appels à {@code now()} ne donneraient pas la même valeur. */
    private static final LocalDateTime CRENEAU = LocalDateTime.of(2030, 3, 4, 9, 0);

    private static Application dossier() {
        JobOffer offer = new JobOffer();
        offer.setId(4L);
        offer.setTitle("Développeur Java");
        Application app = new Application();
        app.setId(9L);
        app.setJobOffer(offer);
        return app;
    }

    private static InterviewRequest demande(String type, LocalDateTime startsAt, String zone,
                                            String adresse, String lien) {
        return new InterviewRequest(type, "TECHNICAL", startsAt, zone, 60,
                adresse, null, lien, "Mme Alaoui", null);
    }

    private static InterviewRequest visio(LocalDateTime startsAt) {
        return demande("VISIO", startsAt, "Africa/Casablanca", null, "https://meet.example.com/abc");
    }

    @Test
    void planifierUneVisioRenseigneLeCreneauEtLeLien() {
        Application app = dossier();

        service.schedule(app, visio(CRENEAU));

        assertEquals("VISIO", app.getInterviewType());
        assertEquals("TECHNICAL", app.getInterviewKind());
        assertEquals("Africa/Casablanca", app.getInterviewTimeZone());
        assertEquals(60, app.getInterviewDurationMinutes());
        assertEquals("https://meet.example.com/abc", app.getInterviewVideoUrl());
        assertEquals(InterviewService.SCHEDULED, app.getInterviewStatus());
        assertNull(app.getInterviewCancelReason());
        assertEquals(Boolean.FALSE, app.getInterviewReminderSent());
        assertTrue(service.isActive(app));
    }

    @Test
    void unLieuNEstPasConserveSurUneVisio() {
        Application app = dossier();

        service.schedule(app, demande("VISIO", CRENEAU, "Europe/Paris",
                "12 rue des Fleurs, Paris", "https://meet.example.com/abc"));

        assertNull(app.getInterviewLocation());
        assertEquals("Europe/Paris", app.getInterviewTimeZone());
    }

    @Test
    void uneVisioSansLienEstRefusee() {
        Application app = dossier();

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.schedule(app, demande("VISIO", CRENEAU, "UTC", null, "  ")));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertEquals("Indiquez le lien de visioconférence.", ex.getReason());
        assertNull(app.getInterviewAt());
    }

    @Test
    void unEntretienSurSiteSansAdresseEstRefuse() {
        Application app = dossier();

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.schedule(app, demande("ONSITE", CRENEAU, "Africa/Casablanca", null, null)));

        assertEquals("Indiquez l'adresse de l'entretien.", ex.getReason());
    }

    @Test
    void unEntretienSurSiteGardeSonAdresse() {
        Application app = dossier();

        service.schedule(app, demande("ONSITE", CRENEAU, "Africa/Casablanca",
                "Route de Rabat, Tétouan", null));

        assertEquals("Route de Rabat, Tétouan", app.getInterviewLocation());
        assertNull(app.getInterviewVideoUrl());
        assertEquals("Route de Rabat, Tétouan", service.whereOrHow(app));
    }

    @Test
    void unCreneauDansLePasseEstRefuse() {
        Application app = dossier();

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.schedule(app, visio(LocalDateTime.now().minusHours(2))));

        assertEquals("Le créneau d'entretien est dans le passé.", ex.getReason());
    }

    @Test
    void unFuseauNonPrisEnChargeEstRefuse() {
        Application app = dossier();

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.schedule(app, demande("VISIO", CRENEAU, "America/New_York",
                        null, "https://meet.example.com/abc")));

        assertEquals("Fuseau horaire non pris en charge.", ex.getReason());
    }

    @Test
    void uneNatureInconnueEstRefusee() {
        Application app = dossier();

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.schedule(app, new InterviewRequest("VISIO", "TEST", CRENEAU,
                        "UTC", 45, null, null, "https://meet.example.com/abc", null, null)));

        assertEquals("Nature d'entretien invalide.", ex.getReason());
    }

    @Test
    void leTelephoneNestPlusProposeALaPlanification() {
        Application app = dossier();

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.schedule(app, demande("PHONE", CRENEAU, "UTC",
                        "05 39 97 90 00", null)));

        assertEquals("Choisissez un entretien sur site ou en visioconférence.", ex.getReason());
    }

    @Test
    void laDureeParDefautEstQuaranteCinqMinutes() {
        Application app = dossier();

        service.schedule(app, new InterviewRequest("VISIO", "HR", CRENEAU, null, null,
                null, null, "https://meet.example.com/abc", null, null));

        assertEquals(45, app.getInterviewDurationMinutes());
        assertEquals("Africa/Casablanca", app.getInterviewTimeZone());
    }

    @Test
    void unReportEcraseLeCreneauEtRearmeLeRappel() {
        Application app = dossier();
        service.schedule(app, visio(CRENEAU));
        app.setInterviewReminderSent(true);

        service.schedule(app, demande("ONSITE", CRENEAU.plusDays(3), "Europe/Paris",
                "12 rue des Fleurs, Paris", null));

        assertEquals("ONSITE", app.getInterviewType());
        assertEquals("Europe/Paris", app.getInterviewTimeZone());
        assertEquals(InterviewService.SCHEDULED, app.getInterviewStatus());
        assertEquals(Boolean.FALSE, app.getInterviewReminderSent());
        assertNull(app.getInterviewVideoUrl());
        assertEquals(CRENEAU.plusDays(3).atZone(ZoneId.of("Europe/Paris")).toInstant(), app.getInterviewAt());
    }

    @Test
    void unReportApresUneAnnulationRepartDEntretienPlanifie() {
        Application app = dossier();
        service.schedule(app, visio(CRENEAU));
        service.cancel(app, "Le recruteur est malade.");

        service.schedule(app, visio(CRENEAU.plusDays(2)));

        assertEquals(InterviewService.SCHEDULED, app.getInterviewStatus());
        assertNull(app.getInterviewCancelReason());
        assertTrue(service.isPlanned(app));
    }

    @Test
    void annulerGardeLeCreneauEtNoteLeMotif() {
        Application app = dossier();
        service.schedule(app, visio(CRENEAU));

        service.cancel(app, "  Poste pourvu en interne.  ");

        assertEquals(InterviewService.CANCELLED, app.getInterviewStatus());
        assertEquals("Poste pourvu en interne.", app.getInterviewCancelReason());
        assertFalse(service.isActive(app));
        assertFalse(service.isPlanned(app));
        assertNotNull(app.getInterviewAt());
    }

    @Test
    void annulerSansEntretienEstRefuse() {
        Application app = dossier();

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> service.cancel(app, "Motif"));

        assertEquals("Aucun entretien planifié sur ce dossier.", ex.getReason());
    }

    @Test
    void annulerDeuxFoisEstRefuse() {
        Application app = dossier();
        service.schedule(app, visio(CRENEAU));
        service.cancel(app, "Motif");

        assertThrows(ResponseStatusException.class, () -> service.cancel(app, "Autre motif"));
    }

    @Test
    void leResumeAnnonceLaDateLeModeEtLaNature() {
        Application app = dossier();
        service.schedule(app, new InterviewRequest("VISIO", "FINAL", CRENEAU.withHour(10).withMinute(30),
                "Africa/Casablanca", 45, null, null, "https://meet.example.com/abc", null, null));

        assertEquals("lundi 4 mars 2030 à 10h30 (Africa/Casablanca) · Visioconférence · Entretien final",
                service.summary(app));
    }

    /** Point 6 du lot : STATUS:CONFIRMED est une propriété iCalendar, pas une réponse du candidat. */
    @Test
    void leFichierCalendrierGardeStatusConfirmed() {
        Application app = dossier();
        service.schedule(app, new InterviewRequest("ONSITE", "HR", CRENEAU.withHour(10).withMinute(30),
                "Africa/Casablanca", 45, "Route de Rabat, Tétouan", null, null, null, null));

        String ics = new String(service.ics(app), StandardCharsets.UTF_8);

        assertTrue(ics.contains("STATUS:CONFIRMED"));
        assertTrue(ics.contains("BEGIN:VCALENDAR"));
        assertTrue(ics.contains("BEGIN:VEVENT"));
        assertTrue(ics.contains("UID:interview-9@hirelink"));
        assertTrue(ics.contains("DTSTART:20300304T093000Z"), ics);
        assertTrue(ics.contains("DTEND:20300304T101500Z"), ics);
        assertTrue(ics.contains("LOCATION:Route de Rabat\\, Tétouan"));
        assertTrue(ics.contains("SUMMARY:Entretien RH — Développeur Java"));
        assertTrue(ics.contains("TRIGGER:-PT1H"));
        assertFalse(ics.contains("PENDING"));
        assertFalse(ics.contains("UNAVAILABLE"));
    }

    @Test
    void leFichierCalendrierDUnEntretienAnnulePasseStatusCancelled() {
        Application app = dossier();
        service.schedule(app, visio(CRENEAU));
        service.cancel(app, "Poste pourvu.");

        assertTrue(new String(service.ics(app), StandardCharsets.UTF_8).contains("STATUS:CANCELLED"));
    }

    @Test
    void lesLignesDuFichierCalendrierRespectentLaLimiteDe75Octets() {
        Application app = dossier();
        service.schedule(app, new InterviewRequest("ONSITE", "TECHNICAL", CRENEAU,
                "Africa/Casablanca", 90, "Route de Rabat, Tétouan", null, null, "Mme Alaoui",
                "Apportez votre portfolio complet ainsi que les maquettes du projet de fin d'études."));

        for (String ligne : new String(service.ics(app), StandardCharsets.UTF_8).split("\r\n")) {
            assertTrue(ligne.getBytes(StandardCharsets.UTF_8).length <= 75, "ligne trop longue : " + ligne);
        }
    }

    @Test
    void unLienDeVisioEstDonneTelQuelDansLeFichierCalendrier() {
        Application app = dossier();
        service.schedule(app, visio(CRENEAU));

        assertEquals("https://meet.example.com/abc", service.whereOrHow(app));
        assertTrue(new String(service.ics(app), StandardCharsets.UTF_8).contains("Avec : Mme Alaoui"));
    }
}
