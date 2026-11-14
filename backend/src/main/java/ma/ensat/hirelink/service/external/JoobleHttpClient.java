package ma.ensat.hirelink.service.external;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/** Client HTTP borne pour Jooble (POST JSON). Hosts allowlistes uniquement. */
@Component
public class JoobleHttpClient {

    private final HttpClient http;
    private final Duration timeout;
    private final int maxBodyBytes;
    private final String userAgent;
    private final Set<String> allowedHosts;

    public JoobleHttpClient(
            @Value("${hirelink.external-jobs.jooble.connect-timeout-ms:8000}") int connectTimeoutMs,
            @Value("${hirelink.external-jobs.jooble.request-timeout-ms:15000}") int requestTimeoutMs,
            @Value("${hirelink.external-jobs.jooble.max-body-bytes:2097152}") int maxBodyBytes,
            @Value("${hirelink.external-jobs.jooble.user-agent:HireLink/1.0 (job-discovery; +https://hirelink.ma)}") String userAgent,
            @Value("${hirelink.external-jobs.jooble.allowed-hosts:ma.jooble.org,jooble.org}") String allowedHostsCsv) {
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(Math.max(1000, connectTimeoutMs)))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        this.timeout = Duration.ofMillis(Math.max(2000, requestTimeoutMs));
        this.maxBodyBytes = Math.max(64_000, maxBodyBytes);
        this.userAgent = userAgent;
        this.allowedHosts = Arrays.stream(allowedHostsCsv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(s -> s.toLowerCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
    }

    public String postJson(String url, String jsonBody) {
        URI uri = URI.create(url);
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        if (!allowedHosts.contains(host)) {
            throw new IllegalStateException("Hote Jooble non autorise : " + host);
        }
        if (!"https".equalsIgnoreCase(uri.getScheme()) && !"http".equalsIgnoreCase(uri.getScheme())) {
            throw new IllegalStateException("Schema Jooble refuse");
        }
        try {
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .timeout(timeout)
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .header("User-Agent", userAgent)
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody == null ? "{}" : jsonBody))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            int code = response.statusCode();
            String body = response.body() == null ? "" : response.body();
            if (body.length() > maxBodyBytes) {
                throw new IllegalStateException("Reponse Jooble trop volumineuse");
            }
            if (code == 403) {
                throw new IllegalStateException("Jooble refuse l'acces (HTTP 403)");
            }
            if (code == 429) {
                throw new IllegalStateException("Quota Jooble depasse (HTTP 429)");
            }
            if (code == 401) {
                throw new IllegalStateException("Cle Jooble invalide (HTTP 401)");
            }
            if (code < 200 || code >= 300) {
                throw new IllegalStateException("API Jooble indisponible (HTTP " + code + ")");
            }
            return body;
        } catch (IllegalStateException e) {
            throw e;
        } catch (java.net.http.HttpTimeoutException e) {
            throw new IllegalStateException("Timeout Jooble", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interruption Jooble", e);
        } catch (Exception e) {
            throw new IllegalStateException("Echec reseau Jooble : " + e.getMessage(), e);
        }
    }
}