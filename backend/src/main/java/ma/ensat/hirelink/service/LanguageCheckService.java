package ma.ensat.hirelink.service;

import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Détecteur local de formulation genrée / excluante (Gaucher et al., 2011) — sans API payante.
 */
@Service
public class LanguageCheckService {

    private static final List<String> MASCULINE_MARKERS = List.of(
            "agressif", "agressive", "dominateur", "dominant", "competitif", "compétitif",
            "ninja", "rockstar", "hunter", "guerrier", "combatif", "ambitieux",
            "exigeant", "leader né", "homme de", "cherche homme", "jeune dynamique",
            "jeune motivé", "robustesse", "force de frappe"
    );

    private static final List<String> INCLUSIVE_SUGGESTIONS = List.of(
            "Préférez « expérience solide » à « agressif / combatifs ».",
            "Remplacez « ninja / rockstar » par le titre de poste réel.",
            "Formulez les qualités en compétences observables (livrer, collaborer, clarifier).",
            "Évitez les tournures « homme de … » ; préférez « personne capable de … »."
    );

    public record LanguageCheckResult(
            boolean flagged,
            List<String> markersFound,
            List<String> suggestions,
            String summary
    ) {}

    public LanguageCheckResult check(String title, String description) {
        String text = ((title == null ? "" : title) + " " + (description == null ? "" : description)).toLowerCase(Locale.ROOT);
        List<String> found = new ArrayList<>();
        for (String m : MASCULINE_MARKERS) {
            if (text.contains(m.toLowerCase(Locale.ROOT))) {
                found.add(m);
            }
        }
        if (found.isEmpty()) {
            return new LanguageCheckResult(false, List.of(), List.of(),
                    "Aucun marqueur fortement connoté détecté. Formulation acceptable.");
        }
        return new LanguageCheckResult(true, found, INCLUSIVE_SUGGESTIONS,
                "Formulation potentiellement excluante détectée (" + found.size()
                        + " marqueur(s)). Suggestions disponibles — décision humaine requise.");
    }
}
