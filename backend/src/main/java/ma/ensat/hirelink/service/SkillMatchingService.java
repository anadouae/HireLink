package ma.ensat.hirelink.service;

import ma.ensat.hirelink.model.ContractType;
import ma.ensat.hirelink.model.ExperienceLevel;
import ma.ensat.hirelink.model.JobOffer;
import ma.ensat.hirelink.model.User;
import ma.ensat.hirelink.model.WorkMode;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Appariement local profil ↔ offre (F14).
 * <p>{@link #explain} : part des compétences demandées que le candidat possède (critère « compétences »).
 * <p>{@link #evaluate} : score de compatibilité pondéré et explicable, critère par critère — voir
 * {@code docs/SCORE_COMPATIBILITE.md}. Le score sert uniquement à trier ; il ne déclenche aucune décision.
 * <p>
 * Les écritures différentes d'une même compétence sont rapprochées par le référentiel (JS = JavaScript,
 * Spring = Spring Boot, fautes de frappe légères) ; le niveau d'une langue n'intervient pas.
 * Aucune API tierce — ne consomme aucun crédit IA (R08).
 */
@Service
public class SkillMatchingService {

    public record MatchDetail(double score, List<String> covered, List<String> missing) {}

    private final SkillCatalogService catalog;

    public SkillMatchingService(SkillCatalogService catalog) {
        this.catalog = catalog;
    }

    /** @return score entre 0.0 et 100.0 */
    public double computeMatchScore(String candidateSkillsCsv, String requiredSkillsCsv) {
        return explain(candidateSkillsCsv, requiredSkillsCsv).score();
    }

    /** Détail vérifiable pour matching_analyses (F14) : compétences couvertes et manquantes, noms normalisés. */
    public MatchDetail explain(String candidateSkillsCsv, String requiredSkillsCsv) {
        Set<String> candidate = new HashSet<>();
        for (String raw : split(candidateSkillsCsv)) candidate.add(catalog.key(raw));
        List<String> covered = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String raw : split(requiredSkillsCsv)) {
            if (!seen.add(catalog.key(raw))) continue;
            String name = catalog.normalise(raw);
            (candidate.contains(catalog.key(raw)) ? covered : missing).add(name);
        }
        int total = covered.size() + missing.size();
        double score = total == 0 ? 0.0 : Math.round(10000.0 * covered.size() / total) / 100.0;
        return new MatchDetail(score, covered, missing);
    }


    // —— Score de compatibilité pondéré ——

    /** Poids par défaut (total 100). Un critère que l'offre ne précise pas est retiré et les autres sont renormalisés. */
    public static final int W_REQUIRED = 45, W_NICE = 10, W_LANGUAGES = 10, W_EXPERIENCE = 15, W_CONTRACT = 10, W_LOCATION = 10;
    /**
     * Garde-fou : si moins de la moitié des compétences obligatoires sont couvertes, le total est plafonné à
     * min(60, couverture % + 20) — les critères pratiques (lieu, contrat…) ne compensent pas l'absence des compétences.
     */
    public static final double REQUIRED_FLOOR = 0.5, CAP_WHEN_BELOW_FLOOR = 60.0, CAP_MARGIN = 20.0;
    /** Valeur neutre quand l'information manque dans le profil candidat (le profil incomplet n'est ni puni ni favorisé). */
    public static final double UNKNOWN = 0.5;

    /** Un critère : {@code ratio} entre 0 et 1, {@code points} = contribution au total après renormalisation. */
    public record Criterion(String code, String label, int weight, boolean applicable, Double ratio, double points,
                            String detail, List<String> matched, List<String> missing) {}

    public record Compatibility(double score, boolean capped, List<Criterion> criteria,
                                List<String> covered, List<String> missing) {}

    public Compatibility evaluate(User candidate, JobOffer offer) {
        String candSkills = candidate == null ? null : candidate.getSkills();
        List<String> reqAll = split(offer.getRequiredSkills());
        List<String> niceAll = split(offer.getNiceToHaveSkills());
        List<String> req = reqAll.stream().filter(r -> !catalog.isLanguage(r)).toList();
        List<String> nice = niceAll.stream().filter(r -> !catalog.isLanguage(r)).toList();
        List<String> langs = new ArrayList<>();
        reqAll.stream().filter(catalog::isLanguage).forEach(langs::add);
        niceAll.stream().filter(catalog::isLanguage).forEach(langs::add);

        List<Criterion> raw = new ArrayList<>();
        MatchDetail r = explain(candSkills, String.join(",", req));
        raw.add(skills("REQUIRED", "Compétences obligatoires", W_REQUIRED, req, r));
        MatchDetail n = explain(candSkills, String.join(",", nice));
        raw.add(skills("NICE", "Compétences appréciées", W_NICE, nice, n));
        raw.add(languages(candSkills, langs));
        raw.add(experience(candidate == null ? null : candidate.getExperienceLevel(), offer.getExperienceLevel()));
        raw.add(contract(candidate == null ? null : candidate.getPreferredContracts(), offer.getContractType()));
        raw.add(location(candidate == null ? null : candidate.getPreferredLocations(), offer));

        int totalWeight = raw.stream().filter(Criterion::applicable).mapToInt(Criterion::weight).sum();
        List<Criterion> out = new ArrayList<>();
        double sum = 0;
        for (Criterion c : raw) {
            double pts = c.applicable() && totalWeight > 0 ? 100.0 * c.weight() * c.ratio() / totalWeight : 0;
            pts = Math.round(pts * 10) / 10.0;
            sum += c.applicable() && totalWeight > 0 ? 100.0 * c.weight() * c.ratio() / totalWeight : 0;
            out.add(new Criterion(c.code(), c.label(), c.weight(), c.applicable(), c.ratio(), pts, c.detail(), c.matched(), c.missing()));
        }
        boolean capped = false;
        double cap = Math.min(CAP_WHEN_BELOW_FLOOR, r.score() + CAP_MARGIN);
        if (!req.isEmpty() && r.score() < REQUIRED_FLOOR * 100 && sum > cap) {
            sum = cap;
            capped = true;
        }
        double score = totalWeight == 0 ? 0 : Math.round(sum * 10) / 10.0;
        List<String> covered = new ArrayList<>(r.covered());
        List<String> missing = new ArrayList<>(r.missing());
        return new Compatibility(score, capped, out, covered, missing);
    }

    private static Criterion skills(String code, String label, int w, List<String> wanted, MatchDetail d) {
        if (wanted.isEmpty()) return new Criterion(code, label, w, false, null, 0, "Non précisé par l'offre", List.of(), List.of());
        double ratio = d.score() / 100.0;
        return new Criterion(code, label, w, true, ratio, 0,
                d.covered().size() + " sur " + (d.covered().size() + d.missing().size()), d.covered(), d.missing());
    }

    private static final List<String> CEFR = List.of("A1", "A2", "B1", "B2", "C1", "C2", "NATIF");

    private static int cefr(String level) {
        if (level == null) return -1;
        String l = level.trim().toUpperCase(Locale.ROOT);
        if (l.startsWith("NATI") || l.startsWith("BILING") || l.startsWith("MATERN")) return 6;
        return CEFR.indexOf(l);
    }

    private Criterion languages(String candSkills, List<String> wanted) {
        String label = "Langues";
        if (wanted.isEmpty()) return new Criterion("LANGUAGES", label, W_LANGUAGES, false, null, 0, "Non précisé par l'offre", List.of(), List.of());
        Map<String, String> mine = new HashMap<>();
        for (String s : split(candSkills)) if (catalog.isLanguage(s)) mine.put(catalog.key(s), catalog.level(s));
        double sum = 0;
        List<String> ok = new ArrayList<>(), ko = new ArrayList<>();
        for (String w : wanted) {
            String name = catalog.normalise(w);
            String key = catalog.key(w);
            if (!mine.containsKey(key)) { ko.add(name); continue; }
            int need = cefr(catalog.level(w)), have = cefr(mine.get(key));
            double v = need < 0 || have >= need ? 1.0 : have < 0 ? 0.75 : have == need - 1 ? 0.5 : 0.0;
            sum += v;
            (v >= 1.0 ? ok : ko).add(v >= 1.0 ? name : name + " — niveau du profil : " + mine.get(key));
        }
        double ratio = sum / wanted.size();
        return new Criterion("LANGUAGES", label, W_LANGUAGES, true, ratio, 0, ok.size() + " sur " + wanted.size() + " au niveau demandé", ok, ko);
    }

    /** Profil : Stagiaire 0 · Junior 1 · Confirmé 2 · Senior 3 · Lead/Manager 4 ; offre : NONE 0 … EXPERT 4. */
    static int candidateRank(String level) {
        if (level == null || level.isBlank()) return -1;
        String l = TextMatcher.compact(level);
        if (l.startsWith("stagi") || l.startsWith("debut") || l.startsWith("etud")) return 0;
        if (l.startsWith("junior")) return 1;
        if (l.startsWith("confirm") || l.startsWith("inter")) return 2;
        if (l.startsWith("senior")) return 3;
        if (l.startsWith("lead") || l.startsWith("manag") || l.startsWith("expert")) return 4;
        return -1;
    }

    private static final String[] RANK_LABEL = {"Sans expérience", "Junior", "Confirmé", "Senior", "Expert / Lead"};

    private static Criterion experience(String candLevel, ExperienceLevel offerLevel) {
        String label = "Expérience";
        if (offerLevel == null) return new Criterion("EXPERIENCE", label, W_EXPERIENCE, false, null, 0, "Non précisé par l'offre", List.of(), List.of());
        int need = offerLevel.ordinal(), have = candidateRank(candLevel);
        if (have < 0) return new Criterion("EXPERIENCE", label, W_EXPERIENCE, true, UNKNOWN, 0,
                "Niveau non renseigné dans le profil (offre : " + RANK_LABEL[need] + ")", List.of(), List.of());
        int diff = have - need;
        double ratio = diff == 0 ? 1.0 : diff == 1 ? 0.9 : diff >= 2 ? 0.6 : diff == -1 ? 0.5 : 0.1;
        String text = "Profil " + RANK_LABEL[have] + " · offre " + RANK_LABEL[need]
                + (diff == 0 ? " : même niveau" : diff > 0 ? (diff >= 2 ? " : nettement surqualifié" : " : un niveau au-dessus") : (diff == -1 ? " : un niveau en dessous" : " : écart important"));
        return new Criterion("EXPERIENCE", label, W_EXPERIENCE, true, ratio, 0, text, List.of(), List.of());
    }

    private static Criterion contract(String prefs, String offerContract) {
        String label = "Type de contrat";
        if (offerContract == null || offerContract.isBlank()) return new Criterion("CONTRACT", label, W_CONTRACT, false, null, 0, "Non précisé par l'offre", List.of(), List.of());
        List<String> wanted = split(prefs);
        if (wanted.isEmpty()) return new Criterion("CONTRACT", label, W_CONTRACT, true, UNKNOWN, 0, "Aucune préférence dans le profil (offre : " + offerContract + ")", List.of(), List.of());
        String key = TextMatcher.compact(offerContract);
        boolean offerInternship = ContractType.fromLabel(offerContract).map(ContractType::isInternship).orElse(false);
        double ratio = 0;
        for (String w : wanted) {
            String k = TextMatcher.compact(w);
            if (k.equals(key)) { ratio = 1.0; break; }
            boolean wInternship = ContractType.fromLabel(w).map(ContractType::isInternship).orElse(false);
            if (offerInternship && wInternship) ratio = Math.max(ratio, 0.8);
            if (!offerInternship && k.equals("premieremploi") && (key.equals("cdi") || key.equals("cdd"))) ratio = Math.max(ratio, 0.8);
        }
        String text = ratio >= 1 ? offerContract + " fait partie des préférences du profil"
                : ratio > 0 ? offerContract + " proche des préférences du profil (" + String.join(", ", wanted) + ")"
                : offerContract + " hors des préférences du profil (" + String.join(", ", wanted) + ")";
        return new Criterion("CONTRACT", label, W_CONTRACT, true, ratio, 0, text, List.of(), List.of());
    }

    private static WorkMode modeOf(String token) {
        String k = TextMatcher.compact(token);
        if (k.startsWith("remote") || k.startsWith("teletravail") || k.startsWith("distance")) return WorkMode.REMOTE;
        if (k.startsWith("hybrid")) return WorkMode.HYBRID;
        if (k.startsWith("surplace") || k.startsWith("sursite") || k.startsWith("presentiel")) return WorkMode.ONSITE;
        return null;
    }

    private static Criterion location(String prefs, JobOffer offer) {
        String label = "Lieu et mode de travail";
        String loc = offer.getLocation() == null ? "" : offer.getLocation();
        WorkMode mode = offer.getWorkMode();
        String[] parts = loc.split("[·,/|-]");
        String city = parts.length > 0 ? parts[0].trim() : "";
        if (mode == null) for (String p : parts) { WorkMode m = modeOf(p); if (m != null) { mode = m; break; } }
        if (modeOf(city) != null) { if (mode == null) mode = modeOf(city); city = ""; }
        if (city.isBlank() && mode == null) return new Criterion("LOCATION", label, W_LOCATION, false, null, 0, "Non précisé par l'offre", List.of(), List.of());
        List<String> tokens = split(prefs);
        if (tokens.isEmpty()) return new Criterion("LOCATION", label, W_LOCATION, true, UNKNOWN, 0, "Aucune préférence de lieu dans le profil", List.of(), List.of());
        java.util.EnumSet<WorkMode> modes = java.util.EnumSet.noneOf(WorkMode.class);
        List<String> cities = new ArrayList<>();
        for (String t : tokens) { WorkMode m = modeOf(t); if (m != null) modes.add(m); else cities.add(TextMatcher.compact(t)); }
        String offerText = (city.isBlank() ? "" : city) + (mode == null ? "" : (city.isBlank() ? "" : " · ") + MODE_LABEL.get(mode));
        double ratio;
        String why;
        if (mode == WorkMode.REMOTE) {
            ratio = modes.isEmpty() || modes.contains(WorkMode.REMOTE) ? 1.0 : 0.8;
            why = ratio == 1 ? "télétravail accepté" : "télétravail, non demandé dans le profil";
        } else {
            boolean cityOk = !city.isBlank() && cities.contains(TextMatcher.compact(city));
            boolean onlyRemote = !modes.isEmpty() && modes.stream().allMatch(m -> m == WorkMode.REMOTE);
            if (onlyRemote) { ratio = 0.3; why = "le profil ne cherche que du télétravail"; }
            else if (cityOk) {
                ratio = mode == WorkMode.ONSITE && !modes.isEmpty() && !modes.contains(WorkMode.ONSITE) ? 0.8 : 1.0;
                why = ratio == 1 ? "ville dans les préférences du profil" : "ville dans les préférences du profil, mais 100 % sur site";
            } else if (cities.isEmpty() || city.isBlank()) { ratio = UNKNOWN; why = "ville non comparable"; }
            else { ratio = 0.2; why = "ville hors des préférences du profil (mobilité à prévoir)"; }
        }
        return new Criterion("LOCATION", label, W_LOCATION, true, ratio, 0, offerText + " : " + why, List.of(), List.of());
    }

    private static final Map<WorkMode, String> MODE_LABEL = Map.of(WorkMode.ONSITE, "Sur site", WorkMode.HYBRID, "Hybride", WorkMode.REMOTE, "Télétravail");

    private static List<String> split(String csv) {
        if (csv == null || csv.isBlank()) return List.of();
        return java.util.Arrays.stream(csv.split("[,;]")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }
}
