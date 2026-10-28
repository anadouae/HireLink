package ma.ensat.hirelink.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class OcrService {

    private static final Set<String> SKILL_LEXICON = Set.of(
            "java", "spring", "spring boot", "mysql", "postgresql", "mongodb",
            "javascript", "typescript", "react", "angular", "vue", "node",
            "python", "django", "fastapi", "docker", "kubernetes", "git",
            "rest", "api", "html", "css", "sql", "hibernate", "jpa",
            "aws", "azure", "linux", "agile", "scrum", "figma", "excel",
            "power bi", "etl", "kafka", "redis", "graphql", "ci/cd"
    );

    public record OcrResult(String text, List<String> suggestedSkills, String filename, int chars) {}

    public OcrResult extract(MultipartFile file) throws Exception {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Fichier vide");
        }
        if (file.getSize() > 12L * 1024 * 1024) {
            throw new IllegalArgumentException("Fichier trop volumineux");
        }
        byte[] data = file.getBytes();
        FileSignature.Kind kind = FileSignature.detect(data);
        if (kind == null || kind == FileSignature.GIF) {
            throw new IllegalArgumentException("Format non supporté — PDF, TXT ou image (PNG, JPG, WEBP)");
        }
        String raw = file.getOriginalFilename() == null ? "cv" : file.getOriginalFilename();
        String name = raw.replaceAll("[^\\p{L}\\p{N}._ -]", "_");
        if (name.length() > 80) name = name.substring(name.length() - 80);
        String text;
        if (kind == FileSignature.PDF) {
            text = extractPdf(data);
        } else if (kind == FileSignature.TEXT) {
            text = new String(data, StandardCharsets.UTF_8);
        } else {
            text = "[Image reçue — la lecture automatique des images n'est pas installée. "
                    + "Collez le texte du CV ou déposez un PDF / fichier texte.]\n"
                    + "Fichier: " + name + " (" + file.getSize() + " octets)";
        }
        text = text == null ? "" : text.replace('\u0000', ' ').trim();
        List<String> skills = suggestSkills(text);
        String kept = text.length() > 12000 ? text.substring(0, 12000) : text;
        return new OcrResult(kept, skills, name, text.length());
    }

    /** Découpe un CV déjà lu en sections familières. Le candidat confirme avant enregistrement. */
    public Map<String, String> sections(String text, List<String> skills) {
        String src = text == null ? "" : text.trim();
        Map<String, Integer> marks = new LinkedHashMap<>();
        marks.put("experience", indexOf(src, "(?i)(expériences?\\s+professionnelles?|expérience\\s+professionnelle|parcours\\s+professionnel|work\\s+experience)"));
        marks.put("education", indexOf(src, "(?i)(formations?|études|education|diplômes?|scolarité)"));
        marks.put("skills", indexOf(src, "(?i)(compétences|competences|skills|technologies|outils)"));
        marks.put("languages", indexOf(src, "(?i)(langues?|languages)"));

        List<Map.Entry<String, Integer>> found = marks.entrySet().stream()
                .filter(e -> e.getValue() >= 0)
                .sorted(Comparator.comparingInt(Map.Entry::getValue))
                .toList();

        Map<String, String> out = new LinkedHashMap<>();
        int first = found.isEmpty() ? src.length() : found.get(0).getValue();
        String identity = clip(src.substring(0, first));
        String contacts = contacts(src);
        if (!contacts.isBlank() && !identity.toLowerCase(Locale.ROOT).contains(contacts.toLowerCase(Locale.ROOT).substring(0, Math.min(12, contacts.length())))) {
            identity = clip((identity + "\n" + contacts).trim());
        }
        out.put("identity", identity.isBlank() ? contacts : identity);

        for (int i = 0; i < found.size(); i++) {
            int start = found.get(i).getValue();
            int end = i + 1 < found.size() ? found.get(i + 1).getValue() : src.length();
            String body = src.substring(start, end);
            body = body.replaceFirst("(?s)^[^\\n]{0,40}\\n?", "").trim();
            out.put(found.get(i).getKey(), clip(body));
        }
        out.putIfAbsent("experience", "");
        out.putIfAbsent("education", "");
        String skillLine = skills == null ? "" : String.join(", ", skills);
        if (out.getOrDefault("skills", "").isBlank()) out.put("skills", clip(skillLine));
        if (out.getOrDefault("languages", "").isBlank()) out.put("languages", clip(knownLanguages(src)));
        return out;
    }

    private int indexOf(String text, String regex) {
        Matcher m = Pattern.compile(regex).matcher(text);
        return m.find() ? m.start() : -1;
    }

    private String contacts(String text) {
        List<String> bits = new ArrayList<>();
        Matcher email = Pattern.compile("[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}", Pattern.CASE_INSENSITIVE).matcher(text);
        if (email.find()) bits.add(email.group());
        Matcher phone = Pattern.compile("(?:\\+212|0)[\\s.-]*[5-7](?:[\\s.-]*\\d){8}").matcher(text);
        if (phone.find()) bits.add(phone.group().replaceAll("\\s+", " ").trim());
        return String.join(" · ", bits);
    }

    private String knownLanguages(String text) {
        String[] langs = {"Français", "Arabe", "Anglais", "Espagnol", "Amazigh", "Allemand"};
        List<String> hit = new ArrayList<>();
        for (String lang : langs) {
            if (text.toLowerCase(Locale.ROOT).contains(lang.toLowerCase(Locale.ROOT))) hit.add(lang);
        }
        return String.join(", ", hit);
    }

    private String clip(String value) {
        if (value == null) return "";
        String clean = value.replace('\u0000', ' ').trim();
        return clean.length() > 1500 ? clean.substring(0, 1500) : clean;
    }

    private String extractPdf(byte[] bytes) throws Exception {
        try (PDDocument doc = Loader.loadPDF(bytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            return stripper.getText(doc);
        }
    }

    private List<String> suggestSkills(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        List<String> found = new ArrayList<>();
        for (String skill : SKILL_LEXICON) {
            Pattern p = Pattern.compile("\\b" + Pattern.quote(skill) + "\\b", Pattern.CASE_INSENSITIVE);
            Matcher m = p.matcher(lower);
            if (m.find()) {
                found.add(Arrays.stream(skill.split(" "))
                        .map(w -> w.substring(0, 1).toUpperCase() + w.substring(1))
                        .collect(Collectors.joining(" ")));
            }
        }
        return found.stream().distinct().limit(20).toList();
    }
}
