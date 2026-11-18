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
 * <p>
 * Mots-clés forts : chaque token significatif de la requête doit matcher
 * (synonymes autorisés <em>par token</em>), sinon score 0. Empêche
 * « stage pfe » de remonter un « Software Intern » via le seul synonyme stage→intern.
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

    /** Score 0..1 d'une ligne catalogue contre la requête (AND des tokens + synonymes locaux). */
    public double scoreListing(String query, CatalogListing row) {
        if (query == null || query.isBlank() || row == null) return 0;
        PreparedQuery preparedQuery = PreparedQuery.of(query, lexicon);
        if (preparedQuery.tokens.isEmpty()) return 0;
        return scorePrepared(preparedQuery, PreparedListing.of(row));
    }

    /** Meilleur score d'un token et de ses synonymes (déjà repliés) contre une annonce préparée. */
    private double bestAgainstToken(List<TextMatcher.FoldedText> variants, PreparedListing row) {
        // Même flux que l'ancien code : le token est évalué d'abord ; le seuil 0,98
        // n'interrompt la boucle qu'à partir des synonymes (expand).
        double best = scoreAgainst(variants.get(0), row);
        for (int i = 1; i < variants.size(); i++) {
            best = Math.max(best, scoreAgainst(variants.get(i), row));
            if (best >= 0.98) return best;
        }
        return best;
    }

    private double scoreAgainst(TextMatcher.FoldedText variant, PreparedListing row) {
        double title = matcher.scorePrepared(variant, row.title) * 1.0;
        double company = matcher.scorePrepared(variant, row.company) * 0.55;
        double skills = matcher.scorePrepared(variant, row.skills) * 0.75;
        double place = matcher.scorePrepared(variant, row.place) * 0.45;
        double contract = matcher.scorePrepared(variant, row.contract) * 0.35;
        double desc = matcher.scorePrepared(variant, row.description) * 0.25;
        double blob = matcher.scorePrepared(variant, row.searchBlob) * 0.4;
        return clamp(Math.max(title, Math.max(company, Math.max(skills, Math.max(place, Math.max(contract, Math.max(desc, blob)))))));
    }

    private double scorePrepared(PreparedQuery query, PreparedListing row) {
        double sum = 0;
        for (List<TextMatcher.FoldedText> variants : query.variantsByToken) {
            double best = bestAgainstToken(variants, row);
            if (best < MIN_SCORE) return 0;
            sum += best;
        }
        double andScore = sum / query.tokens.size();
        // Bonus phrase originale (sans explosion lexicale multi-token).
        double phrase = scoreAgainst(query.phrase, row);
        if (phrase > andScore) {
            return clamp(Math.min(1.0, andScore + 0.12 * (phrase - andScore)));
        }
        return clamp(andScore);
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
        return rank(query, rows, null);
    }

    /**
     * Classe les annonces par pertinence. {@code scoreMemo} (optionnel, limité à la requête courante)
     * réutilise un score déjà calculé pour le même identifiant, et y enregistre les nouveaux scores
     * (y compris 0) afin d'éviter un second parcours pour les compteurs de filtres.
     */
    public List<ScoredListing> rank(String query, List<CatalogListing> rows, Map<Long, Double> scoreMemo) {
        List<ScoredListing> out = new ArrayList<>();
        if (rows == null) return out;
        boolean hasQuery = query != null && !query.isBlank();
        PreparedQuery preparedQuery = null;
        if (hasQuery) {
            preparedQuery = PreparedQuery.of(query, lexicon);
        }
        for (CatalogListing row : rows) {
            Double cached = (scoreMemo != null && row != null && row.getId() != null)
                    ? scoreMemo.get(row.getId()) : null;
            double s;
            if (!hasQuery) {
                s = 1.0;
            } else if (cached != null) {
                s = cached;
            } else if (preparedQuery.tokens.isEmpty()) {
                s = 0;
            } else {
                s = scorePrepared(preparedQuery, PreparedListing.of(row));
            }
            if (hasQuery && scoreMemo != null && row != null && row.getId() != null && cached == null) {
                scoreMemo.put(row.getId(), s);
            }
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
     * Déduplication titre+entreprise+lien : conserve le meilleur score
     * (origine indifférente — pas de priorité systématique INTERNAL).
     */
    public List<ScoredListing> dedupe(List<ScoredListing> ranked) {
        List<ScoredListing> out = new ArrayList<>();
        for (ScoredListing cand : ranked) {
            boolean dup = false;
            for (int i = 0; i < out.size(); i++) {
                ScoredListing keep = out.get(i);
                if (sameListing(keep.listing(), cand.listing())) {
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

    private boolean sameListing(CatalogListing a, CatalogListing b) {
        if (a == null || b == null) return false;
        String ka = a.getDedupeKey();
        String kb = b.getDedupeKey();
        if (ka != null && !ka.isBlank() && ka.equals(kb)) return true;

        String urlA = normalizeUrl(firstUrl(a));
        String urlB = normalizeUrl(firstUrl(b));
        if (urlA.length() > 12 && urlA.equals(urlB)) return true;

        if (lexicon.nearDuplicate(a.getTitle(), a.getCompanyName(), b.getTitle(), b.getCompanyName())) {
            // Même titre+entreprise : doublon même si URLs diffèrent (agrégateurs).
            return true;
        }
        // Clé composite title|company|link quand le lien est connu des deux côtés.
        if (urlA.length() > 8 && urlB.length() > 8) {
            String ta = TextMatcher.compact(a.getTitle());
            String tb = TextMatcher.compact(b.getTitle());
            String ca = TextMatcher.compact(a.getCompanyName());
            String cb = TextMatcher.compact(b.getCompanyName());
            if (!ta.isEmpty() && ta.equals(tb) && !ca.isEmpty() && ca.equals(cb)) return true;
        }
        return false;
    }

    private static String firstUrl(CatalogListing row) {
        if (row.getApplyUrl() != null && !row.getApplyUrl().isBlank()) return row.getApplyUrl();
        return row.getOriginUrl() == null ? "" : row.getOriginUrl();
    }

    private static String normalizeUrl(String url) {
        if (url == null) return "";
        String u = url.trim().toLowerCase(Locale.ROOT);
        int q = u.indexOf('?');
        if (q > 0) u = u.substring(0, q);
        int h = u.indexOf('#');
        if (h > 0) u = u.substring(0, h);
        if (u.endsWith("/")) u = u.substring(0, u.length() - 1);
        return u;
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

    /**
     * Requête préparée une fois par appel de classement : tokens, synonymes et phrase,
     * chacun déjà replié.
     */
    private static final class PreparedQuery {
        final List<String> tokens;
        final List<List<TextMatcher.FoldedText>> variantsByToken;
        final TextMatcher.FoldedText phrase;

        private PreparedQuery(List<String> tokens, List<List<TextMatcher.FoldedText>> variantsByToken,
                              TextMatcher.FoldedText phrase) {
            this.tokens = tokens;
            this.variantsByToken = variantsByToken;
            this.phrase = phrase;
        }

        static PreparedQuery of(String query, JobSearchLexicon lexicon) {
            List<String> tokens = lexicon.significantTokens(query);
            List<List<TextMatcher.FoldedText>> variantsByToken = new ArrayList<>(tokens.size());
            for (String token : tokens) {
                List<TextMatcher.FoldedText> variants = new ArrayList<>();
                // Même ordre que l'ancien bestAgainstToken : le token d'abord, puis lexicon.expand.
                variants.add(TextMatcher.FoldedText.ofRaw(token));
                for (String v : lexicon.expand(token)) {
                    if (v == null || v.isBlank()) continue;
                    variants.add(TextMatcher.FoldedText.ofRaw(v));
                }
                variantsByToken.add(variants);
            }
            // L'ancien code passait fold(query) à score(), qui repliait une seconde fois.
            TextMatcher.FoldedText phrase = TextMatcher.FoldedText.ofRaw(TextMatcher.fold(query));
            return new PreparedQuery(tokens, variantsByToken, phrase);
        }
    }

    /** Champs d'une annonce repliés une seule fois, réutilisés pour tous les tokens et synonymes. */
    private static final class PreparedListing {
        final TextMatcher.FoldedText title;
        final TextMatcher.FoldedText company;
        final TextMatcher.FoldedText skills;
        final TextMatcher.FoldedText place;
        final TextMatcher.FoldedText contract;
        final TextMatcher.FoldedText description;
        final TextMatcher.FoldedText searchBlob;

        private PreparedListing(TextMatcher.FoldedText title, TextMatcher.FoldedText company,
                                TextMatcher.FoldedText skills, TextMatcher.FoldedText place,
                                TextMatcher.FoldedText contract, TextMatcher.FoldedText description,
                                TextMatcher.FoldedText searchBlob) {
            this.title = title;
            this.company = company;
            this.skills = skills;
            this.place = place;
            this.contract = contract;
            this.description = description;
            this.searchBlob = searchBlob;
        }

        static PreparedListing of(CatalogListing row) {
            return new PreparedListing(
                    TextMatcher.FoldedText.ofRaw(nullToEmpty(row.getTitle())),
                    TextMatcher.FoldedText.ofRaw(nullToEmpty(row.getCompanyName())),
                    TextMatcher.FoldedText.ofRaw(nullToEmpty(row.getSkillsText())),
                    TextMatcher.FoldedText.ofRaw(nullToEmpty(row.getCity()) + " " + nullToEmpty(row.getLocationLabel())
                            + " " + nullToEmpty(row.getCountryCode())),
                    TextMatcher.FoldedText.ofRaw(nullToEmpty(row.getContractLabel()) + " " + nullToEmpty(row.getWorkMode())),
                    TextMatcher.FoldedText.ofRaw(nullToEmpty(row.getDescription())),
                    TextMatcher.FoldedText.ofRaw(nullToEmpty(row.getSearchBlob()))
            );
        }
    }
}
