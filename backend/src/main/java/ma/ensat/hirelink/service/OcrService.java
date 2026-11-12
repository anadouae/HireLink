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

    public record OcrResult(String text, List<String> suggestedSkills, String filename, int chars, boolean textLayer, String note) {}

    private final CvSectionParser sectionParser = new CvSectionParser();

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
        boolean textLayer = true;
        String note = null;
        if (kind == FileSignature.PDF) {
            text = extractPdf(data);
            if (letterCount(text) < 20) {
                textLayer = false;
                note = "Ce PDF n'a pas de texte sélectionnable. Aucun moteur OCR n'est installé, donc aucune expérience n'a été déduite.";
                text = note;
            }
        } else if (kind == FileSignature.TEXT) {
            text = new String(data, StandardCharsets.UTF_8);
        } else {
            textLayer = false;
            note = "Image reçue. Aucun moteur OCR n'est installé. Déposez un PDF textuel ou un fichier TXT.";
            text = note;
        }
        text = text == null ? "" : text.replace('\u0000', ' ').trim();
        List<String> skills = textLayer ? suggestSkills(text) : List.of();
        String kept = text.length() > 12000 ? text.substring(0, 12000) : text;
        return new OcrResult(kept, skills, name, textLayer ? text.length() : 0, textLayer, note);
    }

    public CvSectionParser.Parsed analyze(String text, List<String> skills, boolean textLayer, String note) {
        if (!textLayer) return new CvSectionParser.Parsed(emptySections(), List.of(), note);
        CvSectionParser.Parsed parsed = sectionParser.parse(text, skills);
        String merged = parsed.note() != null ? parsed.note() : note;
        return new CvSectionParser.Parsed(parsed.sections(), parsed.experiences(), merged);
    }

    private static Map<String, String> emptySections() {
        Map<String, String> out = new LinkedHashMap<>();
        for (String key : List.of("identity", "experience", "education", "skills", "languages", "projects")) out.put(key, "");
        return out;
    }

    private static int letterCount(String text) {
        if (text == null) return 0;
        int n = 0;
        for (int i = 0; i < text.length(); i++) if (Character.isLetter(text.charAt(i))) n++;
        return n;
    }

    /** Découpe un CV déjà lu en sections familières. Le candidat confirme avant enregistrement. */
    public Map<String, String> sections(String text, List<String> skills) {
        return sectionParser.parse(text, skills).sections();
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
