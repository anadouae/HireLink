package ma.ensat.hirelink.service;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Découpe un CV déjà lu en texte. Les stages, emplois, alternances et projets ne sont retenus
 * que lorsqu'une ligne les porte vraiment. Aucune entreprise, date ou poste n'est inventé.
 */
public final class CvSectionParser {

    public record Experience(
            String title,
            String organization,
            String kind,
            String start,
            String end,
            boolean datesIncomplete
    ) {}

    public record Parsed(Map<String, String> sections, List<Experience> experiences, String note) {}

    private static final Pattern YEAR = Pattern.compile("(19|20)\\d{2}");
    private static final Pattern MONTHS = Pattern.compile(
            "janvier|janv|fevrier|fevr|mars|avril|avr|mai|juin|juillet|juil|aout|aou|septembre|sept|octobre|oct|novembre|nov|decembre|dec|january|jan|february|feb|march|mar|april|apr|may|june|jun|july|jul|august|aug|september|sep|october|oct|november|nov|december|dec|ete|summer|winter|automne|printemps");
    private static final Pattern SPLIT = Pattern.compile("(?i)\\s+(?:chez|at)\\s+|\\s*[—–]\\s*|\\s+-\\s+|\\s*,\\s*");
    private static final Pattern STAGE = Pattern.compile("(?i)\\bstages?\\b|\\bstagiaires?\\b|\\bintern(?:ship)?s?\\b");
    private static final Pattern ALTERNANCE = Pattern.compile("(?i)\\balternances?\\b|\\bapprentissages?\\b|\\bapprentice(?:ship)?\\b|\\bapprenti(?:e|es|s)?\\b");
    private static final Pattern ONGOING = Pattern.compile("(?i)depuis|present|aujourd|current|\\bnow\\b|en cours");
    private static final Pattern LEVEL = Pattern.compile("(?i)\\b(?:A[12]|B[12]|C[12]|natif(?:ve)?|native|courant(?:e)?|fluent|beginner|intermediate|advanced)\\b");
    private static final Pattern LANGUAGE_NAME = Pattern.compile(
            "(?i)\\b(français|francais|french|anglais|english|arabe|arabic|espagnol|spanish|allemand|german|amazigh)\\b");

    private static final Map<String, String> HEADINGS = new LinkedHashMap<>();
    static {
        putAll("experience", "experience", "experiences", "experience professionnelle", "experiences professionnelles",
                "parcours professionnel", "work experience", "professional experience", "employment history", "employment");
        putAll("education", "formation", "formations", "education", "etudes", "etude", "diplomes", "diplome", "scolarite");
        putAll("skills", "competences", "competence", "skills", "technologies", "outils");
        putAll("languages", "langues", "langue", "languages", "language");
        putAll("projects", "projets academiques", "projets universitaires", "academic projects", "projets", "projet", "projects", "project");
    }

    private static final String[][] LANGUAGES = {
            {"francais", "Français"}, {"french", "Français"},
            {"anglais", "Anglais"}, {"english", "Anglais"},
            {"arabe", "Arabe"}, {"arabic", "Arabe"},
            {"espagnol", "Espagnol"}, {"spanish", "Espagnol"},
            {"allemand", "Allemand"}, {"german", "Allemand"},
            {"amazigh", "Amazigh"}
    };

    public Parsed parse(String raw, List<String> lexiconSkills) {
        String text = raw == null ? "" : raw.replace("\r\n", "\n").replace('\r', '\n');
        for (String line : text.split("\n")) {
            if (headingHits(line).size() >= 2) {
                return new Parsed(emptySections(), List.of(),
                        "Mise en page à colonnes non séparée : les rubriques n'ont pas été devinées.");
            }
        }
        Map<String, StringBuilder> blocks = new LinkedHashMap<>();
        blocks.put("identity", new StringBuilder());
        String current = "identity";
        for (String line : text.split("\n")) {
            String heading = headingKey(line);
            if (heading != null) {
                current = heading;
                blocks.computeIfAbsent(current, k -> new StringBuilder());
                continue;
            }
            blocks.computeIfAbsent(current, k -> new StringBuilder()).append(line).append('\n');
        }

        List<Experience> jobs = readEntries(blocks.getOrDefault("experience", new StringBuilder()).toString(), null);
        List<Experience> projects = readEntries(blocks.getOrDefault("projects", new StringBuilder()).toString(), "PROJET");
        List<Experience> all = new ArrayList<>();
        all.addAll(jobs);
        all.addAll(projects);

        Map<String, String> sections = emptySections();
        sections.put("identity", clip(blocks.get("identity").toString()));
        sections.put("experience", clip(summarize(jobs)));
        if (sections.get("experience").isBlank()) {
            sections.put("experience", clip(blocks.getOrDefault("experience", new StringBuilder()).toString()));
        }
        sections.put("education", clip(blocks.getOrDefault("education", new StringBuilder()).toString()));
        sections.put("projects", clip(blocks.getOrDefault("projects", new StringBuilder()).toString()));
        String skillBlock = clip(blocks.getOrDefault("skills", new StringBuilder()).toString());
        if (skillBlock.isBlank() && lexiconSkills != null && !lexiconSkills.isEmpty()) {
            skillBlock = clip(String.join(", ", lexiconSkills));
        }
        sections.put("skills", skillBlock);
        String languageBlock = blocks.getOrDefault("languages", new StringBuilder()).toString();
        String languages = languagesIn(languageBlock.isBlank() ? text : languageBlock);
        sections.put("languages", languages);
        enrichEducation(sections, blocks.getOrDefault("education", new StringBuilder()).toString());
        enrichPreferences(sections, text);
        String note = null;
        if (!blocks.getOrDefault("experience", new StringBuilder()).toString().isBlank() && jobs.isEmpty()) {
            note = "Le texte d'expérience est conservé, mais aucune fiche n'a pu être séparée.";
        }
        return new Parsed(sections, List.copyOf(all), note);
    }

    private static List<Experience> readEntries(String block, String forcedKind) {
        List<Experience> out = new ArrayList<>();
        StringBuilder current = null;
        boolean previousBullet = false;
        for (String line : block.split("\n")) {
            String normalized = normalizeLine(line);
            if (normalized.isBlank()) {
                flush(out, current, forcedKind);
                current = null;
                previousBullet = false;
                continue;
            }
            if (isBullet(normalized)) {
                previousBullet = true;
                continue;
            }
            if (previousBullet && !attachesAsDate(normalized) && !looksLikeEntryHeader(normalized)) {
                previousBullet = false;
                continue;
            }
            previousBullet = false;
            if (current != null && attachesAsDate(normalized)) {
                current.append(' ').append(normalized);
                continue;
            }
            if (attachesAsDate(normalized)) continue;
            flush(out, current, forcedKind);
            current = new StringBuilder(normalized);
        }
        flush(out, current, forcedKind);
        return out;
    }

    private static void flush(List<Experience> out, StringBuilder current, String forcedKind) {
        if (current == null || current.toString().isBlank()) return;
        out.add(toExperience(current.toString(), forcedKind));
    }

    private static Experience toExperience(String text, String forcedKind) {
        String kind = forcedKind != null ? forcedKind : kindOf(text);
        String start = null;
        String end = null;
        boolean incomplete = true;
        Matcher years = YEAR.matcher(text);
        List<String> found = new ArrayList<>();
        while (years.find()) found.add(years.group());
        boolean ongoing = ONGOING.matcher(fold(text)).find();
        if (found.size() >= 2) {
            start = found.get(0);
            end = found.get(found.size() - 1);
            incomplete = false;
        } else if (found.size() == 1) {
            start = found.get(0);
            incomplete = !ongoing;
        }
        String withoutDates = stripDateTokens(text);
        String[] parts = SPLIT.split(withoutDates.trim());
        String title = parts.length > 0 ? clean(stripKind(parts[0])) : "";
        String org = parts.length > 1 ? clean(parts[1]) : "";
        if (title.isBlank()) title = clean(stripKind(withoutDates));
        if (org.isBlank()) org = null;
        if (title.isBlank()) title = null;
        return new Experience(title, org, kind, start, end, incomplete);
    }

    private static String kindOf(String text) {
        if (STAGE.matcher(text).find()) return "STAGE";
        if (ALTERNANCE.matcher(text).find()) return "ALTERNANCE";
        return "EMPLOI";
    }

    private static String stripKind(String value) {
        return value.replaceFirst("(?i)^(stages?|stagiaires?|intern(?:ship)?s?|alternances?|apprentice(?:ship)?|apprenti(?:e)?s?)\\b[:\\s-]*", "").trim();
    }

    private static String summarize(List<Experience> jobs) {
        List<String> lines = new ArrayList<>();
        for (Experience job : jobs) {
            StringBuilder line = new StringBuilder(job.kind());
            if (job.title() != null) line.append(" · ").append(job.title());
            if (job.organization() != null) line.append(" · ").append(job.organization());
            if (job.start() != null) {
                line.append(" · ").append(job.start());
                if (job.end() != null) line.append("–").append(job.end());
                else if (job.datesIncomplete()) line.append(" (dates incomplètes)");
            }
            lines.add(line.toString());
        }
        return String.join("\n", lines);
    }

    private static boolean attachesAsDate(String line) {
        if (!YEAR.matcher(line).find()) return false;
        String stripped = MONTHS.matcher(fold(line)).replaceAll(" ");
        String letters = stripped.replaceAll("[^a-z]", "");
        letters = letters.replace("depuis", "").replace("present", "").replace("aujourd", "")
                .replace("current", "").replace("now", "").replace("cours", "");
        return letters.length() <= 2;
    }

    private static String normalizeLine(String line) {
        return (line == null ? "" : line)
                .replace('\u007f', '•')
                .replace('\u0007', '•')
                .replace('\u000b', ' ')
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static boolean isBullet(String line) {
        return line.contains("•") || line.contains("▪") || line.contains("◦") || line.contains("‣")
                || line.matches("^[*]\\s*.*")
                || line.matches("^[-–—]\\s+.*");
    }

    private static boolean looksLikeEntryHeader(String line) {
        return line.contains("—") || line.contains("–")
                || Pattern.compile("(?i)\\s+(?:chez|at)\\s+").matcher(line).find()
                || line.matches(".*\\s+-\\s+.*")
                || line.contains(",");
    }

    private static String stripDateTokens(String value) {
        String[] original = value.split("\\s+");
        StringBuilder kept = new StringBuilder();
        for (String token : original) {
            String tokenFolded = fold(token).replaceAll("[.,]", "");
            if (tokenFolded.matches("(19|20)\\d{2}")
                    || MONTHS.matcher(tokenFolded).matches()
                    || tokenFolded.matches("depuis|present|aujourd'hui|aujourd|current|now|ongoing|cours")) {
                continue;
            }
            if (!kept.isEmpty()) kept.append(' ');
            kept.append(token);
        }
        return kept.toString();
    }

    private static String languagesIn(String text) {
        List<String> found = new ArrayList<>();
        Matcher matcher = LANGUAGE_NAME.matcher(text == null ? "" : text);
        while (matcher.find()) {
            String canonical = canonicalLanguage(matcher.group());
            if (canonical == null || found.stream().anyMatch(x -> x.startsWith(canonical + " "))) continue;
            String after = text.substring(matcher.end(), Math.min(text.length(), matcher.end() + 24));
            Matcher level = LEVEL.matcher(after);
            found.add(canonical + (level.find() ? " (" + level.group().trim() + ")" : ""));
        }
        return String.join(", ", found);
    }

    private static String canonicalLanguage(String value) {
        String folded = fold(value);
        for (String[] pair : LANGUAGES) {
            if (pair[0].equals(folded)) return pair[1];
        }
        return null;
    }

    private static void enrichEducation(Map<String, String> sections, String block) {
        sections.put("education_school", "");
        sections.put("education_degree", "");
        sections.put("education_domain", "");
        sections.put("education_start", "");
        sections.put("education_end", "");
        sections.put("graduation_year", "");
        if (block == null || block.isBlank()) return;

        List<String> years = new ArrayList<>();
        Matcher yearMatcher = YEAR.matcher(block);
        while (yearMatcher.find()) years.add(yearMatcher.group());
        if (!years.isEmpty()) {
            if (years.size() >= 2) {
                sections.put("education_start", years.get(0));
                sections.put("education_end", years.get(years.size() - 1));
            }
            sections.put("graduation_year", years.get(years.size() - 1));
        }

        String firstLine = block.lines().map(String::trim).filter(line -> !line.isBlank()).findFirst().orElse("");
        String withoutDates = stripDateTokens(firstLine).replaceAll("\\([^)]*\\)", " ").replaceAll("\\s+", " ").trim();
        String[] parts = withoutDates.split("\\s*[—–]\\s*");
        if (parts.length == 1) parts = withoutDates.split("\\s+-\\s+");
        if (parts.length > 0) sections.put("education_school", clean(parts[0]));
        if (parts.length > 1) sections.put("education_degree", clean(parts[1]));
        if (parts.length > 2) sections.put("education_domain", clean(String.join(" — ", java.util.Arrays.copyOfRange(parts, 2, parts.length))));
    }

    private static void enrichPreferences(Map<String, String> sections, String text) {
        sections.put("preferred_locations", "");
        sections.put("work_mode", "");
        String source = text == null ? "" : text;
        List<String> locations = new ArrayList<>();
        Matcher labelled = Pattern.compile("(?im)^(?:lieux recherchés|locations?|based in)\\s*[:\\-]?\\s*([^.;\\n]+)$").matcher(source);
        while (labelled.find()) addLocations(locations, labelled.group(1));
        Matcher location = Pattern.compile("(?i)(?:^|\\s)[àa]\\s+([\\p{L}][\\p{L}'-]{1,40})(?=\\s+(?:ou|or)\\b|[.;])").matcher(source);
        while (location.find()) addLocations(locations, location.group(1));
        if (!locations.isEmpty()) sections.put("preferred_locations", String.join(", ", locations));
        String folded = fold(source);
        if (folded.contains("teletravail") || folded.contains("remote")) sections.put("work_mode", "Télétravail");
        else if (folded.contains("hybride") || folded.contains("hybrid")) sections.put("work_mode", "Hybride");
        else if (folded.contains("sur site") || folded.contains("on-site") || folded.contains("presentiel")) {
            sections.put("work_mode", "Sur site");
        }
    }

    private static void addLocations(List<String> locations, String value) {
        for (String part : value.split("(?i)\\s+ou\\s+|\\s+or\\s+|\\s*,\\s*")) {
            String location = clean(part);
            if (!location.isBlank() && locations.stream().noneMatch(existing -> fold(existing).equals(fold(location)))) {
                locations.add(location);
            }
        }
    }

    private static String headingKey(String line) {
        List<String> hits = exactHeadings(line);
        return hits.size() == 1 ? hits.get(0) : null;
    }

    private static List<String> headingHits(String line) {
        List<String> exact = exactHeadings(line);
        if (!exact.isEmpty()) return exact;
        String key = normalizeHeading(line);
        if (key.length() > 48) return List.of();
        String rest = key;
        List<String> hits = new ArrayList<>();
        List<String> labels = new ArrayList<>(HEADINGS.values());
        labels.sort((a, b) -> Integer.compare(b.length(), a.length()));
        for (String label : labels) {
            if (!Pattern.compile("\\b" + Pattern.quote(label) + "\\b").matcher(rest).find()) continue;
            String section = sectionOf(label);
            if (!hits.contains(section)) hits.add(section);
            rest = rest.replace(label, " ");
        }
        String leftover = rest.replaceAll("[^a-z]", "");
        return hits.size() >= 2 && leftover.length() <= 2 ? hits : List.of();
    }

    private static List<String> exactHeadings(String line) {
        String key = normalizeHeading(line);
        if (key.length() > 48) return List.of();
        List<String> hits = new ArrayList<>();
        for (Map.Entry<String, String> entry : HEADINGS.entrySet()) {
            if (!key.equals(entry.getValue())) continue;
            String section = entry.getKey().substring(0, entry.getKey().indexOf('|'));
            if (!hits.contains(section)) hits.add(section);
        }
        return hits;
    }

    private static String sectionOf(String label) {
        for (Map.Entry<String, String> entry : HEADINGS.entrySet()) {
            if (entry.getValue().equals(label)) return entry.getKey().substring(0, entry.getKey().indexOf('|'));
        }
        return "";
    }

    private static String normalizeHeading(String line) {
        return fold(line).replaceAll("[:\\-–—]+$", "").replaceAll("\\s+", " ").trim();
    }

    private static void putAll(String section, String... labels) {
        for (String label : labels) HEADINGS.put(section + "|" + label, label);
    }

    private static Map<String, String> emptySections() {
        Map<String, String> out = new LinkedHashMap<>();
        for (String key : List.of("identity", "experience", "education", "skills", "languages", "projects")) out.put(key, "");
        return out;
    }

    private static String clip(String value) {
        String clean = value == null ? "" : value.replace('\u0000', ' ').trim();
        return clean.length() > 1500 ? clean.substring(0, 1500) : clean;
    }

    private static String clean(String value) {
        String out = value == null ? "" : value.replaceAll("\\s+", " ").replaceAll("^[,;:\\-–—]+|[,;:\\-–—]+$", "").trim();
        return out.length() > 160 ? out.substring(0, 160).trim() : out;
    }

    private static String fold(String value) {
        return Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);
    }
}
