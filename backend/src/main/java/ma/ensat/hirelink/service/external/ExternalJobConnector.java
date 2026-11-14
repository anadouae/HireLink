package ma.ensat.hirelink.service.external;

import ma.ensat.hirelink.service.ParsedExternalJob;

import java.util.List;

/**
 * SPI Phase 2 : connecteur ATS (Greenhouse, Lever, Ashby, ...).
 * Un connecteur ne cree jamais de candidature interne HireLink.
 */
public interface ExternalJobConnector {

    /** Code source stable (ex. GREENHOUSE). */
    String sourceCode();

    String label();

    String homeUrl();

    /** Provider active globalement via configuration. */
    boolean providerEnabled();

    /**
     * Recupere et normalise les annonces des boards allowlistes actives pour ce provider.
     * En cas d'echec reseau/format : lever IllegalStateException / IllegalArgumentException
     * sans effacer les offres deja importees (l'appelant n'appelle pas importSnapshot).
     */
    List<ParsedExternalJob> fetchNormalized();

    /** Sync d'un seul board (boardKey allowlist). */
    List<ParsedExternalJob> fetchNormalized(String boardKey);
}