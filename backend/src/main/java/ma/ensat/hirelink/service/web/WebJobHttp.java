package ma.ensat.hirelink.service.web;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Client HTTP partagé pour sources Web publiques (timeouts courts, User-Agent explicite).
 * Délais alignés sur le délai par source de l'agrégateur (3 s par défaut) : une source lente libère
 * son fil d'exécution peu après avoir été ignorée.
 */
public final class WebJobHttp {

    /** Connexion TCP/TLS. */
    static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);
    /** Réponse complète d'une source (GET JSON / RSS). */
    static final Duration READ_TIMEOUT = Duration.ofMillis(3500);
    /** Repli Tavily/Serper (désactivé par défaut). */
    static final Duration POST_TIMEOUT = Duration.ofSeconds(5);

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private WebJobHttp() {}

    public static String getJson(String url) throws Exception {
        return get(url, "application/json");
    }

    /** POST JSON (Tavily / Serper fallback) — timeout court, pas de secrets dans l'URL. */
    public static String postJson(String url, String jsonBody) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .timeout(POST_TIMEOUT)
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .header("User-Agent", "HireLink/1.0 (job-discovery; educational; +https://hirelink.local)")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody == null ? "{}" : jsonBody))
                .build();
        return read(req, url);
    }

    /** Flux texte/XML publics (ex. RSS We Work Remotely) — pas de scraping HTML de pages offre. */
    public static String getText(String url, String accept) throws Exception {
        return get(url, accept == null || accept.isBlank() ? "*/*" : accept);
    }

    private static String get(String url, String accept) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .timeout(READ_TIMEOUT)
                .header("Accept", accept)
                .header("User-Agent", "HireLink/1.0 (job-discovery; educational; +https://hirelink.local)")
                .GET()
                .build();
        return read(req, url);
    }

    private static String read(HttpRequest req, String url) throws Exception {
        HttpResponse<String> res = CLIENT.send(req, HttpResponse.BodyHandlers.ofString());
        int code = res.statusCode();
        if (code < 200 || code >= 300) {
            throw new IllegalStateException("HTTP " + code + " pour " + URI.create(url).getHost());
        }
        String body = res.body() == null ? "" : res.body();
        if (body.length() > 4_000_000) {
            throw new IllegalStateException("Réponse trop volumineuse de " + URI.create(url).getHost());
        }
        return body;
    }

    /** URL d'annonce utilisable (https deep-link), pas une page d'accueil générique. */
    public static String deepJobUrl(String url) {
        if (url == null || url.isBlank()) return null;
        String u = url.trim();
        if (!u.regionMatches(true, 0, "https://", 0, 8)) return null;
        String lower = u.toLowerCase(java.util.Locale.ROOT);
        // Rejeter pages d'accueil / recherches génériques
        if (lower.matches("https://[^/]+/?")) return null;
        if (lower.contains("/search?") || lower.contains("/jobs?") || lower.endsWith("/jobs")
                || lower.endsWith("/jobs/") || lower.contains("q=") && lower.contains("search")) {
            // Autoriser si path contient un id d'annonce numérique ou slug long
            URI uri = URI.create(u);
            String path = uri.getPath() == null ? "" : uri.getPath();
            if (path.split("/").length < 3) return null;
        }
        return u;
    }
}
