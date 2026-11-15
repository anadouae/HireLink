package ma.ensat.hirelink.service;

import ma.ensat.hirelink.model.ExternalJobOffer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Politique unique de listabilité publique des offres externes.
 * Partagée par le catalogue ({@link CatalogListingIndexer}) et {@code GET /external-offers}
 * afin que {@code hirelink.catalog.exclude-sources} et les règles statut/URL restent alignées.
 */
@Component
public class ExternalOfferListability {

    private final Set<String> excludeSources;

    public ExternalOfferListability(
            @Value("${hirelink.catalog.exclude-sources:MAROC_EMPLOI}") String excludeSourcesCsv) {
        Set<String> parsed = new HashSet<>();
        if (excludeSourcesCsv != null) {
            for (String s : excludeSourcesCsv.split(",")) {
                if (!s.isBlank()) parsed.add(s.trim().toUpperCase(Locale.ROOT));
            }
        }
        this.excludeSources = Collections.unmodifiableSet(parsed);
    }

    /** Sources exclues de toute vitrine publique (catalogue et legacy {@code /external-offers}). */
    public Set<String> excludeSources() {
        return excludeSources;
    }

    public boolean isSourceExcluded(String source) {
        if (source == null || source.isBlank()) return false;
        return excludeSources.contains(source.trim().toUpperCase(Locale.ROOT));
    }

    /**
     * Offre affichable publiquement : source non exclue, disponible, non quarantaine,
     * non masquée admin, URL DEEP/UNKNOWN (ou qualité absente pour compatibilité legacy).
     */
    public boolean isListable(ExternalJobOffer offer) {
        if (offer == null) return false;
        if (isSourceExcluded(offer.getSource())) return false;
        if (!offer.isAvailable() || offer.isQuarantined() || offer.isHiddenByAdmin()) return false;
        String q = offer.getUrlQuality();
        return q == null || "DEEP".equals(q) || "UNKNOWN".equals(q);
    }
}
