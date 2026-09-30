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
        String name = file.getOriginalFilename() == null ? "file" : file.getOriginalFilename().toLowerCase();
        String text;
        if (name.endsWith(".pdf")) {
            text = extractPdf(file.getBytes());
        } else if (name.endsWith(".txt") || name.endsWith(".md") || name.endsWith(".csv")) {
            text = new String(file.getBytes(), StandardCharsets.UTF_8);
        } else if (name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".webp")) {
            // Image OCR sans Tesseract local : on renvoie un guide + hash meta
            text = "[Image reçue — installez Tesseract pour OCR pixel. "
                    + "En attendant, collez le texte du CV ou uploadez un PDF/TXT.]\n"
                    + "Fichier: " + name + " (" + file.getSize() + " octets)";
        } else {
            // Tentative texte brut
            text = new String(file.getBytes(), StandardCharsets.UTF_8);
            if (text.chars().filter(c -> c < 9 || (c > 14 && c < 32)).count() > text.length() / 5) {
                throw new IllegalArgumentException("Format non supporté — PDF, TXT ou image");
            }
        }
        text = text == null ? "" : text.replace('\u0000', ' ').trim();
        List<String> skills = suggestSkills(text);
        return new OcrResult(text.length() > 12000 ? text.substring(0, 12000) : text, skills, name, text.length());
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
