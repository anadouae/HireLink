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
            String sourceHome,
            /** Date de publication annoncée par la source (null = inconnue). */
            java.time.Instant publishedAt,
            /** Rémunération annoncée par la source, dans sa devise (null = non précisée). */
            Integer salaryMin,
            Integer salaryMax,
            String salaryCurrency,
            /** Période annoncée par la source (YEAR, MONTH, WEEK, DAY, HOUR) ; null = non précisée. */
            String salaryPeriod) {

        /** Annonce sans date ni rémunération connues. */
        public DiscoveredJob(String title, String companyName, String location, String city, String countryCode,
                             String contractType, String workMode, String skills, String description,
                             String originUrl, String applyUrl, String sourceCode, String sourceLabel,
                             String sourceHome) {
            this(title, companyName, location, city, countryCode, contractType, workMode, skills, description,
                    originUrl, applyUrl, sourceCode, sourceLabel, sourceHome, null, null, null, null, null);
        }

        /** Copie avec un autre libellé de contrat (libellé français normalisé, ou null = non précisé). */
        public DiscoveredJob withContractType(String contract) {
            return new DiscoveredJob(title, companyName, location, city, countryCode, contract, workMode, skills,
                    description, originUrl, applyUrl, sourceCode, sourceLabel, sourceHome, publishedAt, salaryMin,
                    salaryMax, salaryCurrency, salaryPeriod);
        }

        /** Copie avec date de publication et rémunération (valeurs nulles = inconnues). */
        public DiscoveredJob withDetails(java.time.Instant published, Integer salMin, Integer salMax, String currency) {
            return withDetails(published, salMin, salMax, currency, null);
        }

        /** Copie avec date, rémunération et période (période nulle = non précisée par la source). */
        public DiscoveredJob withDetails(java.time.Instant published, Integer salMin, Integer salMax,
                                        String currency, String period) {
            Integer lo = salMin != null && salMin > 0 ? salMin : null;
            Integer hi = salMax != null && salMax > 0 ? salMax : null;
            String cur = (lo == null && hi == null) || currency == null || currency.isBlank()
                    ? null : currency.trim().toUpperCase(java.util.Locale.ROOT);
            String per = (lo == null && hi == null) ? null : ma.ensat.hirelink.service.web.WebJobFields.period(period);
            return new DiscoveredJob(title, companyName, location, city, countryCode, contractType, workMode, skills,
                    description, originUrl, applyUrl, sourceCode, sourceLabel, sourceHome, published, lo, hi, cur, per);
        }
    }

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
