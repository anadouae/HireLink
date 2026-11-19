package ma.ensat.hirelink.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/** Bornes de période des journaux d'administration : des jours locaux (Africa/Casablanca), fin incluse. */
final class AdminDates {

    static final ZoneId ZONE = ZoneId.of("Africa/Casablanca");

    private AdminDates() {}

    static Instant dayStart(String day) {
        if (day == null || day.isBlank()) return null;
        try {
            return LocalDate.parse(day.trim()).atStartOfDay(ZONE).toInstant();
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Date de début invalide.");
        }
    }

    static Instant dayEndExclusive(String day) {
        if (day == null || day.isBlank()) return null;
        try {
            return LocalDate.parse(day.trim()).plusDays(1).atStartOfDay(ZONE).toInstant();
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Date de fin invalide.");
        }
    }
}
