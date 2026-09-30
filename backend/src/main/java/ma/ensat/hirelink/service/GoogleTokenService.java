package ma.ensat.hirelink.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class GoogleTokenService {

    public record GoogleIdentity(String sub, String email, String name, boolean emailVerified) {}

    private final RestClient restClient = RestClient.create();
    private final ObjectMapper mapper = new ObjectMapper();

    @Value("${hirelink.google.client-id:}")
    private String clientId;

    @Value("${hirelink.google.dev-bypass:true}")
    private boolean devBypass;

    public boolean isConfigured() {
        return clientId != null && !clientId.isBlank();
    }

    public boolean isDevBypass() {
        return devBypass;
    }

    public GoogleIdentity verifyIdToken(String idToken) {
        if (idToken == null || idToken.isBlank()) {
            throw new IllegalArgumentException("idToken manquant");
        }
        String body = restClient.get()
                .uri("https://oauth2.googleapis.com/tokeninfo?id_token={t}", idToken)
                .retrieve()
                .body(String.class);
        try {
            JsonNode node = mapper.readTree(body);
            String aud = node.path("aud").asText();
            if (isConfigured() && !clientId.equals(aud)) {
                throw new IllegalArgumentException("Audience Google invalide");
            }
            String email = node.path("email").asText(null);
            if (email == null || email.isBlank()) {
                throw new IllegalArgumentException("Email Google manquant");
            }
            return new GoogleIdentity(
                    node.path("sub").asText(),
                    email.toLowerCase(),
                    node.path("name").asText(email),
                    node.path("email_verified").asBoolean(true)
            );
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Token Google invalide: " + e.getMessage());
        }
    }

    public GoogleIdentity devIdentity(String email, String name) {
        if (!devBypass) {
            throw new IllegalStateException("Google démo désactivé");
        }
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("devEmail requis en mode démo");
        }
        String e = email.trim().toLowerCase();
        return new GoogleIdentity("dev-" + e, e, name == null || name.isBlank() ? e.split("@")[0] : name, true);
    }
}
