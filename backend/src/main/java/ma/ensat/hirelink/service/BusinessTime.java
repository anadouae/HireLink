package ma.ensat.hirelink.service;

import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Le « aujourd'hui » du produit : toujours l'heure marocaine, jamais le fuseau par défaut de la JVM.
 * Une offre dont la date limite est hier casablancaise doit expirer ici aussi, même sur un serveur réglé sur UTC.
 */
public final class BusinessTime {

    public static final ZoneId ZONE = ZoneId.of("Africa/Casablanca");

    private BusinessTime() {
    }

    public static LocalDate today() {
        return LocalDate.now(ZONE);
    }
}
