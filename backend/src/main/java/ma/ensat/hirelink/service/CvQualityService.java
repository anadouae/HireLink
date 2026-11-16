package ma.ensat.hirelink.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import ma.ensat.hirelink.model.User;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Vérification technique déterministe du dossier CV/profil.
 * Ce n’est ni un score d’embauche, ni une certification ATS.
 */
@Service
public class CvQualityService {

    private final ObjectMapper mapper = new ObjectMapper();

    public Map<String, Object> assess(User user) {
        List<Map<String, Object>> checks = new ArrayList<>();
        boolean hasCv = user.getCvStorageName() != null && !user.getCvStorageName().isBlank();
        checks.add(check("cv_file", "Document CV déposé", hasCv,
                hasCv ? "Un fichier est associé au compte." : "Aucun CV déposé."));

        String sections = user.getCvSections();
        JsonNode root = read(sections);
        String identity = text(root, "identity");
        String experience = text(root, "experience");
        String education = text(root, "education");
        String skillsSec = text(root, "skills");
        String languages = text(root, "languages");

        boolean textExtractable = (identity + experience + education + skillsSec + languages).trim().length() > 40
                || (user.getSkills() != null && user.getSkills().trim().length() > 5);
        checks.add(check("text_extractable", "Texte extractible / sections confirmées", textExtractable,
                textExtractable
                        ? "Des sections ou compétences sont disponibles pour lecture."
                        : "Peu ou pas de texte confirmé — PDF scanné sans OCR, ou sections non confirmées."));

        checks.add(check("contact", "Coordonnées profil",
                notBlank(user.getEmail()) && (notBlank(user.getPhone()) || notBlank(user.getCity())),
                notBlank(user.getPhone()) || notBlank(user.getCity())
                        ? "E-mail présent ; téléphone ou ville renseignés."
                        : "Ajoutez un téléphone ou une ville pour compléter les coordonnées."));

        checks.add(check("identity_section", "Section identité CV", identity.trim().length() > 8,
                identity.trim().length() > 8 ? "Identité lue ou saisie." : "Section identité vide."));

        checks.add(check("experience_section", "Expériences",
                experience.trim().length() > 40 || notBlank(user.getProfileExperiences()),
                experience.trim().length() > 40 || notBlank(user.getProfileExperiences())
                        ? "Expériences présentes."
                        : "Aucune expérience structurée ou confirmée."));

        boolean hasEdu = education.trim().length() > 8 || notBlank(user.getProfileEducations()) || notBlank(user.getSchool());
        checks.add(check("education_section", "Formations", hasEdu,
                hasEdu ? "Formation détectée ou saisie." : "Ajoutez au moins une formation."));

        int skillN = splitCount(user.getSkills()) + splitCount(skillsSec);
        checks.add(check("skills", "Compétences", skillN >= 3,
                skillN >= 3 ? skillN + " compétences listées." : "Ajoutez au moins 3 compétences."));

        checks.add(check("languages", "Langues", languages.trim().length() > 2 || skillsLookLikeLang(user.getSkills()),
                "Langues renseignées ou absentes (à compléter si besoin)."));

        long ok = checks.stream().filter(c -> Boolean.TRUE.equals(c.get("ok"))).count();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("kind", "TECHNICAL_CHECKLIST");
        out.put("disclaimer",
                "Vérification technique déterministe. Ce n’est pas une certification ATS, "
                        + "ni une probabilité d’embauche, ni un score de matching offre.");
        out.put("passed", ok);
        out.put("total", checks.size());
        out.put("checks", checks);
        out.put("hasCv", hasCv);
        out.put("fileName", user.getCvFileName() == null ? "" : user.getCvFileName());
        return out;
    }

    private Map<String, Object> check(String id, String label, boolean ok, String detail) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", id);
        row.put("label", label);
        row.put("ok", ok);
        row.put("detail", detail);
        return row;
    }

    private JsonNode read(String json) {
        if (json == null || json.isBlank()) return mapper.createObjectNode();
        try {
            return mapper.readTree(json);
        } catch (Exception e) {
            return mapper.createObjectNode();
        }
    }

    private static String text(JsonNode root, String field) {
        if (root == null || !root.has(field) || root.get(field).isNull()) return "";
        return root.get(field).asText("");
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private static int splitCount(String csv) {
        if (csv == null || csv.isBlank()) return 0;
        int n = 0;
        for (String p : csv.split("[,;\\n]+")) if (!p.isBlank()) n++;
        return n;
    }

    private static boolean skillsLookLikeLang(String skills) {
        if (skills == null) return false;
        String s = skills.toLowerCase();
        return s.contains("anglais") || s.contains("français") || s.contains("arabe")
                || s.contains("spanish") || s.contains("english") || s.contains("(b1") || s.contains("(b2");
    }
}
