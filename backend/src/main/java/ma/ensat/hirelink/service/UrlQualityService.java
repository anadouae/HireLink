package ma.ensat.hirelink.service;

import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Classification heuristique des URL d'offres externes - sans telechargement HTML (pas de scraping).
 * Un controle HTTP optionnel peut enrichir le resultat via {@link #classifyAfterRedirect}.
 */
@Service
public class UrlQualityService {

    private static final Set<String> GENERIC_SEGMENTS = Set.of(
            "", "jobs", "job", "careers", "career", "offre", "offres", "emploi", "emplois",
            "vacancies", "vacancy", "opportunities", "opportunity", "positions", "join",
            "join-us", "joinus", "recruitment", "recrutement", "nos-offres", "our-jobs"
    );

    private static final Pattern LOOKS_LIKE_ID = Pattern.compile(
            ".*\\d{2,}.*|.*[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}.*",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern NUMERIC_SEGMENT = Pattern.compile("^\\d+$");
    private static final Pattern APPLY_HINT = Pattern.compile(
            ".*(apply|application|candidat|postuler|remote-jobs|/j/|/job/|/jobs/|/position/|/posting/|/offre/|/offres/).*",
            Pattern.CASE_INSENSITIVE);

    public record Assessment(UrlQuality quality, String reason) {}

    /** Classe l'URL d'origine (annonce). Ne fabrique aucun lien. */
    public Assessment classifyOrigin(String url) {
        return classify(url, false);
    }

    /** Classe l'URL de candidature si fournie ; ne substitue pas l'origine. */
    public Assessment classifyApply(String url) {
        if (url == null || url.isBlank()) {
            return new Assessment(UrlQuality.UNKNOWN, "Aucun lien de candidature fourni");
        }
        return classify(url, true);
    }

    /**
     * Apres une redirection HTTP deja resolue ailleurs (tests / sonde admin).
     * Si la cible finale est une homepage, la qualite devient GENERIC_HOME.
     */
    public Assessment classifyAfterRedirect(String originalUrl, String finalUrl, int status) {
        if (status <= 0 || status >= 400) {
            return new Assessment(UrlQuality.INVALID, "URL inaccessible (HTTP " + status + ")");
        }
        Assessment original = classifyOrigin(originalUrl);
        if (original.quality() == UrlQuality.INVALID) return original;
        if (finalUrl == null || finalUrl.isBlank()) return original;
        Assessment landed = classifyOrigin(finalUrl);
        if (landed.quality() == UrlQuality.GENERIC_HOME || landed.quality() == UrlQuality.INVALID) {
            return new Assessment(UrlQuality.GENERIC_HOME, "Redirection vers une page generique : " + landed.reason());
        }
        if (original.quality() == UrlQuality.DEEP || landed.quality() == UrlQuality.DEEP) {
            return new Assessment(UrlQuality.DEEP, "Cible apres redirection specifique");
        }
        return landed.quality() == UrlQuality.UNKNOWN ? landed : original;
    }

    private Assessment classify(String raw, boolean applyContext) {
        if (raw == null || raw.isBlank()) {
            return new Assessment(UrlQuality.INVALID, "URL vide");
        }
        String trimmed = raw.trim();
        if (trimmed.length() > 500) {
            return new Assessment(UrlQuality.INVALID, "URL trop longue");
        }
        if (trimmed.contains(" ") || trimmed.contains("\n") || trimmed.contains("\r")) {
            return new Assessment(UrlQuality.INVALID, "URL contenant des espaces");
        }
        URI uri;
        try {
            uri = URI.create(trimmed);
        } catch (IllegalArgumentException e) {
            return new Assessment(UrlQuality.INVALID, "URL illisible");
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!"https".equals(scheme)) {
            return new Assessment(UrlQuality.INVALID, "Seul https est accepte");
        }
        String host = uri.getHost();
        if (host == null || host.isBlank() || host.equalsIgnoreCase("localhost")) {
            return new Assessment(UrlQuality.INVALID, "Hote invalide");
        }
        String path = uri.getPath() == null ? "" : uri.getPath().trim();
        while (path.endsWith("/") && path.length() > 1) path = path.substring(0, path.length() - 1);
        String normalized = path.toLowerCase(Locale.ROOT);
        String last = lastSegment(normalized);

        if (path.isEmpty() || "/".equals(path)) {
            return new Assessment(UrlQuality.GENERIC_HOME, "Racine du site (homepage)");
        }
        if (GENERIC_SEGMENTS.contains(stripSlash(normalized)) || GENERIC_SEGMENTS.contains(last)) {
            if (segmentCount(normalized) <= 1) {
                return new Assessment(UrlQuality.GENERIC_HOME, "Index carrieres generique");
            }
        }
        if (segmentCount(normalized) == 1 && GENERIC_SEGMENTS.contains(last)) {
            return new Assessment(UrlQuality.GENERIC_HOME, "Page carrieres sans identifiant d'annonce");
        }

        boolean idLike = LOOKS_LIKE_ID.matcher(normalized).matches()
                || LOOKS_LIKE_ID.matcher(last).matches()
                || NUMERIC_SEGMENT.matcher(last).matches();
        boolean applyLike = APPLY_HINT.matcher(normalized).matches();
        if (idLike || (applyContext && applyLike && segmentCount(normalized) >= 2)) {
            return new Assessment(UrlQuality.DEEP, idLike ? "Chemin d'annonce avec identifiant" : "Chemin de candidature specifique");
        }
        if (applyLike && segmentCount(normalized) >= 2) {
            return new Assessment(UrlQuality.DEEP, "Chemin d'offre/candidature structure");
        }
        if (segmentCount(normalized) >= 2 && last.length() >= 8 && !GENERIC_SEGMENTS.contains(last)) {
            return new Assessment(UrlQuality.UNKNOWN, "Chemin multi-segments sans identifiant numerique clair");
        }
        if (segmentCount(normalized) == 1 && last.length() >= 8 && !GENERIC_SEGMENTS.contains(last)) {
            return new Assessment(UrlQuality.UNKNOWN, "Slug unique ambigu");
        }
        return new Assessment(UrlQuality.UNKNOWN, "URL https ambigue - non garantie comme annonce exacte");
    }

    private static String stripSlash(String path) {
        if (path.startsWith("/")) path = path.substring(1);
        if (path.endsWith("/")) path = path.substring(0, path.length() - 1);
        return path;
    }

    private static String lastSegment(String normalized) {
        String p = stripSlash(normalized);
        int i = p.lastIndexOf('/');
        return i < 0 ? p : p.substring(i + 1);
    }

    private static int segmentCount(String normalized) {
        String p = stripSlash(normalized);
        if (p.isEmpty()) return 0;
        return p.split("/").length;
    }
}