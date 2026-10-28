package ma.ensat.hirelink.security;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.Locale;
import java.util.Set;

/**
 * Politique de mot de passe (OWASP ASVS 2.1, NIST 800-63B) : 12 à 64 caractères, pas de mot de passe courant,
 * pas d'adresse e-mail ni de nom dedans. Pas de règles de composition arbitraires (la longueur prime).
 * Maximum 64 caractères / 72 octets : limite de BCrypt (au-delà, le mot de passe serait tronqué en silence).
 */
public final class PasswordPolicy {

    public static final int MIN = 12;
    public static final int MAX = 64;
    public static final String RULES = "12 à 64 caractères, sans mot de passe courant (ex. « motdepasse2026 »), sans votre e-mail ni votre nom.";

    private static final Set<String> COMMON = Set.of(
            "password", "motdepasse", "motdepasse123", "azerty", "azertyuiop", "azertyuiopqsdf", "qwerty", "qwertyuiop", "qwertyuiopasdf",
            "admin", "administrator", "administrateur", "root", "toor", "letmein", "welcome", "bienvenue", "bonjour", "salam", "salamalaykoum",
            "hirelink", "recruteur", "candidat", "recruiter", "candidate", "monmotdepasse", "mdp", "mdpmdpmdpmdp", "passw0rd", "p@ssw0rd",
            "iloveyou", "jetaime", "soleil", "dragon", "football", "marocain", "maroc", "casablanca", "tetouan", "ensatetouan", "ensa",
            "abc", "abcdef", "abcdefgh", "abcdefghijkl", "azertyazerty", "qsdfghjklm", "wxcvbn", "changeme", "changemenow", "secret",
            "master", "monkey", "shadow", "sunshine", "princess", "superman", "batman", "trustno", "whatever", "freedom", "login",
            "starwars", "pokemon", "naruto", "motdepassefort", "motdepassesecurise", "securite", "test", "testtest", "testing", "user",
            "username", "default", "guest", "demo", "azerty123", "qwerty123", "password1", "password123", "passwordpassword", "pass",
            "aaaaaaaa", "azertyui", "marrakech", "rabat", "tanger", "fes", "agadir", "bismillah", "inchallah", "mohamed", "ahmed", "fatima"
    );

    private PasswordPolicy() {}

    /** Lève 400 avec un message explicite si le mot de passe n'est pas acceptable. */
    public static void validate(String password, String email, String fullName) {
        String error = check(password, email, fullName);
        if (error != null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, error);
    }

    public static String check(String password, String email, String fullName) {
        if (password == null || password.length() < MIN) return "Mot de passe trop court : " + MIN + " caractères minimum.";
        if (password.length() > MAX || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            return "Mot de passe trop long : " + MAX + " caractères maximum.";
        }
        String letters = Normalizer.normalize(password, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT).replaceAll("[^a-z]", "");
        String alnum = password.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        if (COMMON.contains(letters) || COMMON.contains(alnum) || COMMON.contains(password.toLowerCase(Locale.ROOT))) {
            return "Ce mot de passe est trop courant. Choisissez une phrase de passe plus originale.";
        }
        if (password.chars().distinct().count() < 5) return "Mot de passe trop répétitif.";
        if (password.matches("\\d+")) return "Le mot de passe ne peut pas être uniquement composé de chiffres.";
        String lower = password.toLowerCase(Locale.ROOT);
        if (email != null && email.contains("@")) {
            String local = email.substring(0, email.indexOf('@')).toLowerCase(Locale.ROOT);
            if (local.length() >= 4 && lower.contains(local)) return "Le mot de passe ne doit pas contenir votre adresse e-mail.";
        }
        if (fullName != null) {
            for (String part : fullName.toLowerCase(Locale.ROOT).split("\\s+")) {
                if (part.length() >= 5 && lower.contains(part)) return "Le mot de passe ne doit pas contenir votre nom.";
            }
        }
        return null;
    }
}
