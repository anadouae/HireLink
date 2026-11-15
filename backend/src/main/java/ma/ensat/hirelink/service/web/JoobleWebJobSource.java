package ma.ensat.hirelink.service.web;

import ma.ensat.hirelink.service.ParsedExternalJob;
import ma.ensat.hirelink.service.WebJobDiscovery.DiscoveredJob;
import ma.ensat.hirelink.service.external.JoobleJobConnector;
import ma.ensat.hirelink.service.external.JoobleJobParser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Jooble — optionnel, clé régionale obligatoire.
 * Une clé {@code ma.jooble.org} ne couvre que le Maroc ; d'autres domaines = autres pays.
 * Sans clé → source ignorée (les autres sources restent actives).
 */
@Component
public class JoobleWebJobSource implements WebJobSource {

    /** Domaines Jooble connus (clé séparée par pays chez Jooble). */
    private static final Map<String, String> REGIONAL_HOME = Map.of(
            "MA", "https://ma.jooble.org",
            "FR", "https://fr.jooble.org",
            "DE", "https://de.jooble.org",
            "GB", "https://uk.jooble.org",
            "US", "https://jooble.org",
            "ES", "https://es.jooble.org",
            "BE", "https://be.jooble.org",
            "CA", "https://ca.jooble.org"
    );

    private final JoobleJobConnector jooble;
    private final JobGeoVerifier geo;
    private final boolean enabled;
    private final String keyCountry;
    private final String defaultLocation;

    public JoobleWebJobSource(
            JoobleJobConnector jooble,
            JobGeoVerifier geo,
            @Value("${hirelink.web-search.jooble.enabled:true}") boolean enabled,
            @Value("${hirelink.web-search.jooble.key-country:MA}") String keyCountry,
            @Value("${hirelink.external-jobs.jooble.location:Maroc}") String defaultLocation) {
        this.jooble = jooble;
        this.geo = geo;
        this.enabled = enabled;
        this.keyCountry = keyCountry == null || keyCountry.isBlank() ? "MA" : keyCountry.trim().toUpperCase(Locale.ROOT);
        this.defaultLocation = defaultLocation;
    }

    @Override public String code() { return JoobleJobParser.SOURCE; }
    @Override public String label() { return "Jooble (" + keyCountry + ")"; }
    @Override public String homeUrl() {
        return REGIONAL_HOME.getOrDefault(keyCountry, "https://jooble.org");
    }
    @Override public String coverageNote() {
        return "Jooble : une clé = un pays (" + keyCountry + "). Gratuit ~100 req/jour. Autres pays = autres clés (non configurées ici).";
    }
    @Override public Set<String> countryHints() { return Set.of(keyCountry); }
    @Override public boolean isReady() { return enabled && jooble.hasKey(); }
    @Override public String notReadyReason() {
        if (!enabled) return "Jooble Web désactivé.";
        if (!jooble.hasKey()) {
            return "Clé Jooble absente pour " + keyCountry + " (https://"
                    + (keyCountry.equals("US") ? "jooble.org" : keyCountry.toLowerCase(Locale.ROOT) + ".jooble.org")
                    + "/api/about).";
        }
        return "";
    }

    @Override
    public List<DiscoveredJob> fetch(String query, String countryCode, String location, int limit) {
        if (!isReady()) return List.of();
        if (!geo.isWorldwideFilter(countryCode) && !keyCountry.equalsIgnoreCase(countryCode.trim())) {
            return List.of(); // clé régionale non applicable
        }
        if (query == null || query.isBlank()) return List.of();
        String loc = location != null && !location.isBlank() ? location
                : (geo.isWorldwideFilter(countryCode) ? defaultLocation : countryCode);
        try {
            List<ParsedExternalJob> raw = jooble.searchLive(query, loc, 1, Math.min(50, Math.max(limit, 10)));
            List<DiscoveredJob> out = new ArrayList<>();
            for (ParsedExternalJob p : raw) {
                if (p == null || p.title() == null) continue;
                String deep = WebJobHttp.deepJobUrl(p.applyUrl());
                if (deep == null) deep = WebJobHttp.deepJobUrl(p.originUrl());
                if (deep == null) continue;
                String inferred = p.countryCode() != null ? p.countryCode() : geo.inferCountryCode(p.location());
                if (inferred == null) inferred = keyCountry;
                DiscoveredJob job = new DiscoveredJob(
                        p.title(),
                        p.company() == null ? "Entreprise (via Jooble)" : p.company(),
                        p.location(),
                        p.location(),
                        inferred,
                        p.contractLabel(),
                        null,
                        p.skills(),
                        p.description(),
                        deep,
                        deep,
                        code(),
                        label(),
                        homeUrl());
                if (!geo.matches(job, countryCode, location)) continue;
                out.add(job);
                if (out.size() >= limit) break;
            }
            return out;
        } catch (Exception e) {
            throw new IllegalStateException("Jooble indisponible : " + e.getMessage(), e);
        }
    }
}
