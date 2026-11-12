package ma.ensat.hirelink.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/** Appel unique au flux JSON public. Aucune offre n'est inventée si le flux ne répond pas. */
@Component
public class RemoteOkClient {

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).followRedirects(HttpClient.Redirect.NORMAL).build();
    private final String url;

    public RemoteOkClient(@Value("${hirelink.external-jobs.remoteok.url:https://remoteok.com/api}") String url) {
        this.url = url;
    }

    public String fetch() {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(12))
                    .header("Accept", "application/json")
                    .header("User-Agent", "HireLink/1.0 (agregateur; source citee: Remote OK)")
                    .GET()
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new IllegalStateException("Flux Remote OK indisponible (" + response.statusCode() + ")");
            }
            return response.body();
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Flux Remote OK injoignable");
        }
    }
}
