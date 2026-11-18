package ma.ensat.hirelink.service;

import ma.ensat.hirelink.model.CatalogListing;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.text.Normalizer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Vérifie que l'accélération du repli de texte et du classement
 * produit exactement les mêmes scores et le même ordre que l'algorithme d'origine.
 */
class CatalogSmartRankerEquivalenceTest {

    private final TextMatcher matcher = new TextMatcher();
    private CatalogSmartRanker ranker;
    private AncienRanker ancien;

    @BeforeEach
    void setUp() {
        JobSearchLexicon lexicon = new JobSearchLexicon();
        ranker = new CatalogSmartRanker(matcher, lexicon);
        ancien = new AncienRanker(new AncienTextMatcher(), lexicon);
    }

    @Test
    void scoresStrictementEgauxSurJeuxVariés() {
        String[] queries = {
                "développeur",
                "Developpeur",
                "développeur java",
                "C++",
                "c++",
                "C#",
                "Node.js",
                "node js",
                "nodejs",
                "javscript",
                "developpeur javva",
                "stage pfe",
                "full stack",
                "fullstack",
                "analyste données SQL",
                "Tétouan",
                "ingénieur",
                "xyz",
                "jvv",
                "  ",
                "Spring Boot",
                "jav",
                "boot",
                "ring"
        };
        String[] texts = {
                "Développeur Full-Stack Java",
                "Developpeur Java",
                "C++ / C# Engineer",
                "C++",
                "C#",
                "Node.js backend",
                "nodejs",
                "node js",
                "JavaScript",
                "Stage PFE développeur",
                "Software Intern remote",
                "Fullstack Engineer",
                "full stack",
                "Data Analyst SQL Python",
                "Tétouan · Hybride",
                "Ingénieur DevOps junior — CI/CD & staging",
                "Spring Boot",
                "Java",
                "",
                null,
                "Chargé de marketing digital",
                "UX Designer Product Figma"
        };
        for (String q : queries) {
            for (String t : texts) {
                double expected = new AncienTextMatcher().score(q, t);
                double actual = matcher.score(q, t);
                assertEquals(expected, actual, 0.0, "score(\"" + q + "\", \"" + t + "\")");
                TextMatcher.FoldedText pq = TextMatcher.FoldedText.ofRaw(q);
                TextMatcher.FoldedText pt = TextMatcher.FoldedText.ofRaw(t);
                assertEquals(expected, matcher.scorePrepared(pq, pt), 0.0,
                        "scorePrepared(\"" + q + "\", \"" + t + "\")");
            }
        }
    }

    @Test
    void foldIdentiqueALancienYComprisAccentsEtLangages() {
        String[] samples = {
                null, "", "  ", "  Tétouan · Hybride ", "Node.js", "C++", "C#",
                "Développeur", "ingénieur", "full-stack", "CI/CD", "PFE"
        };
        for (String s : samples) {
            assertEquals(AncienTextMatcher.fold(s), TextMatcher.fold(s), "fold(" + s + ")");
            assertEquals(AncienTextMatcher.compact(s), TextMatcher.compact(s), "compact(" + s + ")");
        }
    }

    @Test
    void scoreListingEtOrdreDeRankIdentiques() {
        List<CatalogListing> rows = List.of(
                listing(1, "Développeur Full-Stack Java — produit ATS", "Atlas Digital",
                        "Java,Spring,React", "Tétouan", "CDI", "HYBRID",
                        "Recherche un développeur Java Spring Boot, Node.js et C++."),
                listing(2, "Chargé de marketing digital", "Zellige",
                        "SEO,Ads", "Casablanca", "CDD", "ONSITE",
                        "Campagnes SEO et publicité."),
                listing(3, "Data Analyst", "OCP Group",
                        "SQL,Python,Power BI", "Rabat", "CDI", "REMOTE",
                        "Analyse de données SQL et Python."),
                listing(4, "Chargé de recrutement", "HireLink Demo",
                        "RH,Talent", "Tanger", "STAGE", "HYBRID",
                        "Stage PFE recrutement."),
                listing(5, "UX Designer Product Figma", "Remote Co",
                        "Figma,UI,UX", "Paris", "CDI", "REMOTE",
                        "Product designer Figma."),
                listing(6, "Software Intern", "Acme Remote",
                        "Python,Git", "Remote", "STAGE", "REMOTE",
                        "Software intern, no PFE mention."),
                listing(7, "Stage PFE Développeur Java", "Atlas Digital",
                        "Java,Spring,PFE", "Tétouan", "STAGE", "HYBRID",
                        "Stage PFE développeur Java C#."),
                listing(8, "Ingénieur DevOps junior — CI/CD & staging", "Nour Systems",
                        "Docker,Kubernetes,C++", "Rabat", "CDI", "HYBRID",
                        "CI/CD, Node.js, C++ et scripts."),
                listing(9, "Développeur .NET", "Casa Tech",
                        "C#, .NET, Azure", "Casablanca", "CDI", "HYBRID",
                        "Développeur C# / .NET, fautes de frappe bienvenues."),
                listing(10, "Node.js backend engineer", "Atlas",
                        "Node.js,TypeScript", "Tétouan", "CDI", "REMOTE",
                        "nodejs / node js microservices.")
        );

        String[] queries = {
                "développeur",
                "developpeur javva",
                "analyste données SQL",
                "UX Designer Product Figma",
                "developpeur java spring",
                "stage pfe",
                "stage",
                "C++",
                "C#",
                "Node.js",
                "nodejs",
                "javscript",
                "ingénieur devops",
                "full stack"
        };

        for (String q : queries) {
            for (CatalogListing row : rows) {
                assertEquals(ancien.scoreListing(q, row), ranker.scoreListing(q, row), 0.0,
                        "scoreListing(\"" + q + "\", id=" + row.getId() + ")");
            }
            List<CatalogSmartRanker.ScoredListing> actual = ranker.rank(q, rows);
            List<AncienRanker.ScoredListing> expected = ancien.rank(q, rows);
            assertEquals(expected.size(), actual.size(), "taille rank(\"" + q + "\")");
            long[] expectedIds = expected.stream().mapToLong(s -> s.listing().getId()).toArray();
            long[] actualIds = actual.stream().mapToLong(s -> s.listing().getId()).toArray();
            assertArrayEquals(expectedIds, actualIds, "ordre rank(\"" + q + "\")");
            for (int i = 0; i < expected.size(); i++) {
                assertEquals(expected.get(i).relevance(), actual.get(i).relevance(), 0.0,
                        "relevance rank(\"" + q + "\")[" + i + "]");
            }
        }
    }

    @Test
    void memoNeChangeNiLesScoresNiLOrdre() {
        List<CatalogListing> rows = List.of(
                listing(1, "Développeur Full-Stack Java — produit ATS", "Atlas Digital",
                        "Java,Spring,React", "Tétouan", "CDI", "HYBRID",
                        "Java Spring, description longue répétée. ".repeat(40)),
                listing(2, "Développeur .NET", "Casa Tech",
                        "C#", "Casablanca", "CDI", "HYBRID", "C# .NET"),
                listing(3, "Data Analyst", "OCP Group",
                        "SQL,Python", "Rabat", "CDI", "REMOTE", "SQL")
        );
        String q = "développeur java";
        List<CatalogSmartRanker.ScoredListing> first = ranker.rank(q, rows);
        Map<Long, Double> memo = new java.util.HashMap<>();
        for (CatalogSmartRanker.ScoredListing s : first) {
            memo.put(s.listing().getId(), s.relevance());
        }
        // Annonce déjà vue + une nouvelle : les scores mémorisés doivent être repris tels quels.
        CatalogListing extra = listing(4, "Ingénieur DevOps", "Nour", "Docker", "Rabat", "CDI", "HYBRID", "CI/CD");
        List<CatalogListing> wider = List.of(rows.get(2), rows.get(0), extra, rows.get(1));
        List<CatalogSmartRanker.ScoredListing> second = ranker.rank(q, wider, memo);
        List<CatalogSmartRanker.ScoredListing> fresh = ranker.rank(q, wider);
        assertEquals(fresh.size(), second.size());
        for (int i = 0; i < fresh.size(); i++) {
            assertEquals(fresh.get(i).listing().getId(), second.get(i).listing().getId());
            assertEquals(fresh.get(i).relevance(), second.get(i).relevance(), 0.0);
        }
        assertEquals(ancien.rank(q, wider).size(), second.size());
        long[] expectedIds = ancien.rank(q, wider).stream().mapToLong(s -> s.listing().getId()).toArray();
        long[] actualIds = second.stream().mapToLong(s -> s.listing().getId()).toArray();
        assertArrayEquals(expectedIds, actualIds);
    }

    private static CatalogListing listing(long id, String title, String company, String skills,
                                         String city, String contract, String workMode, String description) {
        CatalogListing row = new CatalogListing();
        row.setId(id);
        row.setOrigin(CatalogListing.ORIGIN_INTERNAL);
        row.setRefId(id);
        row.setPublicId(String.valueOf(id));
        row.setSourceCode("HIRELINK");
        row.setTitle(title);
        row.setCompanyName(company);
        row.setSkillsText(skills);
        row.setCity(city);
        row.setLocationLabel(city);
        row.setCountryCode("MA");
        row.setContractLabel(contract);
        row.setWorkMode(workMode);
        row.setDescription(description);
        row.setSearchBlob(title + " " + company + " " + skills + " " + city + " " + description);
        row.setLinkReliable(true);
        row.setPublishedTrusted(true);
        row.setSortAt(Instant.parse("2026-11-01T10:00:00Z").plusSeconds(id));
        return row;
    }

    /** Copie privée de l'ancien TextMatcher (repli par replaceAll recompilé à chaque appel). */
    static final class AncienTextMatcher {
        static String fold(String value) {
            if (value == null) return "";
            String s = Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
            return s.replaceAll("[^\\p{L}\\p{N}+#]+", " ").trim();
        }

        static String compact(String value) {
            return fold(value).replace(" ", "");
        }

        double score(String query, String text) {
            String t = compact(text);
            double whole = single(compact(query), t, text);
            String[] words = fold(query).split(" ");
            if (words.length < 2) return whole;
            double sum = 0;
            int counted = 0;
            for (String w : words) {
                if (w.isBlank() || w.length() < 2) continue;
                double s = single(w.replace(" ", ""), t, text);
                if (s == 0) {
                    return whole;
                }
                sum += s;
                counted++;
            }
            if (counted == 0) return whole;
            return Math.max(whole, 0.95 * sum / counted);
        }

        private static double single(String q, String t, String text) {
            if (q.isEmpty() || t.isEmpty()) return 0;
            if (q.equals(t)) return 1;
            if (t.startsWith(q)) return 0.85 + 0.05 * q.length() / t.length();
            String[] words = fold(text).split(" ");
            for (String word : words) {
                if (word.startsWith(q)) return 0.75;
            }
            if (q.length() >= 2 && t.contains(q)) return 0.6;
            int tolerance = TextMatcher.tolerance(q.length());
            if (tolerance == 0) return 0;
            int best = TextMatcher.distance(q, t, tolerance);
            for (int len = q.length() - 1; len <= q.length() + 1 && len <= t.length(); len++) {
                best = Math.min(best, TextMatcher.distance(q, t.substring(0, len), tolerance));
            }
            for (String word : words) {
                best = Math.min(best, TextMatcher.distance(q, word, tolerance));
            }
            return best <= tolerance ? 0.5 - 0.1 * best : 0;
        }
    }

    /** Copie privée de l'ancien classement (repli de chaque champ à chaque token / synonyme). */
    static final class AncienRanker {
        record ScoredListing(CatalogListing listing, double relevance) {}

        private final AncienTextMatcher matcher;
        private final JobSearchLexicon lexicon;

        AncienRanker(AncienTextMatcher matcher, JobSearchLexicon lexicon) {
            this.matcher = matcher;
            this.lexicon = lexicon;
        }

        double scoreListing(String query, CatalogListing row) {
            if (query == null || query.isBlank() || row == null) return 0;
            List<String> tokens = lexicon.significantTokens(query);
            if (tokens.isEmpty()) return 0;
            double sum = 0;
            for (String token : tokens) {
                double best = bestAgainstToken(token, row);
                if (best < CatalogSmartRanker.MIN_SCORE) return 0;
                sum += best;
            }
            double andScore = sum / tokens.size();
            double phrase = scoreAgainst(AncienTextMatcher.fold(query), row);
            if (phrase > andScore) {
                return clamp(Math.min(1.0, andScore + 0.12 * (phrase - andScore)));
            }
            return clamp(andScore);
        }

        private double bestAgainstToken(String token, CatalogListing row) {
            double best = scoreAgainst(token, row);
            for (String v : lexicon.expand(token)) {
                if (v == null || v.isBlank()) continue;
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

        List<ScoredListing> rank(String query, List<CatalogListing> rows) {
            List<ScoredListing> out = new ArrayList<>();
            if (rows == null) return out;
            boolean hasQuery = query != null && !query.isBlank();
            for (CatalogListing row : rows) {
                double s = hasQuery ? scoreListing(query, row) : 1.0;
                if (!hasQuery || s >= CatalogSmartRanker.MIN_SCORE) {
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

        private static String nullToEmpty(String v) {
            return v == null ? "" : v;
        }

        private static double clamp(double v) {
            if (v < 0) return 0;
            if (v > 1) return 1;
            return v;
        }
    }
}
