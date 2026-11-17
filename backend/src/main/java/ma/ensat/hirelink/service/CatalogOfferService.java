package ma.ensat.hirelink.service;

import ma.ensat.hirelink.model.CatalogForYouRun;
import ma.ensat.hirelink.model.CatalogListing;
import ma.ensat.hirelink.model.JobOffer;
import ma.ensat.hirelink.model.Organization;
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
 * Catalogue unifié : pagination SQL, mode Pour toi, et recherche intelligente
 * (fuzzy + synonymes + fusion Web optionnelle). Les annonces Web ne sont jamais persistées.
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
            int forYouScanCap,
            boolean rankedByRelevance,
            boolean webSearchAvailable,
            String webSearchStatus,
            String webSearchMessage,
            String webSearchCoverage,
            List<WebJobDiscovery.SourceReport> webSearchSources) {

        /** Compat tests / appels internes sans métadonnées Web. */
        public CatalogPage(
                int page, int size, long totalElements, int totalPages,
                List<Map<String, Object>> items,
                boolean forYouTruncated, int forYouScanned, int forYouScanCap) {
            this(page, size, totalElements, totalPages, items, forYouTruncated, forYouScanned, forYouScanCap,
                    false, false, "UNAVAILABLE", DisabledWebJobDiscovery.MESSAGE,
                    DisabledWebJobDiscovery.MESSAGE, List.of());
        }
    }

    private final CatalogListingRepository listings;
    private final CatalogForYouRunRepository forYouRuns;
    private final CatalogListingIndexer indexer;
    private final SkillMatchingService matching;
    private final ExternalSourceCatalog sources;
    private final JobOfferRepository jobOffers;
    private final PlaceCatalogService places;
    private final CatalogSmartRanker ranker;
    private final JobSearchLexicon lexicon;
    private final WebJobDiscovery webDiscovery;
    private final double forYouThreshold;
    private final int forYouMaxScan;
    private final int maxPageSize;
    private final int smartScanCap;
    private final int webSearchLimit;

    public CatalogOfferService(
            CatalogListingRepository listings,
            CatalogForYouRunRepository forYouRuns,
            CatalogListingIndexer indexer,
            SkillMatchingService matching,
            ExternalSourceCatalog sources,
            JobOfferRepository jobOffers,
            PlaceCatalogService places,
            CatalogSmartRanker ranker,
            JobSearchLexicon lexicon,
            WebJobDiscovery webDiscovery,
            @Value("${hirelink.catalog.for-you-threshold:50}") double forYouThreshold,
            @Value("${hirelink.catalog.for-you-max-scan:5000}") int forYouMaxScan,
            @Value("${hirelink.catalog.max-page-size:50}") int maxPageSize,
            @Value("${hirelink.catalog.smart-scan-cap:2500}") int smartScanCap,
            @Value("${hirelink.web-search.max-results:20}") int webSearchLimit) {
        this.listings = listings;
        this.forYouRuns = forYouRuns;
        this.indexer = indexer;
        this.matching = matching;
        this.sources = sources;
        this.jobOffers = jobOffers;
        this.places = places;
        this.ranker = ranker;
        this.lexicon = lexicon;
        this.webDiscovery = webDiscovery;
        this.forYouThreshold = forYouThreshold;
        this.forYouMaxScan = Math.max(100, forYouMaxScan);
        this.maxPageSize = Math.max(1, maxPageSize);
        this.smartScanCap = Math.max(100, smartScanCap);
        this.webSearchLimit = Math.max(0, webSearchLimit);
    }

    @Transactional
    public int rebuildIndex() {
        return indexer.rebuild();
    }

    /** Compat tests / anciens appels sans filtres salaire·expérience·secteur. */
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
            Double minMatch,
            int page,
            int size) {
        return search(user, q, country, city, contract, workMode, source, origin, sinceDays,
                null, null, null, null, forYou, minMatch, page, size);
    }

    /** Compat tests / anciens appels sans filtre entreprise. */
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
            Integer minSalary,
            String experience,
            String sector,
            boolean forYou,
            Double minMatch,
            int page,
            int size) {
        return search(user, q, country, city, contract, workMode, source, origin, sinceDays,
                minSalary, experience, sector, null, forYou, minMatch, page, size);
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
            Integer minSalary,
            String experience,
            String sector,
            String company,
            boolean forYou,
            Double minMatch,
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
        Integer minSalaryN = (minSalary != null && minSalary > 0) ? minSalary : null;
        String experienceN = blankUpper(experience);
        String sectorN = blank(sector);
        String companyN = blank(company);
        Instant since = null;
        if (sinceDays != null && sinceDays > 0) {
            since = Instant.now().minus(Duration.ofDays(sinceDays));
        }

        double matchFloor = minMatch != null && minMatch > 0 ? minMatch : 0;
        // forYou = classement par pertinence profil, SANS seuil automatique.
        // minMatch > 0 = filtre explicite choisi par le candidat uniquement.
        if (forYou || matchFloor > 0) {
            double threshold = matchFloor > 0 ? matchFloor : 0.0;
            return searchForYou(user, qn, countryN, cityN, cityRaw, contractN, workModeN, sourceN, originN, since,
                    minSalaryN, experienceN, sectorN, companyN, safePage, safeSize, threshold);
        }

        // Recherche libre : fuzzy + synonymes + fusion Web (sans favoriser l'interne).
        if (qn != null) {
            return searchSmart(user, qn, countryN, cityN, cityRaw, contractN, workModeN, sourceN, originN, since,
                    minSalaryN, experienceN, sectorN, companyN, safePage, safeSize);
        }

        long total = listings.countFiltered(null, countryN, cityN, contractN, workModeN, sourceN, originN, since,
                minSalaryN, experienceN, sectorN, companyN);
        int offset = safePage * safeSize;
        List<CatalogListing> rows = total == 0 || offset >= total
                ? List.of()
                : listings.findFilteredPage(null, countryN, cityN, contractN, workModeN, sourceN, originN, since,
                        minSalaryN, experienceN, sectorN, companyN, safeSize, offset);
        List<Map<String, Object>> items = new ArrayList<>(rows.size());
        for (CatalogListing row : rows) {
            items.add(toDto(row, user, null, null));
        }
        int pages = total == 0 ? 0 : (int) Math.ceil(total / (double) safeSize);
        // q vide : fusion Web éphémère (page 0) — pas pour le filtre « internes seulement ».
        boolean allowWeb = originN == null || CatalogListing.ORIGIN_EXTERNAL.equals(originN);
        WebJobDiscovery.DiscoveryResult web = allowWeb
                ? webDiscovery.search("", countryN, cityRaw, webSearchLimit)
                : WebJobDiscovery.DiscoveryResult.unavailable("Filtre limité aux offres HireLink.");
        // Web éphémère en page 0 : en plus du catalogue, sans évincer d'IDs
        // (sinon la pagination SQL perd des offres). totalElements = catalogue seul.
        // La page 0 peut donc dépasser légèrement safeSize (bonus Web).
        if (allowWeb && web.available() && safePage == 0 && web.jobs() != null && !web.jobs().isEmpty()) {
            int cap = Math.min(5, Math.max(1, safeSize / 2));
            List<Map<String, Object>> webItems = new ArrayList<>();
            for (WebJobDiscovery.DiscoveredJob job : web.jobs()) {
                if (job == null || job.originUrl() == null) continue;
                webItems.add(toDtoEphemeral(ephemeralListing(job), Math.max(CatalogSmartRanker.MIN_SCORE, 0.35)));
                if (webItems.size() >= cap) break;
            }
            List<Map<String, Object>> mixed = new ArrayList<>(webItems.size() + items.size());
            mixed.addAll(webItems);
            mixed.addAll(items);
            items = mixed;
        }
        return new CatalogPage(safePage, safeSize, total, pages, items, false, 0, forYouMaxScan,
                false, web.available(), web.status(), web.message(),
                web.coverageSummary(), web.sources());
    }

    /**
     * Parcours intelligent : scan filtré (sans LIKE naïf), score TextMatcher + lexique,
     * fusion optionnelle d'annonces Web éphémères, dédup, pagination par pertinence.
     */
    private CatalogPage searchSmart(
            User user,
            String q, String country, String cityFold, String cityLabel, String contract, String workMode,
            String source, String origin, Instant since,
            Integer minSalary, String experience, String sector, String company,
            int page, int size) {
        // Préfiltre SQL sans q (accents / fautes) puis ranking in-memory.
        List<Object[]> candidateIds = listings.findForYouCandidates(
                null, country, cityFold, contract, workMode, source, origin, since,
                minSalary, experience, sector, company, smartScanCap);
        Map<Long, CatalogListing> byId = new HashMap<>();
        if (!candidateIds.isEmpty()) {
            List<Long> ids = new ArrayList<>(candidateIds.size());
            for (Object[] row : candidateIds) {
                ids.add(((Number) row[0]).longValue());
            }
            for (CatalogListing row : listings.findByIdIn(ids)) {
                byId.put(row.getId(), row);
            }
        }
        List<CatalogListing> pool = new ArrayList<>(byId.values());
        List<CatalogSmartRanker.ScoredListing> ranked = ranker.dedupe(ranker.rank(q, pool));

        // Web éphémère : jamais si filtre « HireLink seulement ».
        boolean allowWeb = origin == null || CatalogListing.ORIGIN_EXTERNAL.equals(origin);
        // Ville lisible pour Jooble (pas la forme foldée SQL).
        WebJobDiscovery.DiscoveryResult web = allowWeb
                ? webDiscovery.search(q, country, cityLabel, webSearchLimit)
                : WebJobDiscovery.DiscoveryResult.unavailable("Filtre limité aux offres HireLink.");
        if (allowWeb && web.available() && web.jobs() != null) {
            for (WebJobDiscovery.DiscoveredJob job : web.jobs()) {
                if (job == null || job.originUrl() == null || job.originUrl().isBlank()) continue;
                if (job.title() == null || job.title().isBlank()) continue;
                if (isDuplicateWeb(ranked, job)) continue;
                double rel = ranker.scoreDiscovered(q, job);
                // Plancher uniquement si le titre/compétences touchent un token métier (≥4 lettres).
                if (rel < CatalogSmartRanker.MIN_SCORE) {
                    if (!webTitleTouchesQuery(q, job)) continue;
                    rel = CatalogSmartRanker.MIN_SCORE;
                }
                // Marqueur éphémère : listing synthétique non persisté (id négatif local à la réponse).
                CatalogListing ephemeral = ephemeralListing(job);
                ranked.add(new CatalogSmartRanker.ScoredListing(ephemeral, rel));
            }
            ranked = ranker.dedupe(ranked);
            ranked.sort(java.util.Comparator
                    .comparingDouble(CatalogSmartRanker.ScoredListing::relevance).reversed()
                    .thenComparing(s -> s.listing().getSortAt(),
                            java.util.Comparator.nullsLast(java.util.Comparator.reverseOrder())));
        }

        long total = ranked.size();
        int offset = page * size;
        List<CatalogSmartRanker.ScoredListing> pageRows = total == 0 || offset >= total
                ? List.of()
                : ranked.subList(offset, (int) Math.min(offset + size, total));
        List<Map<String, Object>> items = new ArrayList<>(pageRows.size());
        for (CatalogSmartRanker.ScoredListing scored : pageRows) {
            CatalogListing row = scored.listing();
            Map<String, Object> dto = row.getId() != null && row.getId() < 0
                    ? toDtoEphemeral(row, scored.relevance())
                    : toDto(row, user, null, scored.relevance());
            items.add(dto);
        }
        int pages = total == 0 ? 0 : (int) Math.ceil(total / (double) size);
        return new CatalogPage(page, size, total, pages, items, false, pool.size(), smartScanCap,
                true, web.available(), web.status(), web.message(),
                web.coverageSummary(), web.sources());
    }

    private static boolean webTitleTouchesQuery(String q, WebJobDiscovery.DiscoveredJob job) {
        if (q == null || job == null) return false;
        String blob = TextMatcher.fold(
                (job.title() == null ? "" : job.title()) + " "
                        + (job.skills() == null ? "" : job.skills()));
        if (blob.isEmpty()) return false;
        for (String w : TextMatcher.fold(q).split(" ")) {
            if (w.length() < 4) continue;
            if (blob.contains(w)) return true;
        }
        return false;
    }

    private boolean isDuplicateWeb(List<CatalogSmartRanker.ScoredListing> ranked, WebJobDiscovery.DiscoveredJob job) {
        String url = job.applyUrl() != null && !job.applyUrl().isBlank() ? job.applyUrl() : job.originUrl();
        String urlNorm = url == null ? "" : url.trim().toLowerCase(Locale.ROOT);
        for (CatalogSmartRanker.ScoredListing s : ranked) {
            CatalogListing row = s.listing();
            if (urlNorm.length() > 8) {
                String a = row.getApplyUrl() == null ? "" : row.getApplyUrl().trim().toLowerCase(Locale.ROOT);
                String o = row.getOriginUrl() == null ? "" : row.getOriginUrl().trim().toLowerCase(Locale.ROOT);
                if (urlNorm.equals(a) || urlNorm.equals(o)) return true;
            }
            if (lexicon.nearDuplicate(row.getTitle(), row.getCompanyName(), job.title(), job.companyName())) {
                return true;
            }
        }
        return false;
    }

    private static CatalogListing ephemeralListing(WebJobDiscovery.DiscoveredJob job) {
        CatalogListing row = new CatalogListing();
        // id négatif : non persisté, signal pour toDtoEphemeral
        row.setId(-1L - Math.floorMod(job.originUrl().hashCode(), 1_000_000));
        row.setOrigin(CatalogListing.ORIGIN_EXTERNAL);
        row.setRefId(0L);
        row.setPublicId("web-" + Integer.toHexString(job.originUrl().hashCode()));
        row.setSourceCode(job.sourceCode() == null ? "WEB" : job.sourceCode());
        row.setTitle(job.title());
        row.setCompanyName(job.companyName() == null || job.companyName().isBlank() ? "Entreprise non précisée" : job.companyName());
        row.setLocationLabel(job.location());
        row.setCity(job.city());
        row.setCountryCode(job.countryCode());
        row.setContractLabel(job.contractType());
        row.setWorkMode(job.workMode());
        row.setSkillsText(job.skills());
        row.setDescription(job.description());
        row.setOriginUrl(job.originUrl());
        row.setApplyUrl(job.applyUrl() == null || job.applyUrl().isBlank() ? job.originUrl() : job.applyUrl());
        row.setUrlQuality("DEEP");
        row.setLinkReliable(true);
        row.setPublishedTrusted(false);
        row.setSortAt(Instant.now());
        row.setSearchBlob(job.title() + " " + job.companyName() + " " + job.skills());
        return row;
    }

    private Map<String, Object> toDtoEphemeral(CatalogListing row, double relevance) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("external", true);
        body.put("id", row.getPublicId());
        body.put("origin", CatalogListing.ORIGIN_EXTERNAL);
        body.put("source", row.getSourceCode());
        String srcLabel = sources.labelOf(row.getSourceCode());
        if (srcLabel == null || srcLabel.isBlank()) {
            srcLabel = "Recherche Web";
        }
        String srcHome = sources.homeOf(row.getSourceCode());
        body.put("sourceLabel", srcLabel);
        body.put("sourceHome", srcHome);
        body.put("boardKey", null);
        body.put("title", row.getTitle());
        body.put("companyName", row.getCompanyName());
        body.put("location", row.getLocationLabel());
        body.put("city", row.getCity());
        body.put("countryCode", row.getCountryCode());
        body.put("contractType", row.getContractLabel());
        body.put("workMode", row.getWorkMode());
        body.put("requiredSkills", row.getSkillsText());
        body.put("description", row.getDescription());
        body.put("originUrl", row.getOriginUrl());
        body.put("applyUrl", row.getApplyUrl());
        body.put("urlQuality", row.getUrlQuality());
        body.put("linkReliable", true);
        body.put("directAccessTrusted", true);
        body.put("publishedAtTrusted", false);
        body.put("createdAt", row.getSortAt());
        body.put("matchesProfile", false);
        body.put("matchAvailable", false);
        body.put("matchLabel", "Annonce Web temporaire — correspondance non calculée ; l’offre reste visible. Candidature sur le site d’origine.");
        body.put("relevanceScore", Math.round(relevance * 1000.0) / 1000.0);
        body.put("ephemeral", true);
        body.put("provenance", ranker.provenanceWeb(new WebJobDiscovery.DiscoveredJob(
                row.getTitle(), row.getCompanyName(), row.getLocationLabel(), row.getCity(), row.getCountryCode(),
                row.getContractLabel(), row.getWorkMode(), row.getSkillsText(), row.getDescription(),
                row.getOriginUrl(), row.getApplyUrl(), row.getSourceCode(),
                srcLabel, srcHome == null ? "" : srcHome)));
        return body;
    }

    private CatalogPage searchForYou(
            User user,
            String q, String country, String cityFold, String cityLabel, String contract, String workMode,
            String source, String origin, Instant since,
            Integer minSalary, String experience, String sector, String company,
            int page, int size, double scoreThreshold) {
        // Profil vide : on liste quand même (score 0), jamais une liste vide « aucune offre au monde ».
        boolean canScore = user != null && matching.hasConfirmedSkills(user);
        // q smart : pas de LIKE SQL (accents / fautes) — filtre textuel après chargement.
        String sqlQ = null;
        long filteredTotal = listings.countFiltered(sqlQ, country, cityFold, contract, workMode, source, origin, since,
                minSalary, experience, sector, company);
        boolean truncated = filteredTotal > forYouMaxScan;
        List<Object[]> candidates = listings.findForYouCandidates(
                sqlQ, country, cityFold, contract, workMode, source, origin, since,
                minSalary, experience, sector, company, forYouMaxScan);
        Map<Long, CatalogListing> listingCache = new HashMap<>();
        if ((q != null && !q.isBlank()) || !candidates.isEmpty()) {
            List<Long> ids = new ArrayList<>();
            for (Object[] row : candidates) ids.add(((Number) row[0]).longValue());
            if (!ids.isEmpty()) {
                for (CatalogListing L : listings.findByIdIn(ids)) listingCache.put(L.getId(), L);
            }
        }
        forYouRuns.deleteOlderThan(Instant.now().minus(Duration.ofHours(1)));
        String runId = UUID.randomUUID().toString();
        List<CatalogForYouRun> batch = new ArrayList<>();
        Map<Long, JobOffer> internalCache = new HashMap<>();
        for (Object[] row : candidates) {
            Long listingId = ((Number) row[0]).longValue();
            String originCode = String.valueOf(row[1]);
            Long refId = ((Number) row[2]).longValue();
            String skills = row[3] == null ? "" : String.valueOf(row[3]);
            if (q != null && !q.isBlank()) {
                CatalogListing L = listingCache.get(listingId);
                if (L == null || ranker.scoreListing(q, L) < CatalogSmartRanker.MIN_SCORE) continue;
            }
            Double score = canScore ? scoreCandidate(user, originCode, refId, skills, internalCache) : 0.0;
            if (score == null) {
                score = 0.0; // score inconnu : on conserve l'offre en bas de liste
            }
            if (scoreThreshold <= 0 || score >= scoreThreshold) {
                batch.add(new CatalogForYouRun(runId, listingId, score));
            }
        }
        if (!batch.isEmpty()) forYouRuns.saveAll(batch);
        forYouRuns.flush();
        try {
            long total = forYouRuns.countEligible(runId, scoreThreshold);
            int offset = page * size;
            List<Long> pageIds = total == 0 || offset >= total
                    ? List.of()
                    : forYouRuns.findPageIds(runId, scoreThreshold, size, offset);
            Map<Long, Double> scores = new HashMap<>();
            for (Object[] s : forYouRuns.scoresForRun(runId, scoreThreshold)) {
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
                if (row != null) {
                    Double forced = scores.get(row.getId());
                    Double textRel = (q != null && !q.isBlank()) ? ranker.scoreListing(q, row) : null;
                    items.add(toDto(row, user, forced, textRel));
                }
            }
            // Web éphémère : même ensemble accessible qu'en mode OFF (page 0).
            // Pas de classement global unique interne+Web (sources hétérogènes) : annonces Web
            // ajoutées avec indicateur neutre après le catalogue trié.
            boolean allowWeb = origin == null || CatalogListing.ORIGIN_EXTERNAL.equals(origin);
            WebJobDiscovery.DiscoveryResult web = allowWeb
                    ? webDiscovery.search(q == null ? "" : q, country, cityLabel, webSearchLimit)
                    : WebJobDiscovery.DiscoveryResult.unavailable("Filtre limité aux offres HireLink.");
            if (allowWeb && web.available() && page == 0 && web.jobs() != null && !web.jobs().isEmpty()) {
                int cap = Math.min(5, Math.max(1, size / 2));
                List<Map<String, Object>> webItems = new ArrayList<>();
                for (WebJobDiscovery.DiscoveredJob job : web.jobs()) {
                    if (job == null || job.originUrl() == null) continue;
                    if (q != null && !q.isBlank()) {
                        double rel = ranker.scoreDiscovered(q, job);
                        if (rel < CatalogSmartRanker.MIN_SCORE) continue;
                        webItems.add(toDtoEphemeral(ephemeralListing(job), rel));
                    } else {
                        webItems.add(toDtoEphemeral(ephemeralListing(job), Math.max(CatalogSmartRanker.MIN_SCORE, 0.35)));
                    }
                    if (webItems.size() >= cap) break;
                }
                List<Map<String, Object>> mixed = new ArrayList<>(webItems.size() + items.size());
                mixed.addAll(webItems);
                mixed.addAll(items);
                items = mixed;
            }
            int pages = total == 0 ? 0 : (int) Math.ceil(total / (double) size);
            return new CatalogPage(page, size, total, pages, items, truncated, candidates.size(), forYouMaxScan,
                    q != null && !q.isBlank(), web.available(), web.status(), web.message(),
                    web.coverageSummary(), web.sources());
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

    private Map<String, Object> toDto(CatalogListing row, User user, Double forcedScore, Double relevance) {
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
        String matchLabel = "Correspondance non calculée — l’offre reste visible.";
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
                if (score != null && score > 0) {
                    matchAvailable = true;
                    matchLabel = "Estimation de compatibilité (compétences, langues, expérience, contrat, lieu) — pas une probabilité d’embauche.";
                }
            } else if (row.getSkillsText() != null && !row.getSkillsText().isBlank()) {
                if (score == null) {
                    score = matching.computeMatchScore(user.getSkills(), row.getSkillsText());
                }
                if (score != null) {
                    matchAvailable = true;
                    matchLabel = "Estimation indicative à partir des compétences citées par la source — pas une décision HireLink.";
                }
            }
        }
        if (score != null) {
            body.put("matchScore", score);
        }
        // Toujours exposer compétences présentes / manquantes quand calculables (transparence « match profil »).
        if (user != null && matching.hasConfirmedSkills(user) && row.getSkillsText() != null && !row.getSkillsText().isBlank()) {
            SkillMatchingService.MatchDetail skills = matching.explain(user.getSkills(), row.getSkillsText());
            body.put("skillsPresent", skills.covered());
            body.put("skillsMissing", skills.missing());
        }
        body.put("matchesProfile", matchAvailable && score != null && score >= forYouThreshold);
        body.put("matchAvailable", matchAvailable);
        body.put("matchLabel", matchLabel);
        if (relevance != null) {
            body.put("relevanceScore", Math.round(relevance * 1000.0) / 1000.0);
        }
        body.put("provenance", ranker.provenance(row, sources));
        enrichTerms(body, row, external);
        body.put("cacheFreshnessAt", row.getSortAt());
        if (external) {
            body.put("applyHint", "Ouvrir l’annonce sur le site d’origine — HireLink ne crée pas de candidature pour cette offre.");
        } else {
            body.put("applyHint", "Fiche et candidature gérées dans HireLink.");
        }
        return body;
    }

    /** Salaire / expérience / secteur : colonnes catalogue d’abord, repli job_offers HireLink. */
    private void enrichTerms(Map<String, Object> body, CatalogListing row, boolean external) {
        if (row.getSalaryMin() != null) body.put("salaryMin", row.getSalaryMin());
        if (row.getSalaryMax() != null) body.put("salaryMax", row.getSalaryMax());
        if (row.getExperienceLevel() != null) body.put("experienceLevel", row.getExperienceLevel());
        if (row.getSector() != null && !row.getSector().isBlank()) body.put("sector", row.getSector());
        if (external) return;
        if (body.containsKey("salaryMin") && body.containsKey("experienceLevel") && body.containsKey("sector")) return;
        JobOffer offer = jobOffers.findById(row.getRefId()).orElse(null);
        if (offer == null) return;
        if (!body.containsKey("salaryMin") && offer.getSalaryMin() != null) body.put("salaryMin", offer.getSalaryMin());
        if (!body.containsKey("salaryMax") && offer.getSalaryMax() != null) body.put("salaryMax", offer.getSalaryMax());
        if (!body.containsKey("experienceLevel") && offer.getExperienceLevel() != null) {
            body.put("experienceLevel", offer.getExperienceLevel().name());
        }
        if (!body.containsKey("sector")) {
            Organization org = offer.getOrganization();
            if (org != null && org.getSector() != null && !org.getSector().isBlank()) {
                body.put("sector", org.getSector());
            }
        }
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