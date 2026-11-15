package ma.ensat.hirelink.service;

import ma.ensat.hirelink.model.CatalogListing;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Score de pertinence textuelle (fuzzy + synonymes) pour le catalogue unifié.
 * Ne favorise pas l'origine INTERNAL : le classement suit le score, puis la date.
 */
@Component
public class CatalogSmartRanker {

    /** Seuil minimal pour conserver un résultat (faute de frappe faible). */
    public static final double MIN_SCORE = 0.18;

    private final TextMatcher matcher;
    private final JobSearchLexicon lexicon;

    public CatalogSmartRanker(TextMatcher matcher, JobSearchLexicon lexicon) {
        this.matcher = matcher;
        this.lexicon = lexicon;
    }

    public record ScoredListing(CatalogListing listing, double relevance) {}

    public List<String> variantsOf(String query) {
        return lexicon.expand(query);
    }

    /** Score 0..1 d'une ligne catalogue contre la requête (meilleure variante). */
    public double scoreListing(String query, CatalogListing row) {
        if (query == null || query.isBlank() || row == null) return 0;
        List<String> variants = lexicon.expand(query);
        double best = 0;
        for (String v : variants) {
            best = Math.max(best, scoreAgainst(v, row));
            if (best >= 0.98) return best;
        }
        return best;
    }

    private double scoreAgainst(String variant, CatalogListing row) {
        double title = matcher.score(variant, nullToEmpty(row.getTitle())) * 1.0;
        double company = matcher.score(variant, nullToEmpty(row.getCompanyName())) * 0.55;
        double skills = matcher.score(variant, nullToEmpty(row.getSkillsText())) * 0.75;
        double place = matcher.score(variant,
                nullToEmpty(row.getCity()) + " " + nullToEmpty(row.getLocationLabel()) + " " + nullToEmpty(row.getCountryCode())) * 0.45;
        double contract = matcher.score(variant,
                nullToEmpty(row.getContractLabel()) + " " + nullToEmpty(row.getWorkMode())) * 0.35;
        double desc = matcher.score(variant, nullToEmpty(row.getDescription())) * 0.25;
        double blob = matcher.score(variant, nullToEmpty(row.getSearchBlob())) * 0.4;
        return clamp(Math.max(title, Math.max(company, Math.max(skills, Math.max(place, Math.max(contract, Math.max(desc, blob)))))));
    }

    /** Score d'une annonce Web éphémère. */
    public double scoreDiscovered(String query, WebJobDiscovery.DiscoveredJob job) {
        if (query == null || query.isBlank() || job == null) return 0;
        CatalogListing fake = new CatalogListing();
        fake.setTitle(job.title());
        fake.setCompanyName(job.companyName());
        fake.setCity(job.city());
        fake.setLocationLabel(job.location());
        fake.setCountryCode(job.countryCode());
        fake.setContractLabel(job.contractType());
        fake.setWorkMode(job.workMode());
        fake.setSkillsText(job.skills());
        fake.setDescription(job.description());
        fake.setSearchBlob(String.join(" ",
                nullToEmpty(job.title()), nullToEmpty(job.companyName()), nullToEmpty(job.skills()),
                nullToEmpty(job.description()), nullToEmpty(job.location())));
        return scoreListing(query, fake);
    }

    public List<ScoredListing> rank(String query, List<CatalogListing> rows) {
        List<ScoredListing> out = new ArrayList<>();
        if (rows == null) return out;
        boolean hasQuery = query != null && !query.isBlank();
        for (CatalogListing row : rows) {
            double s = hasQuery ? scoreListing(query, row) : 1.0;
            if (!hasQuery || s >= MIN_SCORE) {
                out.add(new ScoredListing(row, s));
            }
        }
        out.sort(Comparator
                .comparingDouble(ScoredListing::relevance).reversed()
                .thenComparing((ScoredListing s) -> s.listing().getSortAt(),
                        Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(s -> s.listing().getId(), Comparator.nullsLast(Long::compareTo)));
        return out;
    }

    /**
     * Déduplication soft titre+entreprise : conserve le meilleur score
     * (origine indifférente — pas de priorité systématique INTERNAL).
     */
    public List<ScoredListing> dedupe(List<ScoredListing> ranked) {
        List<ScoredListing> out = new ArrayList<>();
        for (ScoredListing cand : ranked) {
            boolean dup = false;
            for (int i = 0; i < out.size(); i++) {
                ScoredListing keep = out.get(i);
                if (sameDedupeKey(keep.listing(), cand.listing())
                        || lexicon.nearDuplicate(
                        keep.listing().getTitle(), keep.listing().getCompanyName(),
                        cand.listing().getTitle(), cand.listing().getCompanyName())) {
                    dup = true;
                    if (cand.relevance() > keep.relevance()) {
                        out.set(i, cand);
                    }
                    break;
                }
            }
            if (!dup) out.add(cand);
        }
        out.sort(Comparator
                .comparingDouble(ScoredListing::relevance).reversed()
                .thenComparing((ScoredListing s) -> s.listing().getSortAt(),
                        Comparator.nullsLast(Comparator.reverseOrder())));
        return out;
    }

    private static boolean sameDedupeKey(CatalogListing a, CatalogListing b) {
        String ka = a.getDedupeKey();
        String kb = b.getDedupeKey();
        return ka != null && !ka.isBlank() && ka.equals(kb);
    }

    public Map<String, Object> provenance(CatalogListing row, ExternalSourceCatalog sources) {
        Map<String, Object> p = new LinkedHashMap<>();
        boolean external = CatalogListing.ORIGIN_EXTERNAL.equals(row.getOrigin());
        p.put("kind", external ? "EXTERNAL" : "INTERNAL");
        p.put("label", external ? sources.labelOf(row.getSourceCode()) : "HireLink");
        p.put("openMode", external ? "ORIGIN_URL" : "HIRELINK");
        if (external) {
            String url = row.getApplyUrl() != null && !row.getApplyUrl().isBlank()
                    ? row.getApplyUrl() : row.getOriginUrl();
            p.put("url", url);
        } else {
            p.put("url", "/app/candidate.html?offer=" + row.getPublicId());
        }
        return p;
    }

    public Map<String, Object> provenanceWeb(WebJobDiscovery.DiscoveredJob job) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("kind", "EXTERNAL");
        p.put("label", job.sourceLabel() == null ? job.sourceCode() : job.sourceLabel());
        p.put("openMode", "ORIGIN_URL");
        String url = job.applyUrl() != null && !job.applyUrl().isBlank() ? job.applyUrl() : job.originUrl();
        p.put("url", url);
        p.put("ephemeral", true);
        return p;
    }

    private static String nullToEmpty(String v) {
        return v == null ? "" : v;
    }

    private static double clamp(double v) {
        if (v < 0) return 0;
        if (v > 1) return 1;
        return v;
    }
}
