package ma.ensat.hirelink.service;

import ma.ensat.hirelink.dto.ApiDtos.InterviewRequest;
import ma.ensat.hirelink.model.Application;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Règles d'un entretien (type, nature, lieu ou lien selon le mode, fuseau, durée), report, annulation,
 * réponse du candidat et export calendrier (.ics). Ne touche ni aux droits ni à la base : l'appelant s'en charge.
 */
@Service
public class InterviewService {

    public static final String SCHEDULED = "SCHEDULED";
    public static final String CANCELLED = "CANCELLED";
    public static final String PENDING = "PENDING";
    public static final String CONFIRMED = "CONFIRMED";
    public static final String UNAVAILABLE = "UNAVAILABLE";

    public static final Map<String, String> TYPE_FR = Map.of(
            "ONSITE", "Sur site", "VISIO", "Visioconférence", "PHONE", "Téléphone");
    public static final Map<String, String> KIND_FR = Map.of(
            "HR", "Entretien RH", "TECHNICAL", "Entretien technique", "MANAGER", "Entretien avec le manager", "FINAL", "Entretien final");

    private static final String DEFAULT_ZONE = "Africa/Casablanca";
    private static final int DEFAULT_DURATION = 45;
    private static final Set<String> ZONES = Set.of("Africa/Casablanca", "Europe/Paris", "Europe/London", "UTC");
    private static final DateTimeFormatter ICS_STAMP = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneId.of("UTC"));
    private static final DateTimeFormatter HUMAN = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy 'à' HH'h'mm", java.util.Locale.FRENCH);

    /** Valide la demande et applique l'entretien (nouvelle planification ou report) : réponse du candidat remise à zéro. */
    public void schedule(Application app, InterviewRequest req) {
        String type = req.type().trim().toUpperCase();
        if (!TYPE_FR.containsKey(type)) throw bad("Type d'entretien invalide.");
        String kind = req.kind().trim().toUpperCase();
        if (!KIND_FR.containsKey(kind)) throw bad("Nature d'entretien invalide.");
        String zone = req.timeZone() == null || req.timeZone().isBlank() ? DEFAULT_ZONE : req.timeZone().trim();
        if (!ZONES.contains(zone)) throw bad("Fuseau horaire non pris en charge.");
        Instant at = req.startsAt().atZone(ZoneId.of(zone)).toInstant();
        if (at.isBefore(Instant.now())) throw bad("Le créneau d'entretien est dans le passé.");
        String location = clean(req.location());
        String video = clean(req.videoUrl());
        String phone = clean(req.phone());
        switch (type) {
            case "ONSITE" -> require(location, "Indiquez l'adresse de l'entretien.");
            case "VISIO" -> require(video, "Indiquez le lien de visioconférence.");
            default -> require(phone, "Indiquez le numéro de téléphone de l'entretien.");
        }
        app.setInterviewType(type);
        app.setInterviewKind(kind);
        app.setInterviewAt(at);
        app.setInterviewTimeZone(zone);
        app.setInterviewDurationMinutes(req.durationMinutes() == null ? DEFAULT_DURATION : req.durationMinutes());
        app.setInterviewLocation("ONSITE".equals(type) ? location : null);
        app.setInterviewMapUrl("ONSITE".equals(type) ? clean(req.mapUrl()) : null);
        app.setInterviewVideoUrl("VISIO".equals(type) ? video : null);
        app.setInterviewPhone("PHONE".equals(type) ? phone : null);
        app.setInterviewParticipants(clean(req.participants()));
        app.setInterviewNotes(clean(req.instructions()));
        app.setInterviewStatus(SCHEDULED);
        app.setInterviewCancelReason(null);
        app.setInterviewConfirmation(PENDING);
        app.setInterviewCandidateNote(null);
        app.setInterviewReminderSent(false);
    }

    public void cancel(Application app, String reason) {
        requireActive(app);
        app.setInterviewStatus(CANCELLED);
        app.setInterviewCancelReason(reason.trim());
    }

    public void answer(Application app, boolean available, String note) {
        requireActive(app);
        app.setInterviewConfirmation(available ? CONFIRMED : UNAVAILABLE);
        app.setInterviewCandidateNote(available ? null : clean(note));
    }

    public boolean isActive(Application app) {
        return app.getInterviewAt() != null && !CANCELLED.equals(app.getInterviewStatus());
    }

    private void requireActive(Application app) {
        if (!isActive(app)) throw bad("Aucun entretien planifié sur ce dossier.");
    }

    /** « jeudi 5 novembre 2026 à 10h30 (Africa/Casablanca) ». */
    public String when(Application app) {
        ZoneId zone = zoneOf(app);
        return HUMAN.format(app.getInterviewAt().atZone(zone)) + " (" + zone.getId() + ")";
    }

    /** Lieu, lien ou numéro selon le mode, pour les messages. */
    public String whereOrHow(Application app) {
        return switch (app.getInterviewType() == null ? "" : app.getInterviewType()) {
            case "ONSITE" -> app.getInterviewLocation();
            case "VISIO" -> app.getInterviewVideoUrl();
            case "PHONE" -> "Téléphone : " + app.getInterviewPhone();
            default -> "";
        };
    }

    /** Résumé court : « jeudi 5 novembre 2026 à 10h30 · Visioconférence · Entretien technique ». */
    public String summary(Application app) {
        return when(app) + " · " + TYPE_FR.getOrDefault(app.getInterviewType(), "") + " · " + KIND_FR.getOrDefault(app.getInterviewKind(), "Entretien");
    }

    /** Événement iCalendar (RFC 5545), importable dans Google Agenda, Outlook ou Apple Calendrier. */
    public byte[] ics(Application app) {
        int minutes = app.getInterviewDurationMinutes() == null ? DEFAULT_DURATION : app.getInterviewDurationMinutes();
        List<String> lines = new ArrayList<>(List.of(
                "BEGIN:VCALENDAR", "VERSION:2.0", "PRODID:-//HireLink//Entretiens//FR", "CALSCALE:GREGORIAN", "METHOD:PUBLISH",
                "BEGIN:VEVENT",
                "UID:interview-" + app.getId() + "@hirelink",
                "DTSTAMP:" + ICS_STAMP.format(Instant.now()),
                "DTSTART:" + ICS_STAMP.format(app.getInterviewAt()),
                "DTEND:" + ICS_STAMP.format(app.getInterviewAt().plusSeconds(minutes * 60L)),
                "SUMMARY:" + escape(KIND_FR.getOrDefault(app.getInterviewKind(), "Entretien") + " — " + app.getJobOffer().getTitle()),
                "STATUS:" + (isActive(app) ? "CONFIRMED" : "CANCELLED")));
        String where = whereOrHow(app);
        if (where != null && !where.isBlank()) lines.add("LOCATION:" + escape(where));
        List<String> description = new ArrayList<>();
        description.add("Mode : " + TYPE_FR.getOrDefault(app.getInterviewType(), ""));
        if (app.getInterviewParticipants() != null) description.add("Avec : " + app.getInterviewParticipants());
        if (app.getInterviewMapUrl() != null) description.add("Plan : " + app.getInterviewMapUrl());
        if (app.getInterviewNotes() != null) description.add("Consignes : " + app.getInterviewNotes());
        lines.add("DESCRIPTION:" + escape(String.join("\n", description)));
        lines.add("BEGIN:VALARM");
        lines.add("TRIGGER:-PT1H");
        lines.add("ACTION:DISPLAY");
        lines.add("DESCRIPTION:Entretien dans une heure");
        lines.add("END:VALARM");
        lines.add("END:VEVENT");
        lines.add("END:VCALENDAR");
        StringBuilder out = new StringBuilder();
        lines.forEach(l -> out.append(fold(l)).append("\r\n"));
        return out.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static ZoneId zoneOf(Application app) {
        try {
            return ZoneId.of(app.getInterviewTimeZone() == null ? DEFAULT_ZONE : app.getInterviewTimeZone());
        } catch (DateTimeException e) {
            return ZoneId.of(DEFAULT_ZONE);
        }
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,").replace("\r", "").replace("\n", "\\n");
    }

    /** Lignes de 75 octets au plus (RFC 5545), suite précédée d'une espace. */
    private static String fold(String line) {
        StringBuilder out = new StringBuilder();
        int bytes = 0;
        for (int i = 0; i < line.length(); ) {
            int cp = line.codePointAt(i);
            int size = new String(Character.toChars(cp)).getBytes(StandardCharsets.UTF_8).length;
            if (bytes + size > 74) {
                out.append("\r\n ");
                bytes = 1;
            }
            out.appendCodePoint(cp);
            bytes += size;
            i += Character.charCount(cp);
        }
        return out.toString();
    }

    private static String clean(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static void require(String value, String message) {
        if (value == null) throw bad(message);
    }

    private static ResponseStatusException bad(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
