package ma.ensat.hirelink.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Fournisseur Web par défaut : aucun appel externe.
 * Active un vrai connecteur seulement avec {@code hirelink.web-search.enabled=true}
 * et un provider configuré (hors démo).
 */
@Service
@ConditionalOnProperty(name = "hirelink.web-search.enabled", havingValue = "false", matchIfMissing = true)
public class DisabledWebJobDiscovery implements WebJobDiscovery {

    public static final String MESSAGE =
            "La recherche Web d’annonces externes est temporairement indisponible. "
                    + "Les offres HireLink et les annonces déjà indexées restent affichées.";

    @Override
    public DiscoveryResult search(String query, String countryCode, String location, int limit) {
        return DiscoveryResult.unavailable(MESSAGE);
    }
}
