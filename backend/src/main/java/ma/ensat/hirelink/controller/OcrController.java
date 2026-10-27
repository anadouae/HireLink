package ma.ensat.hirelink.controller;

import ma.ensat.hirelink.model.User;
import ma.ensat.hirelink.repository.UserRepository;
import ma.ensat.hirelink.service.OcrService;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/ocr")
public class OcrController {

    private final OcrService ocrService;
    private final UserRepository userRepository;

    public OcrController(OcrService ocrService, UserRepository userRepository) {
        this.ocrService = ocrService;
        this.userRepository = userRepository;
    }

    @PostMapping(value = "/cv", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> parseCv(
            @AuthenticationPrincipal User user,
            @RequestPart("file") MultipartFile file,
            @RequestParam(value = "applySkills", defaultValue = "true") boolean applySkills
    ) {
        try {
            OcrService.OcrResult result = ocrService.extract(file);
            if (applySkills && !result.suggestedSkills().isEmpty()) {
                String merged = mergeSkills(user.getSkills(), result.suggestedSkills());
                user.setSkills(merged);
                if (user.getFullName() != null && !user.getFullName().isBlank()) {
                    user.setProfileComplete(true);
                }
                userRepository.save(user);
            }
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("filename", result.filename());
            body.put("chars", result.chars());
            body.put("suggestedSkills", result.suggestedSkills());
            body.put("textPreview", result.text().length() > 800
                    ? result.text().substring(0, 800) + "…"
                    : result.text());
            body.put("sections", ocrService.sections(result.text(), result.suggestedSkills()));
            body.put("skillsApplied", applySkills);
            return body;
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "OCR échoué: " + e.getMessage());
        }
    }

    private String mergeSkills(String existing, java.util.List<String> suggested) {
        java.util.LinkedHashSet<String> set = new java.util.LinkedHashSet<>();
        if (existing != null && !existing.isBlank()) {
            for (String s : existing.split("[,;|/]+")) {
                if (!s.isBlank()) set.add(s.trim());
            }
        }
        set.addAll(suggested);
        return set.stream().limit(40).collect(Collectors.joining(", "));
    }
}
