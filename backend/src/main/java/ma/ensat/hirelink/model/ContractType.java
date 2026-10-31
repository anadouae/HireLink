package ma.ensat.hirelink.model;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Types de contrat proposés à la création d'une offre ; chacun décide des champs obligatoires et des
 * rémunérations possibles. L'offre conserve le libellé (colonne {@code contract_type}) : les offres existantes restent valables.
 */
public enum ContractType {
    CDI("CDI", false, false),
    CDD("CDD", false, true),
    STAGE("Stage", true, true),
    PFE("PFE", true, true),
    PFA("PFA", true, true),
    STAGE_ETE("Stage d'été", true, true),
    ALTERNANCE("Alternance", false, true),
    FREELANCE("Freelance", false, true),
    TEMPS_PARTIEL("Temps partiel", false, false);

    private final String label;
    private final boolean internship;
    private final boolean durationRequired;

    ContractType(String label, boolean internship, boolean durationRequired) {
        this.label = label;
        this.internship = internship;
        this.durationRequired = durationRequired;
    }

    public String label() { return label; }

    /** Stage, PFE, PFA, stage d'été : gratification possible, convention de stage, pas de période d'essai. */
    public boolean isInternship() { return internship; }

    public boolean isDurationRequired() { return durationRequired; }

    /**
     * Types de contrat qu'un candidat peut cocher dans son profil : ceux des offres, plus des situations
     * qui n'existent pas comme offre (premier emploi, job étudiant, bénévolat). Source unique du formulaire et de la validation.
     */
    public static final List<String> PREFERENCES = Stream.concat(
            Arrays.stream(values()).map(ContractType::label),
            Stream.of("Premier emploi", "Job étudiant", "Bénévolat")).toList();

    /** Garde, sans doublon et dans l'ordre saisi, les préférences connues d'une liste « Stage, CDI ». */
    public static String cleanPreferences(String csv) {
        if (csv == null || csv.isBlank()) return "";
        LinkedHashSet<String> kept = new LinkedHashSet<>();
        for (String part : csv.split(",")) {
            String item = part.trim();
            PREFERENCES.stream().filter(p -> p.equalsIgnoreCase(item)).findFirst().ifPresent(kept::add);
        }
        return String.join(", ", kept);
    }

    public static Optional<ContractType> fromLabel(String label) {
        return Arrays.stream(values()).filter(t -> t.label.equalsIgnoreCase(label == null ? "" : label.trim())).findFirst();
    }
}
