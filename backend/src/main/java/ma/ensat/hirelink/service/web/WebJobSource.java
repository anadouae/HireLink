package ma.ensat.hirelink.service.web;

import ma.ensat.hirelink.service.WebJobDiscovery.DiscoveredJob;

import java.util.List;
import java.util.Set;

/** Source Web autorisée (API publique ou clé optionnelle). Ne persiste rien. */
public interface WebJobSource {

    String code();

    String label();

    String homeUrl();

    /** Description honnête de la couverture (affichée à l'utilisateur). */
    String coverageNote();

    /**
     * Pays ISO pour lesquels cette source est pertinente.
     * Vide = mondiale / remote mixte — chaque offre doit encore passer le filtre géo.
     */
    Set<String> countryHints();

    boolean isReady();

    String notReadyReason();

    List<DiscoveredJob> fetch(String query, String countryCode, String location, int limit);
}
