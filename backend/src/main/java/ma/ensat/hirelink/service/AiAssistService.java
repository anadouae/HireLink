package ma.ensat.hirelink.service;

import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Assist IA locale (règles + templates) — freemium sans API payante.
 * Premium débloque les versions longues / multi-scénarios.
 */
@Service
public class AiAssistService {

    public record AssistResult(String title, String content, boolean premiumLocked, List<String> tips) {}

    public AssistResult rewriteProfile(String skills, String roles, String level, boolean premium) {
        List<String> skillList = split(skills);
        String role = first(roles, "le poste ciblé");
        String lvl = first(level, "votre niveau");

        StringBuilder sb = new StringBuilder();
        sb.append("Profil orienté résultats — ").append(role).append("\n\n");
        sb.append("Je suis un profil ").append(lvl.toLowerCase())
          .append(" motivé(e) par des missions concrètes, avec une capacité à livrer vite et clairement.\n\n");
        if (!skillList.isEmpty()) {
            sb.append("Compétences clés : ").append(String.join(", ", skillList.stream().limit(8).toList())).append(".\n");
        }
        sb.append("Je recherche une collaboration transparente : délais tenus, feedback utile, progression mesurable.\n");

        List<String> tips = new ArrayList<>();
        tips.add("Ajoutez 1 chiffre concret (projet, délai, volume).");
        tips.add("Alignez 3 compétences sur l’offre ciblée.");
        if (!premium) {
            tips.add("Premium : pitch long + variantes CDI / Stage / Remote.");
            return new AssistResult("Pitch profil (aperçu freemium)", sb.toString().trim(), true, tips);
        }
        sb.append("\nVariante courte (LinkedIn-style) :\n");
        sb.append(role).append(" | ").append(lvl).append(" | ")
          .append(skillList.stream().limit(4).collect(Collectors.joining(" · "))).append(".\n");
        sb.append("\nVariante entretien (30 s) :\n");
        sb.append("« Je combine ").append(skillList.stream().limit(3).collect(Collectors.joining(", ")))
          .append(" pour produire de la valeur rapidement, avec un suivi clair à chaque étape. »\n");
        tips.add("Enregistrez ce pitch dans votre profil avant de postuler.");
        return new AssistResult("Pitch profil Premium", sb.toString().trim(), false, tips);
    }

    public AssistResult interviewPrep(String jobTitle, String skills, boolean premium) {
        String title = first(jobTitle, "le poste");
        List<String> skillList = split(skills);
        StringBuilder sb = new StringBuilder();
        sb.append("Préparation entretien — ").append(title).append("\n\n");
        sb.append("1) Ouverture (60 s)\n");
        sb.append("Présentez votre parcours en 3 points : contexte, compétence phare, résultat.\n\n");
        sb.append("2) Questions probables\n");
        sb.append("- Pourquoi ce poste / cette entreprise ?\n");
        sb.append("- Exemple de projet lié à ").append(skillList.isEmpty() ? "vos skills" : skillList.get(0)).append(" ?\n");
        sb.append("- Comment gérez-vous un délai serré ?\n\n");
        sb.append("3) Questions à poser\n");
        sb.append("- Quel est le critère de succès à 90 jours ?\n");
        sb.append("- Comment se prend la décision après l’entretien ?\n");

        List<String> tips = List.of(
                "Préparez 1 STAR story (Situation, Tâche, Action, Résultat).",
                "Relisez le SLA de l’offre : montrez que vous valorisez les délais."
        );
        if (!premium) {
            return new AssistResult("Prep entretien (aperçu)", sb.toString().trim(), true,
                    List.of(tips.get(0), "Premium : script réponses + grille d’auto-évaluation."));
        }
        sb.append("\n4) Script réponses (Premium)\n");
        sb.append("Q: Force principale ?\nR: « Ma force est de clarifier le besoin, livrer un incrément utile, puis itérer avec feedback. »\n\n");
        sb.append("Q: Faiblesse ?\nR: « Je peux trop perfectionner — je me fixe maintenant des critères de done explicites. »\n\n");
        sb.append("5) Checklist J-1\n- CV à jour · 3 exemples · questions · tenue / setup visio\n");
        return new AssistResult("Prep entretien Premium", sb.toString().trim(), false, tips);
    }

    private List<String> split(String raw) {
        if (raw == null || raw.isBlank()) return List.of();
        return Arrays.stream(raw.split("[,;|/]+"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .limit(20)
                .toList();
    }

    private String first(String raw, String fallback) {
        if (raw == null || raw.isBlank()) return fallback;
        String[] p = raw.split("[,;/]+");
        return p[0].trim().isEmpty() ? fallback : p[0].trim();
    }
}
