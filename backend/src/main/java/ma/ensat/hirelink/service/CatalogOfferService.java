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
            List<WebJobDiscovery.SourceReport> webSearchSources,
            Map<String, Object> facets) {

        /** Sans compteurs de filtres (facettes). */
        public CatalogPage(
                int page, int size, long totalElements, int totalPages,
                List<Map<String, Object>> items,
                boolean forYouTruncated, int forYouScanned, int forYouScanCap,
                boolean rankedByRelevance, boolean webSearchAvailable, String webSearchStatus,
                String webSearchMessage, String webSearchCoverage,
                List<WebJobDiscovery.SourceReport> webSearchSources) {
            this(page, size, totalElements, totalPages, items, forYouTruncated, forYouScanned, forYouScanCap,
                    rankedByRelevance, webSearchAvailable, webSearchStatus, webSearchMessage, webSearchCoverage,
                    webSearchSources, null);
        }

        /** Copie avec les compteurs de filtres calculés côté serveur. */
        public CatalogPage withFacets(Map<String, Object> computed) {
            return new CatalogPage(page, size, totalElements, totalPages, items, forYouTruncated, forYouScanned,
                    forYouScanCap, rankedByRelevance, webSearchAvailable, webSearchStatus, webSearchMessage,
                    webSearchCoverage, webSearchSources, computed);
        }

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
    private final boolean includeImported;

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
            @Value("${hirelink.web-search.max-results:20}") int webSearchLimit,
            @Value("${hirelink.catalog.include-imported:false}") boolean includeImported) {
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
        this.includeImported = includeImported;
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

    /** Compat : sans champ « Où », sans tri explicite, sans compteurs de filtres. */
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
        return search(user, q, null, country, city, contract, workMode, source, origin, sinceDays,
                minSalary, experience, sector, company, forYou, minMatch, null, false, page, size);
    }

    /**
     * Recherche candidat complète.
     *
     * @param where      champ unique « Où » : ville, pays ou « Ville, Pays » (prioritaire sur country / city)
     * @param sort       {@code relevance} (défaut), {@code date} ou {@code compatibility}
     * @param withFacets calcule aussi les compteurs par choix de filtre (« Stage (12) »)
     */
    @Transactional
    public CatalogPage search(
            User user,
            String q,
            String where,
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
            String sort,
            boolean withFacets,
            int page,
            int size) {
        return search(user, q, where, country, city, contract, workMode, source, origin, sinceDays, minSalary,
                experience, sector, company, forYou, minMatch, sort, withFacets, false, page, size);
    }

    /**
     * Idem, avec {@code deferWeb} : les offres HireLink sont renvoyées tout de suite, sans attendre les sites
     * d'emploi ; l'écran charge ensuite les offres web via {@link #searchWebOffers} (statut Web « DEFERRED »).
     */
    @Transactional
    public CatalogPage search(
            User user,
            String q,
            String where,
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
            String sort,
            boolean withFacets,
            boolean deferWeb,
            int page,
            int size) {
        String whereN = blank(where);
        if (whereN != null) {
            String[] place = resolveWhere(whereN);
            country = place[0];
            city = place[1];
        }
        String sortN = sort == null ? "" : TextMatcher.fold(sort);
        boolean byCompatibility = sortN.startsWith("compat");
        boolean byDate = sortN.equals("date") || sortN.equals("recent");
        boolean rankByProfile = forYou || byCompatibility;
        WebJobDiscovery.DiscoveryResult[] webUsed = new WebJobDiscovery.DiscoveryResult[1];
        // Scores textuels de la requête courante, réutilisés pour les compteurs de filtres.
        Map<Long, Double> scoreMemo = new HashMap<>();
        CatalogPage result = searchCore(user, q, country, city, contract, workMode, source, origin, sinceDays,
                minSalary, experience, sector, company, rankByProfile, minMatch, byDate, deferWeb, page, size, webUsed,
                scoreMemo);
        if (!withFacets) return result;
        // Les annonces Web ne comptent dans le total qu'en recherche libre (sans classement profil).
        boolean smartPath = blank(q) != null && !rankByProfile && !(minMatch != null && minMatch > 0);
        Map<String, Object> facets;
        try {
            facets = computeFacets(q, country, city, contract, workMode, source, origin, sinceDays, minSalary,
                    experience, sector, company, smartPath, smartPath ? webUsed[0] : null, scoreMemo);
        } catch (RuntimeException e) {
            facets = null; // compteurs indicatifs : jamais bloquants pour la recherche
        }
        return result.withFacets(facets);
    }

    /** Offres web d'une recherche, chargées après les offres HireLink (affichage progressif). */
    public record WebOffersPage(
            List<Map<String, Object>> items,
            long total,
            Map<String, Object> facets,
            boolean webSearchAvailable,
            String webSearchStatus,
            String webSearchMessage,
            String webSearchCoverage,
            List<WebJobDiscovery.SourceReport> webSearchSources) {}

    /**
     * Offres web pour les mêmes critères que {@link #search} : mêmes filtres, mêmes compteurs (à additionner
     * à ceux des offres HireLink), doublons d'une offre HireLink retirés (HireLink d'abord).
     * Une source lente ou en panne n'empêche jamais la réponse.
     */
    @Transactional(readOnly = true)
    public WebOffersPage searchWebOffers(
            String q, String where, String country, String city, String contract, String workMode, String origin,
            Integer sinceDays, Integer minSalary, String experience, String sector, String company, String sort,
            boolean withFacets) {
        String whereN = blank(where);
        if (whereN != null) {
            String[] place = resolveWhere(whereN);
            country = place[0];
            city = place[1];
        }
        String qn = blank(q);
        String countryN = blankUpper(country);
        String cityRaw = places.canonicalCity(blank(city));
        String cityN = cityRaw == null ? null : TextMatcher.fold(cityRaw);
        String originN = mapOrigin(origin);
        String companyN = blank(company);
        Instant since = sinceDays != null && sinceDays > 0 ? Instant.now().minus(Duration.ofDays(sinceDays)) : null;
        DimFilters dims = new DimFilters(blank(contract), blankUpper(workMode), since,
                (minSalary != null && minSalary > 0) ? minSalary : null, blankUpper(experience), blank(sector));
        String sortN = sort == null ? "" : TextMatcher.fold(sort);
        boolean byDate = sortN.equals("date") || sortN.equals("recent");

        if (!allowsWeb(originN)) {
            return new WebOffersPage(List.of(), 0, withFacets ? facetsOf(List.of(), dims, 0) : null,
                    false, "SKIPPED", "Filtre limité aux offres HireLink.", "", List.of());
        }
        WebJobDiscovery.DiscoveryResult web = fetchWeb(qn, countryN, cityRaw, originN);
        List<WebJobDiscovery.DiscoveredJob> jobs = web.available() && web.jobs() != null ? web.jobs() : List.of();

        // Offres HireLink de la même recherche : une annonce web identique n'est pas affichée en double.
        List<CatalogSmartRanker.ScoredListing> hirelink = new ArrayList<>();
        java.util.Set<String> hirelinkKeys = new java.util.HashSet<>();
        if (!jobs.isEmpty() && !NO_CATALOG_ORIGIN.equals(catalogOrigin(originN))) {
            try {
                List<Object[]> idRows = listings.findForYouCandidates(null, countryN, cityN, null, null, null,
                        catalogOrigin(originN), null, null, null, null, companyN, smartScanCap);
                List<Long> ids = new ArrayList<>(idRows.size());
                for (Object[] r : idRows) ids.add(((Number) r[0]).longValue());
                List<CatalogListing> pool = ids.isEmpty() ? List.of() : listings.findByIdIn(ids);
                if (qn != null) {
                    hirelink.addAll(ranker.rank(qn, new ArrayList<>(pool)));
                } else {
                    for (CatalogListing row : pool) hirelink.add(new CatalogSmartRanker.ScoredListing(row, 0));
                }
                for (CatalogSmartRanker.ScoredListing s : hirelink) {
                    CatalogListing row = s.listing();
                    hirelinkKeys.add(ma.ensat.hirelink.service.web.WebJobDedupe.key(
                            row.getTitle(), row.getCompanyName(), row.getLocationLabel()));
                }
            } catch (RuntimeException e) {
                hirelink.clear(); // dédoublonnage indicatif : jamais bloquant
                hirelinkKeys.clear();
            }
        }

        // Annonces de la recherche (avant filtres à choix) : base des compteurs ; puis filtres.
        List<CatalogSmartRanker.ScoredListing> scope = new ArrayList<>();
        for (WebJobDiscovery.DiscoveredJob job : jobs) {
            try {
                if (job == null || job.originUrl() == null || job.originUrl().isBlank()) continue;
                if (job.title() == null || job.title().isBlank()) continue;
                CatalogListing eph = ephemeralListing(job);
                if (!passes(eph, null, companyN, null)) continue;
                if (hirelinkKeys.contains(ma.ensat.hirelink.service.web.WebJobDedupe.key(
                        job.title(), job.companyName(), job.location()))) continue;
                if (isDuplicateWeb(hirelink, job)) continue;
                double rel = Math.max(CatalogSmartRanker.MIN_SCORE, 0.35);
                if (qn != null) {
                    rel = ranker.scoreDiscovered(qn, job);
                    if (rel < CatalogSmartRanker.MIN_SCORE) {
                        if (!webTitleTouchesQuery(qn, job)) continue;
                        rel = CatalogSmartRanker.MIN_SCORE;
                    }
                }
                scope.add(new CatalogSmartRanker.ScoredListing(eph, rel));
            } catch (RuntimeException badJob) {
                // une annonce mal formée est ignorée
            }
        }
        List<CatalogSmartRanker.ScoredListing> kept = new ArrayList<>();
        for (CatalogSmartRanker.ScoredListing s : scope) {
            if (passes(s.listing(), dims, companyN, null)) kept.add(s);
        }
        if (byDate) {
            kept.sort(java.util.Comparator.comparing(
                    (CatalogSmartRanker.ScoredListing s) -> dateKey(s.listing()),
                    java.util.Comparator.nullsLast(java.util.Comparator.reverseOrder())));
        } else if (qn != null) {
            kept.sort(java.util.Comparator
                    .comparingDouble(CatalogSmartRanker.ScoredListing::relevance).reversed()
                    .thenComparing(s -> dateKey(s.listing()),
                            java.util.Comparator.nullsLast(java.util.Comparator.reverseOrder())));
        }
        Map<String, WebJobDiscovery.DiscoveredJob> byPublicId = new HashMap<>();
        for (WebJobDiscovery.DiscoveredJob job : jobs) {
            if (job != null && job.originUrl() != null) {
                byPublicId.putIfAbsent("web-" + Integer.toHexString(job.originUrl().hashCode()), job);
            }
        }
        List<Map<String, Object>> items = new ArrayList<>(kept.size());
        for (CatalogSmartRanker.ScoredListing s : kept) {
            Map<String, Object> dto = toDtoEphemeral(s.listing(), s.relevance());
            WebJobDiscovery.DiscoveredJob job = byPublicId.get(s.listing().getPublicId());
            if (job != null && (job.salaryMin() != null || job.salaryMax() != null)) {
                // Montant dans la devise de la source : affiché, jamais comparé aux seuils en MAD.
                dto.put("salaryMin", job.salaryMin());
                dto.put("salaryMax", job.salaryMax());
                dto.put("salaryCurrency", job.salaryCurrency());
                dto.put("salaryPeriod", job.salaryPeriod());
            }
            items.add(dto);
        }
        Map<String, Object> facets = null;
        if (withFacets) {
            List<CatalogListing> rows = new ArrayList<>(scope.size());
            for (CatalogSmartRanker.ScoredListing s : scope) rows.add(s.listing());
            facets = facetsOf(rows, dims, rows.size());
        }
        return new WebOffersPage(items, items.size(), facets, web.available(), web.status(), web.message(),
                web.coverageSummary(), web.sources());
    }

    private CatalogPage searchCore(
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
            boolean byDate,
            boolean deferWeb,
            int page,
            int size,
            WebJobDiscovery.DiscoveryResult[] webUsed,
            Map<Long, Double> scoreMemo) {
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
        // Une seule interrogation des sources Web par recherche ; jamais si filtre « HireLink seulement ».
        // Une source lente ou en échec ne fait jamais échouer la recherche : le catalogue reste affiché.
        WebJobDiscovery.DiscoveryResult web = deferWeb && allowsWeb(originN)
                ? new WebJobDiscovery.DiscoveryResult(false, WEB_DEFERRED, "Offres web chargées séparément.",
                        List.of(), List.of(), "")
                : fetchWeb(qn, countryN, cityRaw, originN);
        webUsed[0] = web;
        // forYou = classement par pertinence profil, SANS seuil automatique.
        // minMatch > 0 = filtre explicite (API) uniquement.
        if (forYou || matchFloor > 0) {
            double threshold = matchFloor > 0 ? matchFloor : 0.0;
            return searchForYou(user, qn, countryN, cityN, cityRaw, contractN, workModeN, sourceN, originN, since,
                    minSalaryN, experienceN, sectorN, companyN, safePage, safeSize, threshold, web);
        }

        // Recherche libre : fuzzy + synonymes + fusion Web (sans favoriser l'interne).
        if (qn != null) {
            return searchSmart(user, qn, countryN, cityN, cityRaw, contractN, workModeN, sourceN, originN, since,
                    minSalaryN, experienceN, sectorN, companyN, safePage, safeSize, byDate, web, scoreMemo);
        }
        DimFilters dims = new DimFilters(contractN, workModeN, since, minSalaryN, experienceN, sectorN);

        long total = listings.countFiltered(null, countryN, cityN, contractN, workModeN, sourceN, catalogOrigin(originN), since,
                minSalaryN, experienceN, sectorN, companyN);
        int offset = safePage * safeSize;
        List<CatalogListing> rows = total == 0 || offset >= total
                ? List.of()
                : listings.findFilteredPage(null, countryN, cityN, contractN, workModeN, sourceN, catalogOrigin(originN), since,
                        minSalaryN, experienceN, sectorN, companyN, safeSize, offset);
        List<Map<String, Object>> items = new ArrayList<>(rows.size());
        for (CatalogListing row : rows) {
            items.add(toDto(row, user, null, null));
        }
        int pages = total == 0 ? 0 : (int) Math.ceil(total / (double) safeSize);
        // q vide : fusion Web éphémère (page 0) — pas pour le filtre « internes seulement ».
        boolean allowWeb = originN == null || CatalogListing.ORIGIN_EXTERNAL.equals(originN);
        // Web éphémère en page 0 : en plus du catalogue, sans évincer d'IDs
        // (sinon la pagination SQL perd des offres). totalElements = catalogue seul.
        // La page 0 peut donc dépasser légèrement safeSize (bonus Web).
        if (allowWeb && web.available() && safePage == 0 && web.jobs() != null && !web.jobs().isEmpty()) {
            int cap = Math.min(5, Math.max(1, safeSize / 2));
            List<Map<String, Object>> webItems = new ArrayList<>();
            for (WebJobDiscovery.DiscoveredJob job : web.jobs()) {
                if (job == null || job.originUrl() == null) continue;
                CatalogListing eph = ephemeralListing(job);
                if (!passes(eph, dims, companyN, null)) continue;
                webItems.add(toDtoEphemeral(eph, Math.max(CatalogSmartRanker.MIN_SCORE, 0.35)));
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
            int page, int size, boolean byDate, WebJobDiscovery.DiscoveryResult web,
            Map<Long, Double> scoreMemo) {
        DimFilters dims = new DimFilters(contract, workMode, since, minSalary, experience, sector);
        // Préfiltre SQL sans q (accents / fautes) puis ranking in-memory.
        List<Object[]> candidateIds = listings.findForYouCandidates(
                null, country, cityFold, contract, workMode, source, catalogOrigin(origin), since,
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
        List<CatalogSmartRanker.ScoredListing> ranked = ranker.dedupe(ranker.rank(q, pool, scoreMemo));

        // Web éphémère : jamais si filtre « HireLink seulement ».
        boolean allowWeb = origin == null || CatalogListing.ORIGIN_EXTERNAL.equals(origin);
        if (allowWeb && web.available() && web.jobs() != null) {
            for (WebJobDiscovery.DiscoveredJob job : web.jobs()) {
                if (job == null || job.originUrl() == null || job.originUrl().isBlank()) continue;
                if (job.title() == null || job.title().isBlank()) continue;
                // Les filtres actifs (contrat, mode, date, entreprise…) s'appliquent aussi aux annonces Web.
                if (!passes(ephemeralListing(job), dims, company, null)) continue;
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
        if (byDate) {
            // Tri « Date » : plus récentes d'abord ; annonces Web sans date fiable en fin de liste.
            ranked = new ArrayList<>(ranked);
            ranked.sort(java.util.Comparator.comparing(
                    (CatalogSmartRanker.ScoredListing s) -> dateKey(s.listing()),
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

    // ------------------------------------------------------------------ filtres, « Où », facettes

    /** Choix proposés par l'écran de recherche : les compteurs sont calculés pour chacun. */
    static final List<String> SECTOR_OPTIONS = List.of(
            "Informatique", "Finance", "Santé", "Commerce", "Industrie", "Services", "Éducation");
    static final List<Integer> SALARY_OPTIONS = List.of(5000, 8000, 12000, 15000, 20000);
    static final List<Integer> SINCE_OPTIONS = List.of(7, 30, 90);

    /** Filtres à choix (contrat, mode, date, salaire, expérience, secteur) déjà normalisés. */
    record DimFilters(String contract, String workMode, Instant since, Integer minSalary,
                      String experience, String sector) {}

    /**
     * Champ unique « Où » → {pays ISO, ville}. Accepte « Casablanca », « Maroc », « MA » ou « Casablanca, Maroc ».
     */
    String[] resolveWhere(String where) {
        String[] parts = where.split(",");
        String first = parts[0].trim();
        String last = parts.length > 1 ? parts[parts.length - 1].trim() : "";
        if (!last.isEmpty()) {
            return new String[] {PlaceCatalogService.normalizeCountryCode(last), first.isEmpty() ? null : first};
        }
        String code = first.length() == 2 || places.isCountry(first)
                ? PlaceCatalogService.normalizeCountryCode(first)
                : null;
        if (code == null && first.length() != 2) {
            String fold = TextMatcher.fold(first);
            if (fold.equals("maroc") || fold.equals("morocco") || fold.equals("france")) {
                code = PlaceCatalogService.normalizeCountryCode(first);
            }
        }
        return code != null ? new String[] {code, null} : new String[] {null, first};
    }

    /** Interrogation Web unique et protégée : une panne ou une lenteur ne fait jamais échouer la recherche. */
    private WebJobDiscovery.DiscoveryResult fetchWeb(String q, String country, String cityLabel, String origin) {
        boolean allowWeb = origin == null || CatalogListing.ORIGIN_EXTERNAL.equals(origin);
        if (!allowWeb) return WebJobDiscovery.DiscoveryResult.unavailable("Filtre limité aux offres HireLink.");
        try {
            WebJobDiscovery.DiscoveryResult r = webDiscovery.search(q == null ? "" : q, country, cityLabel, webSearchLimit);
            return r != null ? r : WebJobDiscovery.DiscoveryResult.unavailable(WEB_DOWN);
        } catch (RuntimeException e) {
            return WebJobDiscovery.DiscoveryResult.unavailable(WEB_DOWN);
        }
    }

    static final String WEB_DEFERRED = "DEFERRED";

    private static boolean allowsWeb(String origin) {
        return origin == null || CatalogListing.ORIGIN_EXTERNAL.equals(origin);
    }

    private static final String WEB_DOWN =
            "Recherche Web momentanément indisponible : les offres du catalogue restent affichées.";

    /**
     * Même logique que les filtres SQL (contrat, mode, date fiable, salaire, expérience, secteur),
     * plus l'entreprise en « contient » insensible à la casse et aux accents. {@code skip} ignore une dimension.
     */
    static boolean passes(CatalogListing row, DimFilters f, String company, String skip) {
        if (row == null) return false;
        if (company != null && !"company".equals(skip)
                && !TextMatcher.fold(row.getCompanyName()).contains(TextMatcher.fold(company))) {
            return false;
        }
        if (f == null) return true;
        if (f.contract() != null && !"contract".equals(skip)
                && !(isUnspecified(f.contract()) ? isBlankValue(row.getContractLabel())
                        : TextMatcher.fold(f.contract()).equals(TextMatcher.fold(row.getContractLabel())))) {
            return false;
        }
        if (f.workMode() != null && !"workMode".equals(skip)
                && !(isUnspecified(f.workMode()) ? isBlankValue(row.getWorkMode())
                        : f.workMode().equalsIgnoreCase(row.getWorkMode() == null ? "" : row.getWorkMode().trim()))) {
            return false;
        }
        if (f.since() != null && !"since".equals(skip)
                && !(row.isPublishedTrusted() && row.getPublishedAt() != null && !row.getPublishedAt().isBefore(f.since()))) {
            return false;
        }
        if (f.minSalary() != null && !"minSalary".equals(skip)) {
            Integer sal = salaryOf(row);
            if (sal == null || sal < f.minSalary()) return false;
        }
        if (f.experience() != null && !"experience".equals(skip)
                && !(isUnspecified(f.experience()) ? isBlankValue(row.getExperienceLevel())
                        : f.experience().equalsIgnoreCase(row.getExperienceLevel() == null ? "" : row.getExperienceLevel().trim()))) {
            return false;
        }
        if (f.sector() != null && !"sector".equals(skip)
                && !(isUnspecified(f.sector()) ? isBlankValue(row.getSector())
                        : TextMatcher.fold(row.getSector()).contains(TextMatcher.fold(f.sector())))) {
            return false;
        }
        return true;
    }

    /**
     * Choix « Non précisé » des filtres Contrat, Mode, Expérience et Secteur : sélectionne les offres qui ne
     * renseignent pas cette information. Les compteurs l'affichent à côté des autres choix, si bien qu'aucune
     * offre n'est écartée sans que le candidat le voie.
     */
    public static final String UNSPECIFIED = "NON-PRECISE";

    static boolean isUnspecified(String filterValue) {
        return filterValue != null && UNSPECIFIED.equalsIgnoreCase(filterValue.trim());
    }

    private static boolean isBlankValue(String v) {
        return v == null || v.isBlank();
    }

    private static Integer salaryOf(CatalogListing row) {
        return row.getSalaryMax() != null ? row.getSalaryMax() : row.getSalaryMin();
    }

    /** Date de tri « plus récentes » : date de publication fiable, sinon date catalogue ; Web éphémère = inconnue. */
    private static Instant dateKey(CatalogListing row) {
        if (row == null) return null;
        if (row.getId() != null && row.getId() < 0) {
            // Web : date annoncée par la source si elle est connue.
            return row.isPublishedTrusted() ? row.getPublishedAt() : null;
        }
        if (row.isPublishedTrusted() && row.getPublishedAt() != null) return row.getPublishedAt();
        return row.getSortAt();
    }

    /**
     * Compteurs par choix de filtre, calculés côté serveur : chaque compteur tient compte de la recherche
     * et de tous les AUTRES filtres actifs (ex. « Stage (12) » avec Mode = Remote déjà choisi).
     */
    private Map<String, Object> computeFacets(
            String q, String country, String city, String contract, String workMode, String source, String origin,
            Integer sinceDays, Integer minSalary, String experience, String sector, String company,
            boolean smartPath, WebJobDiscovery.DiscoveryResult web,
            Map<Long, Double> scoreMemo) {
        String qn = blank(q);
        String countryN = blankUpper(country);
        String cityRaw = places.canonicalCity(blank(city));
        String cityN = cityRaw == null ? null : TextMatcher.fold(cityRaw);
        String sourceN = blankUpper(source);
        String originN = mapOrigin(origin);
        String companyN = blank(company);
        Instant since = sinceDays != null && sinceDays > 0 ? Instant.now().minus(Duration.ofDays(sinceDays)) : null;
        DimFilters f = new DimFilters(blank(contract), blankUpper(workMode), since,
                (minSalary != null && minSalary > 0) ? minSalary : null, blankUpper(experience), blank(sector));

        // Même périmètre que la recherche, sans les filtres à choix (appliqués ci-dessous dimension par dimension).
        List<Object[]> idRows = listings.findForYouCandidates(
                null, countryN, cityN, null, null, sourceN, catalogOrigin(originN), null, null, null, null, companyN, smartScanCap);
        List<Long> ids = new ArrayList<>(idRows.size());
        for (Object[] r : idRows) ids.add(((Number) r[0]).longValue());
        List<CatalogListing> pool = ids.isEmpty() ? new ArrayList<>() : new ArrayList<>(listings.findByIdIn(ids));
        List<CatalogListing> rows = new ArrayList<>();
        if (qn != null) {
            List<CatalogSmartRanker.ScoredListing> scored = ranker.rank(qn, pool, scoreMemo);
            if (smartPath) scored = ranker.dedupe(scored);
            for (CatalogSmartRanker.ScoredListing sl : scored) rows.add(sl.listing());
            if (smartPath && web != null && web.available() && web.jobs() != null) {
                for (WebJobDiscovery.DiscoveredJob job : web.jobs()) {
                    if (job == null || job.originUrl() == null || job.originUrl().isBlank()) continue;
                    if (job.title() == null || job.title().isBlank()) continue;
                    CatalogListing eph = ephemeralListing(job);
                    if (!passes(eph, null, companyN, null)) continue;
                    if (isDuplicateWeb(scored, job)) continue;
                    if (ranker.scoreDiscovered(qn, job) < CatalogSmartRanker.MIN_SCORE && !webTitleTouchesQuery(qn, job)) {
                        continue;
                    }
                    rows.add(eph);
                }
            }
        } else {
            rows.addAll(pool);
        }
        return facetsOf(rows, f, pool.size());
    }

    /** Compteurs par choix pour un ensemble d'offres déjà restreint à la recherche (sans les filtres à choix). */
    private Map<String, Object> facetsOf(List<CatalogListing> rows, DimFilters f, int scanned) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("contract", countValues(rows, f, "contract", CatalogListing::getContractLabel, false));
        out.put("workMode", countValues(rows, f, "workMode", CatalogListing::getWorkMode, true));
        out.put("experience", countValues(rows, f, "experience", CatalogListing::getExperienceLevel, true));
        Map<String, Integer> sinceCounts = new LinkedHashMap<>();
        for (int days : SINCE_OPTIONS) {
            Instant from = Instant.now().minus(Duration.ofDays(days));
            sinceCounts.put(String.valueOf(days), (int) rows.stream()
                    .filter(r -> passes(r, f, null, "since"))
                    .filter(r -> r.isPublishedTrusted() && r.getPublishedAt() != null && !r.getPublishedAt().isBefore(from))
                    .count());
        }
        out.put("since", sinceCounts);
        Map<String, Integer> sectorCounts = new LinkedHashMap<>();
        for (String option : SECTOR_OPTIONS) {
            String needle = TextMatcher.fold(option);
            sectorCounts.put(option, (int) rows.stream()
                    .filter(r -> passes(r, f, null, "sector"))
                    .filter(r -> TextMatcher.fold(r.getSector()).contains(needle))
                    .count());
        }
        int noSector = (int) rows.stream()
                .filter(r -> passes(r, f, null, "sector"))
                .filter(r -> isBlankValue(r.getSector()))
                .count();
        if (noSector > 0) sectorCounts.put(UNSPECIFIED, noSector);
        out.put("sector", sectorCounts);
        Map<String, Integer> salaryCounts = new LinkedHashMap<>();
        boolean salaryAvailable = false;
        for (CatalogListing r : rows) {
            if (salaryOf(r) != null && passes(r, f, null, "minSalary")) { salaryAvailable = true; break; }
        }
        for (int threshold : SALARY_OPTIONS) {
            salaryCounts.put(String.valueOf(threshold), (int) rows.stream()
                    .filter(r -> passes(r, f, null, "minSalary"))
                    .filter(r -> salaryOf(r) != null && salaryOf(r) >= threshold)
                    .count());
        }
        out.put("minSalary", salaryCounts);
        out.put("salaryAvailable", salaryAvailable);
        out.put("total", rows.stream().filter(r -> passes(r, f, null, null)).count());
        out.put("scanned", scanned);
        out.put("scanCap", smartScanCap);
        return out;
    }

    /** Valeurs présentes pour une dimension, comptées avec les autres filtres actifs (insensible à la casse). */
    private static Map<String, Integer> countValues(
            List<CatalogListing> rows, DimFilters f, String dim,
            java.util.function.Function<CatalogListing, String> getter, boolean upper) {
        Map<String, String> labels = new LinkedHashMap<>();
        Map<String, Integer> counts = new HashMap<>();
        int unspecified = 0;
        for (CatalogListing r : rows) {
            if (!passes(r, f, null, dim)) continue;
            String raw = getter.apply(r);
            if (raw == null || raw.isBlank()) { unspecified++; continue; }
            String label = upper ? raw.trim().toUpperCase(Locale.ROOT) : raw.trim();
            String key = TextMatcher.fold(label);
            if (key.isEmpty()) continue;
            labels.putIfAbsent(key, label);
            counts.merge(key, 1, Integer::sum);
        }
        Map<String, Integer> out = new LinkedHashMap<>();
        counts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .forEach(e -> out.put(labels.get(e.getKey()), e.getValue()));
        if (unspecified > 0) out.put(UNSPECIFIED, unspecified);
        return out;
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
        row.setContractLabel(ma.ensat.hirelink.service.web.WebContractLabels.french(job.contractType()));
        row.setWorkMode(job.workMode());
        row.setSkillsText(job.skills());
        row.setDescription(job.description());
        row.setOriginUrl(job.originUrl());
        row.setApplyUrl(job.applyUrl() == null || job.applyUrl().isBlank() ? job.originUrl() : job.applyUrl());
        row.setUrlQuality("DEEP");
        row.setLinkReliable(true);
        // Date annoncée par la source : utilisée pour le filtre Date et le tri par date ; inconnue = null.
        row.setPublishedAt(job.publishedAt());
        row.setPublishedTrusted(job.publishedAt() != null);
        row.setSortAt(job.publishedAt() != null ? job.publishedAt() : Instant.now());
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
        body.put("publishedAt", row.isPublishedTrusted() ? row.getPublishedAt() : null);
        body.put("publishedAtTrusted", row.isPublishedTrusted());
        body.put("createdAt", row.isPublishedTrusted() ? row.getPublishedAt() : null);
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
            int page, int size, double scoreThreshold, WebJobDiscovery.DiscoveryResult web) {
        DimFilters dims = new DimFilters(contract, workMode, since, minSalary, experience, sector);
        // Profil vide : on liste quand même (score 0), jamais une liste vide « aucune offre au monde ».
        boolean canScore = user != null && matching.hasConfirmedSkills(user);
        // q smart : pas de LIKE SQL (accents / fautes) — filtre textuel après chargement.
        String sqlQ = null;
        long filteredTotal = listings.countFiltered(sqlQ, country, cityFold, contract, workMode, source, catalogOrigin(origin), since,
                minSalary, experience, sector, company);
        boolean truncated = filteredTotal > forYouMaxScan;
        List<Object[]> candidates = listings.findForYouCandidates(
                sqlQ, country, cityFold, contract, workMode, source, catalogOrigin(origin), since,
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
            // Pas de « : 0.0 » ici : le mélange Double/double déballait un score null (NullPointerException
            // → « Une erreur interne est survenue ») dès qu'une offre n'était pas évaluable (ex. sans compétences).
            Double score = canScore ? scoreCandidate(user, originCode, refId, skills, internalCache) : null;
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
            if (allowWeb && web.available() && page == 0 && web.jobs() != null && !web.jobs().isEmpty()) {
                int cap = Math.min(5, Math.max(1, size / 2));
                List<Map<String, Object>> webItems = new ArrayList<>();
                for (WebJobDiscovery.DiscoveredJob job : web.jobs()) {
                    if (job == null || job.originUrl() == null) continue;
                    if (!passes(ephemeralListing(job), dims, company, null)) continue;
                    if (q != null && !q.isBlank()) {
                        double rel = ranker.scoreDiscovered(q, job);
                        if (rel < CatalogSmartRanker.MIN_SCORE) continue;
                        webItems.add(toDtoEphemeral(ephemeralListing(job), rel));
                    } else {
                        webItems.add(toDtoEphemeral(ephemeralListing(job), Math.max(CatalogSmartRanker.MIN_SCORE, 0.35)));
                    }
                    if (webItems.size() >= cap) break;
                }
                // Après le catalogue trié par compatibilité (score non calculable pour le Web).
                List<Map<String, Object>> mixed = new ArrayList<>(webItems.size() + items.size());
                mixed.addAll(items);
                mixed.addAll(webItems);
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

    /**
     * Origine lue dans le catalogue stocké : offres HireLink uniquement (les annonces importées ne sont plus
     * proposées au candidat ; seule la recherche Web en direct complète). « Sources externes » = Web seul.
     * {@code hirelink.catalog.include-imported=true} rétablit l'ancien comportement (retrait réversible).
     */
    private String catalogOrigin(String origin) {
        if (includeImported) return origin;
        return CatalogListing.ORIGIN_EXTERNAL.equals(origin) ? NO_CATALOG_ORIGIN : CatalogListing.ORIGIN_INTERNAL;
    }

    /** Valeur d'origine qui ne correspond à aucune ligne : catalogue vide, Web seul. */
    private static final String NO_CATALOG_ORIGIN = "WEB_ONLY";

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