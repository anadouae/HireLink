package ma.ensat.hirelink.service;

import ma.ensat.hirelink.model.CatalogListing;
import ma.ensat.hirelink.model.ExternalJobOffer;
import ma.ensat.hirelink.model.JobOffer;
import ma.ensat.hirelink.model.Organization;
import ma.ensat.hirelink.repository.CatalogListingRepository;
import ma.ensat.hirelink.repository.ExternalJobOfferRepository;
import ma.ensat.hirelink.repository.JobOfferRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Reconstruit la projection catalog_listings a partir des tables sources.
 * Exclut MAROC_EMPLOI (sample) et les offres non listables.
 */
@Service
public class CatalogListingIndexer {

    private static final Logger log = LoggerFactory.getLogger(CatalogListingIndexer.class);

    private final CatalogListingRepository listings;
    private final JobOfferRepository jobOffers;
    private final ExternalJobOfferRepository externalOffers;
    private final PlaceCatalogService places;
    private final Set<String> excludeSources;

    public CatalogListingIndexer(
            CatalogListingRepository listings,
            JobOfferRepository jobOffers,
            ExternalJobOfferRepository externalOffers,
            PlaceCatalogService places,
            @Value("${hirelink.catalog.exclude-sources:MAROC_EMPLOI}") String excludeSourcesCsv) {
        this.listings = listings;
        this.jobOffers = jobOffers;
        this.externalOffers = externalOffers;
        this.places = places;
        this.excludeSources = new HashSet<>();
        for (String s : excludeSourcesCsv.split(",")) {
            if (!s.isBlank()) excludeSources.add(s.trim().toUpperCase(Locale.ROOT));
        }
    }

    @Transactional
    public int rebuild() {
        listings.deleteAllListings();
        listings.flush();
        Map<String, CatalogListing> byDedupe = new HashMap<>();
        int kept = 0;
        for (JobOffer offer : jobOffers.findAll()) {
            if (!isInternalListable(offer)) continue;
            CatalogListing row = fromInternal(offer);
            if (acceptDedupe(byDedupe, row)) {
                listings.save(row);
                kept++;
            }
        }
        for (ExternalJobOffer offer : externalOffers.findAll()) {
            if (!isExternalListable(offer)) continue;
            CatalogListing row = fromExternal(offer);
            if (acceptDedupe(byDedupe, row)) {
                listings.save(row);
                kept++;
            }
        }
        log.info("Catalogue reconstruit : {} lignes (sources exclues={})", kept, excludeSources);
        return kept;
    }

    /** Synchronise une offre interne : upsert si listable, sinon retrait. Idempotent. */
    @Transactional
    public void syncInternal(JobOffer offer) {
        if (offer == null || offer.getId() == null) return;
        if (!isInternalListable(offer)) {
            removeByOriginRef(CatalogListing.ORIGIN_INTERNAL, offer.getId());
            return;
        }
        upsert(fromInternal(offer));
    }

    /** Synchronise une offre externe : upsert si listable, sinon retrait. Idempotent. */
    @Transactional
    public void syncExternal(ExternalJobOffer offer) {
        if (offer == null || offer.getId() == null) return;
        if (!isExternalListable(offer)) {
            removeByOriginRef(CatalogListing.ORIGIN_EXTERNAL, offer.getId());
            return;
        }
        upsert(fromExternal(offer));
    }

    @Transactional
    public void removeByOriginRef(String origin, Long refId) {
        listings.findByOriginAndRefId(origin, refId).ifPresent(listings::delete);
    }

    private void upsert(CatalogListing row) {
        listings.findByOriginAndRefId(row.getOrigin(), row.getRefId()).ifPresent(existing -> row.setId(existing.getId()));
        // Soft-dedupe URL : si une autre ligne porte la meme cle avec priorite inferieure, la retirer.
        if (row.getDedupeKey() != null && !row.getDedupeKey().isBlank()) {
            listings.findByDedupeKey(row.getDedupeKey()).ifPresent(other -> {
                boolean same = other.getOrigin().equals(row.getOrigin()) && other.getRefId().equals(row.getRefId());
                if (!same && priority(row) >= priority(other)) {
                    listings.delete(other);
                }
            });
        }
        if (row.getId() != null) {
            CatalogListing managed = listings.findById(row.getId()).orElse(null);
            if (managed != null) {
                copyFields(managed, row);
                listings.save(managed);
                return;
            }
        }
        listings.save(row);
    }

    private static void copyFields(CatalogListing target, CatalogListing src) {
        target.setOrigin(src.getOrigin());
        target.setRefId(src.getRefId());
        target.setPublicId(src.getPublicId());
        target.setSourceCode(src.getSourceCode());
        target.setBoardKey(src.getBoardKey());
        target.setTitle(src.getTitle());
        target.setCompanyName(src.getCompanyName());
        target.setLocationLabel(src.getLocationLabel());
        target.setCity(src.getCity());
        target.setCityFold(src.getCityFold());
        target.setCountryCode(src.getCountryCode());
        target.setContractLabel(src.getContractLabel());
        target.setWorkMode(src.getWorkMode());
        target.setSkillsText(src.getSkillsText());
        target.setDescription(src.getDescription());
        target.setOriginUrl(src.getOriginUrl());
        target.setApplyUrl(src.getApplyUrl());
        target.setUrlQuality(src.getUrlQuality());
        target.setLinkReliable(src.isLinkReliable());
        target.setPublishedAt(src.getPublishedAt());
        target.setPublishedTrusted(src.isPublishedTrusted());
        target.setSortAt(src.getSortAt());
        target.setDedupeKey(src.getDedupeKey());
        target.setSearchBlob(src.getSearchBlob());
    }


    private boolean acceptDedupe(Map<String, CatalogListing> byDedupe, CatalogListing row) {
        if (row.getDedupeKey() == null || row.getDedupeKey().isBlank()) return true;
        CatalogListing existing = byDedupe.get(row.getDedupeKey());
        if (existing == null) {
            byDedupe.put(row.getDedupeKey(), row);
            return true;
        }
        // Garde HireLink plutot qu'externe ; sinon garde le plus recent sort_at.
        int priNew = priority(row);
        int priOld = priority(existing);
        if (priNew > priOld || (priNew == priOld && row.getSortAt().isAfter(existing.getSortAt()))) {
            listings.delete(existing);
            byDedupe.put(row.getDedupeKey(), row);
            return true;
        }
        return false;
    }

    private static int priority(CatalogListing row) {
        if (CatalogListing.ORIGIN_INTERNAL.equals(row.getOrigin())) return 3;
        if (row.isLinkReliable()) return 2;
        return 1;
    }

    private boolean isInternalListable(JobOffer offer) {
        return offer.isPublished()
                && !offer.isSuspended()
                && offer.getClosedAt() == null
                && offer.getArchivedAt() == null;
    }

    private boolean isExternalListable(ExternalJobOffer offer) {
        if (offer.getSource() != null && excludeSources.contains(offer.getSource().toUpperCase(Locale.ROOT))) {
            return false;
        }
        if (!offer.isAvailable() || offer.isQuarantined() || offer.isHiddenByAdmin()) return false;
        String q = offer.getUrlQuality();
        return q == null || "DEEP".equals(q) || "UNKNOWN".equals(q);
    }

    private CatalogListing fromInternal(JobOffer offer) {
        CatalogListing row = new CatalogListing();
        row.setOrigin(CatalogListing.ORIGIN_INTERNAL);
        row.setRefId(offer.getId());
        row.setPublicId(String.valueOf(offer.getId()));
        row.setSourceCode("HIRELINK");
        row.setTitle(offer.getTitle());
        String company = companyName(offer);
        row.setCompanyName(company);
        row.setLocationLabel(offer.getLocation());
        Geo geo = geoOf(offer.getLocation());
        row.setCity(geo.city);
        row.setCityFold(geo.city == null ? null : ma.ensat.hirelink.service.TextMatcher.fold(geo.city));
        row.setCountryCode(geo.country);
        row.setContractLabel(offer.getContractType());
        row.setWorkMode(offer.getWorkMode() == null ? null : offer.getWorkMode().name());
        row.setSkillsText(blankToNull(offer.getRequiredSkills()));
        row.setDescription(offer.getDescription());
        row.setOriginUrl(null);
        row.setApplyUrl(null);
        row.setUrlQuality(null);
        row.setLinkReliable(true);
        Instant published = offer.getCreatedAt() != null ? offer.getCreatedAt() : Instant.now();
        row.setPublishedAt(published);
        row.setPublishedTrusted(true);
        row.setSortAt(published);
        row.setDedupeKey(null);
        row.setSearchBlob(blob(offer.getTitle(), company, offer.getLocation(), offer.getRequiredSkills(),
                offer.getContractType(), offer.getDescription()));
        return row;
    }

    private CatalogListing fromExternal(ExternalJobOffer offer) {
        CatalogListing row = new CatalogListing();
        row.setOrigin(CatalogListing.ORIGIN_EXTERNAL);
        row.setRefId(offer.getId());
        row.setPublicId("ext-" + offer.getId());
        row.setSourceCode(offer.getSource());
        row.setBoardKey(offer.getBoardKey());
        row.setTitle(offer.getTitle());
        row.setCompanyName(offer.getCompany());
        row.setLocationLabel(offer.getLocation());
        Geo geo = geoOf(offer.getLocation());
        if (offer.getCountryCode() != null && !offer.getCountryCode().isBlank()) {
            geo = new Geo(geo.city, offer.getCountryCode().trim().toUpperCase(Locale.ROOT));
        }
        row.setCity(geo.city);
        row.setCityFold(geo.city == null ? null : ma.ensat.hirelink.service.TextMatcher.fold(geo.city));
        row.setCountryCode(geo.country);
        row.setContractLabel(offer.getContractLabel());
        row.setWorkMode(null);
        row.setSkillsText(blankToNull(offer.getSkills()));
        row.setDescription(offer.getDescription());
        row.setOriginUrl(offer.getOriginUrl());
        row.setApplyUrl(offer.getApplyUrl());
        row.setUrlQuality(offer.getUrlQuality());
        row.setLinkReliable("DEEP".equals(offer.getUrlQuality()));
        Instant published = offer.getPublishedAt();
        boolean trusted = published != null;
        row.setPublishedAt(published);
        row.setPublishedTrusted(trusted);
        Instant sort = published != null ? published : (offer.getLastSeenAt() != null ? offer.getLastSeenAt() : offer.getLastSyncedAt());
        if (sort == null) sort = Instant.EPOCH;
        row.setSortAt(sort);
        row.setDedupeKey(urlDedupeKey(offer.getOriginUrl()));
        row.setSearchBlob(blob(offer.getTitle(), offer.getCompany(), offer.getLocation(), offer.getSkills(),
                offer.getContractLabel(), offer.getDescription(), offer.getSource()));
        return row;
    }

    private String companyName(JobOffer offer) {
        Organization org = offer.getOrganization();
        if (org != null && org.getName() != null && !org.getName().isBlank()) return org.getName();
        if (offer.getRecruiter() != null && offer.getRecruiter().getFullName() != null) {
            return offer.getRecruiter().getFullName();
        }
        return "Entreprise";
    }

    private Geo geoOf(String location) {
        if (location == null || location.isBlank()) return new Geo(null, null);
        String city = null;
        for (String segment : location.split("[·|,/]")) {
            String s = segment.trim();
            if (s.isEmpty()) continue;
            if (places.isCountry(s)) continue;
            city = s;
            break;
        }
        if (city == null) city = location.trim();
        city = places.canonicalCity(city);
        String countryName = places.countryOf(city);
        String code = null;
        if (countryName != null && !countryName.isBlank()) {
            String fold = countryName.toLowerCase(Locale.ROOT);
            if (fold.contains("maroc") || fold.contains("morocco")) code = "MA";
        }
        return new Geo(city, code);
    }

    private static String urlDedupeKey(String url) {
        if (url == null || url.isBlank()) return null;
        String n = url.trim().toLowerCase(Locale.ROOT);
        while (n.endsWith("/")) n = n.substring(0, n.length() - 1);
        int q = n.indexOf('?');
        if (q > 0) n = n.substring(0, q);
        try {
            byte[] dig = MessageDigest.getInstance("SHA-256").digest(n.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(dig);
        } catch (Exception e) {
            return null;
        }
    }

    private static String blob(String... parts) {
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p == null || p.isBlank()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(p);
        }
        return sb.toString();
    }

    private static String blankToNull(String v) {
        if (v == null) return null;
        String t = v.trim();
        return t.isEmpty() ? null : t;
    }

    private record Geo(String city, String country) {}
}