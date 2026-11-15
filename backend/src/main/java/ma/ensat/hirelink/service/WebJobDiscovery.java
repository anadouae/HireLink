package ma.ensat.hirelink.service;

import java.util.List;

/**
 * Découverte d'annonces sur le Web via un ou plusieurs fournisseurs autorisés.
 * Désactivé par défaut sur la démo. Résultats jamais persistés.
 */
public interface WebJobDiscovery {

    record DiscoveredJob(
            String title,
            String companyName,
            String location,
            String city,
            String countryCode,
            String contractType,
            String workMode,
            String skills,
            String description,
            String originUrl,
            String applyUrl,
            String sourceCode,
            String sourceLabel,
            String sourceHome) {}

    record SourceReport(
            String code,
            String label,
            String status,
            String note,
            int hits) {}

    record DiscoveryResult(
            boolean available,
            String status,
            String message,
            List<DiscoveredJob> jobs,
            List<SourceReport> sources,
            String coverageSummary) {

        public static DiscoveryResult unavailable(String message) {
            return new DiscoveryResult(false, "UNAVAILABLE", message, List.of(), List.of(), message);
        }

        public static DiscoveryResult ok(List<DiscoveredJob> jobs) {
            return ok(jobs, List.of(), "");
        }

        public static DiscoveryResult ok(List<DiscoveredJob> jobs, List<SourceReport> sources, String coverageSummary) {
            return new DiscoveryResult(true, "OK", coverageSummary == null ? "" : coverageSummary,
                    jobs == null ? List.of() : jobs,
                    sources == null ? List.of() : sources,
                    coverageSummary == null ? "" : coverageSummary);
        }

        public static DiscoveryResult error(String message) {
            return new DiscoveryResult(false, "ERROR", message, List.of(), List.of(), message);
        }
    }

    DiscoveryResult search(String query, String countryCode, String location, int limit);

    default DiscoveryResult search(String query, String countryCode, int limit) {
        return search(query, countryCode, null, limit);
    }
}
