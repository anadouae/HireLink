package ma.ensat.hirelink.service;

import org.springframework.stereotype.Component;

import java.math.BigInteger;

/**
 * Verification 1 - validation locale de l'ICE (Identifiant Commun de l'Entreprise, Maroc).
 * Structure officielle OMPIC : 15 positions = 9 (entreprise) + 4 (etablissement) + 2 (controle).
 * <p>
 * La cle modulo 97 est documentee par des sources tiers (python-stdnum, outils prives) mais
 * <strong>pas dans un texte OMPIC/DGI public</strong>. Elle est donc calculee en avis seulement :
 * un ecart de cle n'entraine jamais un rejet automatique du format structurel.
 */
@Component
public class IceFormatValidator {

    public record Result(
            boolean formatValid,
            String normalized,
            String enterpriseCode,
            String establishmentCode,
            String checkDigits,
            /** {@code null} si non evaluable ; avis seulement, jamais bloquant. */
            Boolean checkDigitAdvisoryOk,
            String message
    ) {}

    /**
     * Normalise (retire espaces / separateurs, conserve les zeros de tete) puis valide la structure 9+4+2.
     */
    public Result validate(String raw) {
        if (raw == null || raw.isBlank()) {
            return new Result(false, "", null, null, null, null, "ICE obligatoire");
        }
        String normalized = normalize(raw);
        if (normalized.isEmpty()) {
            return new Result(false, "", null, null, null, null, "ICE invalide : aucun chiffre reconnu");
        }
        if (!normalized.chars().allMatch(Character::isDigit)) {
            return new Result(false, normalized, null, null, null, null,
                    "ICE invalide : uniquement des chiffres (espaces et separateurs sont retires)");
        }
        if (normalized.length() != 15) {
            return new Result(false, normalized, null, null, null, null,
                    "ICE invalide : exactement 15 chiffres requis (actuellement " + normalized.length() + ")");
        }
        String enterprise = normalized.substring(0, 9);
        String establishment = normalized.substring(9, 13);
        String check = normalized.substring(13, 15);
        Boolean advisory = advisoryCheckDigitOk(normalized);
        String msg = "Format ICE valide (9 + 4 + 2)";
        if (Boolean.FALSE.equals(advisory)) {
            msg += " - avis : la cle de controle ne correspond pas au calcul modulo 97 (non officiel OMPIC ; non bloquant)";
        } else if (Boolean.TRUE.equals(advisory)) {
            msg += " - avis : cle de controle coherente (modulo 97)";
        }
        return new Result(true, normalized, enterprise, establishment, check, advisory, msg);
    }

    /** Retire tout sauf les chiffres ; ne tronque pas et ne retire pas les zeros de tete. */
    public static String normalize(String raw) {
        if (raw == null) return "";
        StringBuilder sb = new StringBuilder(15);
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c >= '0' && c <= '9') sb.append(c);
        }
        return sb.toString();
    }

    /**
     * Avis modulo 97 : les 15 chiffres lus comme entier doivent etre == 0 (mod 97).
     * Non officiel - ne doit jamais servir de rejet exclusif.
     */
    static Boolean advisoryCheckDigitOk(String fifteenDigits) {
        if (fifteenDigits == null || fifteenDigits.length() != 15) return null;
        try {
            return new BigInteger(fifteenDigits).mod(BigInteger.valueOf(97)).signum() == 0;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
