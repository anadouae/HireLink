package ma.ensat.hirelink.service.external;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Client HTTP borne pour les APIs ATS publiques.
 * SSRF : seuls les hotes allowlistes ; redirections non suivies ;
 * timeouts et taille de reponse limites.
 */
@Component
public class AtsHttpClient {

    private final HttpClient http;
    private final Duration requestTimeout;
    private final int maxBodyBytes;
    private final String userAgent;
    private final long minIntervalMs;
    private final Set<String> allowedHosts;
    private final boolean rejectPrivateIp;
    private long lastCallAt;

    public AtsHttpClient(
            @Value("${hirelink.external-jobs.ats.connect-timeout-ms:8000}") int connectTimeoutMs,
            @Value("${hirelink.external-jobs.ats.request-timeout-ms:15000}") int requestTimeoutMs,
            @Value("${hirelink.external-jobs.ats.max-body-bytes:2097152}") int maxBodyBytes,
            @Value("${hirelink.external-jobs.ats.min-interval-ms:400}") long minIntervalMs,
            @Value("${hirelink.external-jobs.ats.user-agent:HireLink/1.0 (job-discovery; +https://hirelink.ma; attribution-required)}") String userAgent,
            @Value("${hirelink.external-jobs.ats.allowed-hosts:boards-api.greenhouse.io,api.lever.co,api.eu.lever.co,api.ashbyhq.com}") String allowedHostsCsv,
            @Value("${hirelink.external-jobs.ats.reject-private-ip:true}") boolean rejectPrivateIp) {
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(Math.max(1000, connectTimeoutMs)))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        this.requestTimeout = Duration.ofMillis(Math.max(2000, requestTimeoutMs));
        this.maxBodyBytes = Math.max(64_000, maxBodyBytes);
        this.minIntervalMs = Math.max(0, minIntervalMs);
        this.userAgent = userAgent;
        this.allowedHosts = Arrays.stream(allowedHostsCsv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(s -> s.toLowerCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
        this.rejectPrivateIp = rejectPrivateIp;
    }

    public String getJson(String url) {
        URI uri = validateUrl(url);
        throttle();
        try {
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .timeout(requestTimeout)
                    .header("Accept", "application/json")
                    .header("User-Agent", userAgent)
                    .GET()
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            int code = response.statusCode();
            if (code == 301 || code == 302 || code == 303 || code == 307 || code == 308) {
                throw new IllegalStateException("Redirection ATS refusee (HTTP " + code + ") — suivi non autorise");
            }
            if (code == 429) {
                throw new IllegalStateException("Rate limit ATS (HTTP 429) pour " + uri.getHost());
            }
            if (code == 404) {
                throw new IllegalStateException("Board ATS introuvable (HTTP 404) : " + uri.getPath());
            }
            if (code < 200 || code >= 300) {
                throw new IllegalStateException("API ATS indisponible (HTTP " + code + ") sur " + uri.getHost());
            }
            String body = response.body() == null ? "" : response.body();
            if (body.length() > maxBodyBytes) {
                throw new IllegalStateException("Reponse ATS trop volumineuse (>" + maxBodyBytes + " octets)");
            }
            return body;
        } catch (IllegalStateException e) {
            throw e;
        } catch (java.net.http.HttpTimeoutException e) {
            throw new IllegalStateException("Timeout API ATS sur " + uri.getHost());
        } catch (Exception e) {
            throw new IllegalStateException("API ATS injoignable : " + uri.getHost());
        }
    }

    public URI validateUrl(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("URL ATS vide");
        }
        URI uri;
        try {
            uri = URI.create(raw.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("URL ATS illisible");
        }
        if (!"https".equalsIgnoreCase(uri.getScheme()) && !"http".equalsIgnoreCase(uri.getScheme())) {
            throw new IllegalArgumentException("Schema URL ATS refuse");
        }
        // Production: https only. Tests may allow http://127.0.0.1 via property.
        if ("http".equalsIgnoreCase(uri.getScheme()) && !"127.0.0.1".equals(uri.getHost()) && !"localhost".equalsIgnoreCase(uri.getHost())) {
            throw new IllegalArgumentException("URL ATS non https refusee");
        }
        if ("https".equalsIgnoreCase(uri.getScheme()) == false && !"127.0.0.1".equals(uri.getHost()) && !"localhost".equalsIgnoreCase(uri.getHost())) {
            throw new IllegalArgumentException("URL ATS non https refusee");
        }
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        if (!allowedHosts.contains(host)) {
            throw new IllegalArgumentException("Hote ATS non allowliste : " + host);
        }
        if (uri.getUserInfo() != null) {
            throw new IllegalArgumentException("URL ATS avec userinfo refusee");
        }
        if (rejectPrivateIp) {
            rejectPrivateResolvedHost(host);
        }
        return uri;
    }

    private void rejectPrivateResolvedHost(String host) {
        try {
            InetAddress[] addrs = InetAddress.getAllByName(host);
            for (InetAddress addr : addrs) {
                if (addr.isAnyLocalAddress() || addr.isLoopbackAddress() || addr.isLinkLocalAddress()
                        || addr.isSiteLocalAddress() || addr.isMulticastAddress()) {
                    throw new IllegalArgumentException("Resolution SSRF refusee pour " + host);
                }
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            // DNS failure surfaces on connect.
        }
    }

    private synchronized void throttle() {
        long now = System.currentTimeMillis();
        long wait = lastCallAt + minIntervalMs - now;
        if (wait > 0) {
            try {
                Thread.sleep(wait);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Throttle ATS interrompu");
            }
        }
        lastCallAt = System.currentTimeMillis();
    }
}