package ma.ensat.hirelink.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Référentiel de compétences (classpath : {@code skills-catalog.json}) : catégories, synonymes (JS = JavaScript,
 * Spring = Spring Boot…), recherche tolérante aux fautes, normalisation anti-doublons et classement par catégorie.
 * Le référentiel n'est jamais une liste fermée : toute compétence saisie librement est acceptée (catégorie « Autres »).
 */
@Service
public class SkillCatalogService {

    public static final String OTHER = "OTHER";
    private static final int MAX_NAME = 60;
    private static final Pattern LEVEL = Pattern.compile("^(.*?)\\s*\\(([^()]{1,20})\\)\\s*$");

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Category(String code, String label) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Skill(String name, String category, List<String> aliases) {
        public Skill {
            aliases = aliases == null ? List.of() : aliases;
        }
    }

    /** {@code exact} : égalité, préfixe ou sous-chaîne ; sinon proposition « proche » (faute de frappe). */
    public record Suggestion(String name, String category, String categoryLabel, boolean exact, String alias) {}

    public record Item(String name, String level) {}

    public record Group(String code, String label, List<Item> skills) {}

    private record Catalog(List<Category> categories, List<Skill> skills) {}

    private final TextMatcher matcher;
    private final Map<String, String> categoryLabels = new LinkedHashMap<>();
    private final List<Skill> skills;
    private final Map<String, Skill> byKey = new HashMap<>();

    public SkillCatalogService(TextMatcher matcher) {
        this.matcher = matcher;
        try (InputStream in = SkillCatalogService.class.getResourceAsStream("/skills-catalog.json")) {
            Catalog catalog = new ObjectMapper().readValue(in, Catalog.class);
            catalog.categories().forEach(c -> categoryLabels.put(c.code(), c.label()));
            this.skills = catalog.skills();
        } catch (IOException e) {
            throw new IllegalStateException("Référentiel de compétences illisible", e);
        }
        for (Skill s : skills) {
            byKey.put(TextMatcher.compact(s.name()), s);
            s.aliases().forEach(a -> byKey.putIfAbsent(TextMatcher.compact(a), s));
        }
    }

    public List<Category> categories() {
        return categoryLabels.entrySet().stream().map(e -> new Category(e.getKey(), e.getValue())).toList();
    }

    /** Propositions classées : correspondances directes d'abord, puis les plus proches (fautes de frappe). */
    public List<Suggestion> search(String query, int limit) {
        String q = TextMatcher.compact(query);
        if (q.isEmpty()) return List.of();
        record Hit(Skill skill, double score, String alias) {}
        List<Hit> hits = new ArrayList<>();
        for (Skill s : skills) {
            double best = matcher.score(q, s.name());
            String via = null;
            for (String alias : s.aliases()) {
                double a = matcher.score(q, alias) * 0.95;
                if (a > best) { best = a; via = alias; }
            }
            if (best > 0 && (q.length() > 1 || best >= 0.75)) hits.add(new Hit(s, best, via));
        }
        hits.sort(Comparator.comparingDouble(Hit::score).reversed().thenComparing(h -> h.skill().name().length()));
        return hits.stream().limit(limit)
                .map(h -> new Suggestion(h.skill().name(), h.skill().category(), label(h.skill().category()),
                        h.score() >= TextMatcher.DIRECT, h.alias()))
                .toList();
    }

    /** Compétence du référentiel : nom ou synonyme exact, sinon faute de frappe unique (noms de 5 lettres et plus). */
    public Optional<Skill> find(String raw) {
        String key = TextMatcher.compact(parse(raw).name());
        if (key.isEmpty()) return Optional.empty();
        Skill exact = byKey.get(key);
        if (exact != null) return Optional.of(exact);
        if (key.length() < 5) return Optional.empty();
        Skill closest = null;
        int bestDistance = 2;
        for (Map.Entry<String, Skill> e : byKey.entrySet()) {
            if (e.getKey().length() < 5 || e.getKey().charAt(0) != key.charAt(0)) continue;
            int d = TextMatcher.distance(key, e.getKey(), 1);
            if (d < bestDistance) { bestDistance = d; closest = e.getValue(); }
        }
        return Optional.ofNullable(closest);
    }

    /** Clé de comparaison : deux écritures d'une même compétence donnent la même clé (« js » = « JavaScript »). */
    public String key(String raw) {
        return find(raw).map(s -> TextMatcher.compact(s.name())).orElseGet(() -> TextMatcher.compact(parse(raw).name()));
    }

    /** Nom canonique si la compétence est connue, sinon saisie nettoyée ; le niveau « (B2) » est conservé. */
    public String normalise(String raw) {
        Item parsed = parse(raw);
        String name = find(raw).map(Skill::name).orElseGet(() -> clean(parsed.name()));
        if (name.isEmpty()) return "";
        return parsed.level() == null ? name : name + " (" + parsed.level() + ")";
    }

    /** Liste « a, b, c » normalisée et sans doublon ; {@code null} reste {@code null}. */
    public String normaliseList(String csv, int maxLength) {
        if (csv == null) return null;
        Map<String, String> unique = new LinkedHashMap<>();
        for (String raw : csv.split("[,;]")) {
            String name = normalise(raw);
            if (!name.isEmpty()) unique.putIfAbsent(key(raw), name);
        }
        String joined = String.join(", ", unique.values());
        if (joined.length() > maxLength) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Trop de compétences : retirez-en quelques-unes.");
        }
        return joined;
    }

    /** Classe une liste de compétences par catégorie (ordre du référentiel, « Autres » en dernier). */
    public List<Group> classify(String csv) {
        Map<String, List<Item>> byCategory = new LinkedHashMap<>();
        categoryLabels.keySet().forEach(code -> byCategory.put(code, new ArrayList<>()));
        Set<String> seen = new LinkedHashSet<>();
        if (csv != null) {
            for (String raw : csv.split("[,;]")) {
                Item parsed = parse(raw);
                if (parsed.name().isBlank() || !seen.add(key(raw))) continue;
                Optional<Skill> known = find(raw);
                String category = known.map(Skill::category).orElse(OTHER);
                String name = known.map(Skill::name).orElseGet(() -> clean(parsed.name()));
                byCategory.get(category).add(new Item(name, parsed.level()));
            }
        }
        List<Group> groups = new ArrayList<>();
        byCategory.forEach((code, items) -> {
            if (!items.isEmpty()) groups.add(new Group(code, label(code), items));
        });
        return groups;
    }

    private String label(String code) {
        return categoryLabels.getOrDefault(code, categoryLabels.get(OTHER));
    }

    /** « Anglais (B2) » → nom « Anglais », niveau « B2 ». */
    private static Item parse(String raw) {
        String s = raw == null ? "" : raw.trim();
        Matcher m = LEVEL.matcher(s);
        return m.matches() ? new Item(m.group(1).trim(), m.group(2).trim()) : new Item(s, null);
    }

    private static String clean(String name) {
        String s = name.replaceAll("[\\p{Cntrl}<>]", "").replaceAll("\\s+", " ").trim();
        if (s.length() > MAX_NAME) s = s.substring(0, MAX_NAME).trim();
        return s.isEmpty() || !Character.isLowerCase(s.charAt(0)) ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
