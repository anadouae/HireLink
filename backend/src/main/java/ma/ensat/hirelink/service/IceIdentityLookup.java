package ma.ensat.hirelink.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;

/**
 * Verification 2 — existence / identite d'entreprise pour un ICE.
 * <p>
 * Etat des sources officielles (nov. 2026) :
 * <ul>
 *   <li>{@code ice.gov.ma} — recuperation / declaration ICE, pas d'API publique de recherche</li>
 *   <li>{@code directinfo.ma} (OMPIC) — services payants / abonnement, pas d'API REST gratuite documentee</li>
 *   <li>{@code ompic.ma} — documentation ICE, pas d'API ouverte</li>
 *   <li>SIMPL Recherche entreprise (DGI) — interface web publique, pas d'API documentee pour usage commercial</li>
 * </ul>
 * Donc : registre interne HireLink (seed) + file manuelle. Un fournisseur HTTP optionnel peut etre branche
 * via {@code hirelink.ice.registry-url} + {@code hirelink.ice.registry-api-key} sans exposer la cle au front.
 */
@Service
public class IceIdentityLookup {

    public record Hit(
            String legalName,
            String address,
            String city,
            String sector,
            String website,
            String source,
            Instant checkedAt
    ) {}

    private final String registryUrl;
    private final String registryApiKey;

    public IceIdentityLookup(
            @Value("${hirelink.ice.registry-url:}") String registryUrl,
            @Value("${hirelink.ice.registry-api-key:}") String registryApiKey
    ) {
        this.registryUrl = registryUrl == null ? "" : registryUrl.trim();
        this.registryApiKey = registryApiKey == null ? "" : registryApiKey.trim();
    }

    public boolean externalProviderConfigured() {
        return !registryUrl.isBlank() && !registryApiKey.isBlank();
    }

    public Optional<Hit> lookup(String normalizedIce) {
        Instant now = Instant.now();
        if (normalizedIce == null || normalizedIce.length() != 15) {
            return Optional.empty();
        }
        // Fournisseur externe optionnel (contrat commercial) — non active sans config.
        // Intentionnel : pas d'appel HTTP tant que URL + cle ne sont pas fournies.
        if (externalProviderConfigured()) {
            // Placeholder reserve : brancher le client HTTP officiel ici uniquement
            // lorsque OMPIC/DGI aura publie une API autorisee pour usage commercial.
        }
        return CompanyDirectory.byIce(normalizedIce).map(e -> new Hit(
                e.name(),
                null,
                e.city(),
                e.sector(),
                e.website(),
                "Referentiel HireLink (seed interne - pas un extrait OMPIC en direct)",
                now
        ));
    }

    public static String manualPortalHint() {
        return "Verifiez l'identite sur le portail DGI (SIMPL Recherche entreprise) ou DirectInfo OMPIC, "
                + "puis demandez une validation admin si besoin.";
    }
}
