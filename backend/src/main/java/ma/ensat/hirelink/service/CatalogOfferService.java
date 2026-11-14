package ma.ensat.hirelink.service;

import ma.ensat.hirelink.model.CatalogForYouRun;
import ma.ensat.hirelink.model.CatalogListing;
import ma.ensat.hirelink.model.JobOffer;
import ma.ensat.hirelink.model.User;
import ma.ensat.hirelink.repository.CatalogForYouRunRepository;
import ma.ensat.hirelink.repository.CatalogListingRepository;
import ma.ensat.hirelink.repository.JobOfferRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Catalogue unifie avec pagination SQL et mode Pour toi.
 * Internes : SkillMatchingService.evaluate (multicritere).
 * Externes : explain competences source uniquement, label indicatif.
 * Au-dela de for-you-max-scan : truncation douce (offres les plus recentes), jamais HTTP 400.
 */
@Service
public class CatalogOfferService {

    public record CatalogPage(
            int page,
            int size,
            long totalElements,
            int totalPages,
            List<Map<String, Object>> items,
            boolean forYouTruncated,
            int forYouScanned,
            int forYouScanCap) {}

    private final CatalogListingRepository listings;
    private final CatalogForYouRunRepository forYouRuns;
    private final CatalogListingIndexer indexer;
    private final SkillMatchingService matching;
    private final ExternalSourceCatalog sources;
    private final JobOfferRepository jobOffers;
    private final PlaceCatalogService places;
    private final double forYouThreshold;
    private final int forYouMaxScan;
    private final int maxPageSize;

    public CatalogOfferService(
            CatalogListingRepository listings,
            CatalogForYouRunRepository forYouRuns,
            CatalogListingIndexer indexer,
            SkillMatchingService matching,
            ExternalSourceCatalog sources,
            JobOfferRepository jobOffers,
            PlaceCatalogService places,
            @Value("${hirelink.catalog.for-you-threshold:50}") double forYouThreshold,
            @Value("${hirelink.catalog.for-you-max-scan:5000}") int forYouMaxScan,
            @Value("${hirelink.catalog.max-page-size:50}") int maxPageSize) {
        this.listings = listings;
        this.forYouRuns = forYouRuns;
        this.indexer = indexer;
        this.matching = matching;
        this.sources = sources;
        this.jobOffers = jobOffers;
        this.places = places;
        this.forYouThreshold = forYouThreshold;
        this.forYouMaxScan = Math.max(100, forYouMaxScan);
        this.maxPageSize = Math.max(1, maxPageSize);
    }

    @Transactional
    public int rebuildIndex() {
        return indexer.rebuild();
    }

    @Transactional
    public CatalogPage search(
            User user,
            String q,
            String country,
            String city,
            String contract,
            String workMode,
            String source,
            String origin,
            Integer sinceDays,
            boolean forYou,
            int page,
            int size) {
        int safeSize = Math.min(maxPageSize, Math.max(1, size));
        int safePage = Math.max(0, page);
        String qn = blank(q);
        String countryN = blankUpper(country);
        String cityRaw = places.canonicalCity(blank(city));
        String cityN = cityRaw == null ? null : TextMatcher.fold(cityRaw);
        String contractN = blank(contract);
        String workModeN = blankUpper(workMode);
        String sourceN = blankUpper(source);
        String originN = mapOrigin(origin);
        Instant since = null;
        if (sinceDays != null && sinceDays > 0) {
            since = Instant.now().minus(Duration.ofDays(sinceDays));
        }

        if (forYou) {
            return searchForYou(user, qn, countryN, cityN, contractN, workModeN, sourceN, originN, since, safePage, safeSize);
        }

        long total = listings.countFiltered(qn, countryN, cityN, contractN, workModeN, sourceN, originN, since);
        int offset = safePage * safeSize;
        List<CatalogListing> rows = total == 0 || offset >= total
                ? List.of()
                : listings.findFilteredPage(qn, countryN, cityN, contractN, workModeN, sourceN, originN, since, safeSize, offset);
        List<Map<String, Object>> items = new ArrayList<>(rows.size());
        for (CatalogListing row : rows) {
            items.add(toDto(row, user, null));
        }
        int pages = total == 0 ? 0 : (int) Math.ceil(total / (double) safeSize);
        return new CatalogPage(safePage, safeSize, total, pages, items, false, 0, forYouMaxScan);
    }

    private CatalogPage searchForYou(
            User user,
            String q, String country, String city, String contract, String workMode,
            String source, String origin, Instant since,
            int page, int size) {
        if (user == null || !matching.hasConfirmedSkills(user)) {
            return new CatalogPage(page, size, 0, 0, List.of(), false, 0, forYouMaxScan);
        }
        long filteredTotal = listings.countFiltered(q, country, city, contract, workMode, source, origin, since);
        boolean truncated = filteredTotal > forYouMaxScan;
        List<Object[]> candidates = listings.findForYouCandidates(
                q, country, city, contract, workMode, source, origin, since, forYouMaxScan);
        forYouRuns.deleteOlderThan(Instant.now().minus(Duration.ofHours(1)));
        String runId = UUID.randomUUID().toString();
        List<CatalogForYouRun> batch = new ArrayList<>();
        Map<Long, JobOffer> internalCache = new HashMap<>();
        for (Object[] row : candidates) {
            Long listingId = ((Number) row[0]).longValue();
            String originCode = String.valueOf(row[1]);
            Long refId = ((Number) row[2]).longValue();
            String skills = row[3] == null ? "" : String.valueOf(row[3]);
            Double score = scoreCandidate(user, originCode, refId, skills, internalCache);
            if (score != null && score >= forYouThreshold) {
                batch.add(new CatalogForYouRun(runId, listingId, score));
            }
        }
        if (!batch.isEmpty()) forYouRuns.saveAll(batch);
        forYouRuns.flush();
        try {
            long total = forYouRuns.countEligible(runId, forYouThreshold);
            int offset = page * size;
            List<Long> pageIds = total == 0 || offset >= total
                    ? List.of()
                    : forYouRuns.findPageIds(runId, forYouThreshold, size, offset);
            Map<Long, Double> scores = new HashMap<>();
            for (Object[] s : forYouRuns.scoresForRun(runId, forYouThreshold)) {
                scores.put(((Number) s[0]).longValue(), ((Number) s[1]).doubleValue());
            }
            Map<Long, CatalogListing> byId = new HashMap<>();
            if (!pageIds.isEmpty()) {
                for (CatalogListing row : listings.findByIdIn(pageIds)) {
                    byId.put(row.getId(), row);
                }
            }
            List<Map<String, Object>> items = new ArrayList<>(pageIds.size());
            for (Long id : pageIds) {
                CatalogListing row = byId.get(id);
                if (row != null) items.add(toDto(row, user, scores.get(row.getId())));
            }
            int pages = total == 0 ? 0 : (int) Math.ceil(total / (double) size);
            return new CatalogPage(page, size, total, pages, items, truncated, candidates.size(), forYouMaxScan);
        } finally {
            forYouRuns.deleteByRunId(runId);
        }
    }

    private Double scoreCandidate(User user, String origin, Long refId, String skills, Map<Long, JobOffer> cache) {
        if (CatalogListing.ORIGIN_INTERNAL.equals(origin)) {
            JobOffer offer = cache.computeIfAbsent(refId, id -> jobOffers.findById(id).orElse(null));
            if (offer == null) return null;
            SkillMatchingService.Compatibility compat = matching.evaluateIfAvailable(user, offer);
            return compat == null ? null : compat.score();
        }
        if (skills == null || skills.isBlank()) return null;
        if (!matching.hasConfirmedSkills(user)) return null;
        return matching.computeMatchScore(user.getSkills(), skills);
    }

    private Map<String, Object> toDto(CatalogListing row, User user, Double forcedScore) {
        Map<String, Object> body = new LinkedHashMap<>();
        boolean external = CatalogListing.ORIGIN_EXTERNAL.equals(row.getOrigin());
        body.put("external", external);
        body.put("id", row.getPublicId());
        body.put("origin", row.getOrigin());
        body.put("source", row.getSourceCode());
        body.put("sourceLabel", external ? sources.labelOf(row.getSourceCode()) : "HireLink");
        body.put("sourceHome", external ? sources.homeOf(row.getSourceCode()) : "");
        body.put("boardKey", row.getBoardKey());
        body.put("title", row.getTitle());
        body.put("companyName", row.getCompanyName());
        body.put("location", row.getLocationLabel());
        body.put("city", row.getCity());
        body.put("countryCode", row.getCountryCode());
        body.put("contractType", row.getContractLabel());
        body.put("workMode", row.getWorkMode());
        body.put("requiredSkills", row.getSkillsText());
        body.put("description", row.getDescription());
        if (external) {
            body.put("originUrl", row.getOriginUrl());
            body.put("applyUrl", row.getApplyUrl());
            body.put("urlQuality", row.getUrlQuality());
            body.put("linkReliable", row.isLinkReliable());
            body.put("directAccessTrusted", row.isLinkReliable());
        } else {
            body.put("linkReliable", true);
            body.put("directAccessTrusted", true);
        }
        if (row.isPublishedTrusted() && row.getPublishedAt() != null) {
            body.put("publishedAt", row.getPublishedAt());
            body.put("publishedAtTrusted", true);
        } else {
            body.put("publishedAtTrusted", false);
        }
        body.put("createdAt", row.getSortAt());

        Double score = forcedScore;
        boolean matchAvailable = false;
        String matchLabel = "Correspondance non disponible";
        if (user != null && matching.hasConfirmedSkills(user)) {
            if (!external) {
                if (score == null) {
                    JobOffer offer = jobOffers.findById(row.getRefId()).orElse(null);
                    if (offer != null) {
                        SkillMatchingService.Compatibility compat = matching.evaluateIfAvailable(user, offer);
                        if (compat != null) {
                            score = compat.score();
                            body.put("matchBreakdown", compat);
                        }
                    }
                }
                if (score != null) {
                    matchAvailable = true;
                    matchLabel = "Compatibilite multicritere HireLink (competences, langues, experience, contrat, lieu).";
                    body.put("matchScore", score);
                    body.put("matchesProfile", score >= forYouThreshold);
                }
            } else if (row.getSkillsText() != null && !row.getSkillsText().isBlank()) {
                if (score == null) {
                    score = matching.computeMatchScore(user.getSkills(), row.getSkillsText());
                }
                matchAvailable = true;
                matchLabel = "Indicatif : competences citees par la source uniquement (pas le matching multicritere HireLink).";
                body.put("matchScore", score);
                body.put("matchesProfile", score >= forYouThreshold);
            }
        }
        if (!matchAvailable) {
            body.put("matchesProfile", false);
        }
        body.put("matchAvailable", matchAvailable);
        body.put("matchLabel", matchLabel);
        return body;
    }

    private static String mapOrigin(String origin) {
        if (origin == null || origin.isBlank() || "all".equalsIgnoreCase(origin)) return null;
        if ("internal".equalsIgnoreCase(origin) || "hirelink".equalsIgnoreCase(origin)) {
            return CatalogListing.ORIGIN_INTERNAL;
        }
        if ("external".equalsIgnoreCase(origin)) return CatalogListing.ORIGIN_EXTERNAL;
        return null;
    }

    private static String blank(String v) {
        if (v == null) return null;
        String t = v.trim();
        return t.isEmpty() ? null : t;
    }

    private static String blankUpper(String v) {
        String t = blank(v);
        return t == null ? null : t.toUpperCase(Locale.ROOT);
    }
}