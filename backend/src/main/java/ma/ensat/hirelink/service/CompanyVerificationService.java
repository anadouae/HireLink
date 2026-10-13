package ma.ensat.hirelink.service;

import ma.ensat.hirelink.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Anti-fraude employeur — modèle hybride LinkedIn-like :
 * inscription libre (raison sociale + ICE + email) sans documents ;
 * validation / badge a posteriori ; publication bloquée si non vérifié.
 */
@Service
public class CompanyVerificationService {

    /** Extrait registre (remplacer par API officielle OMPIC). */
    private static final Map<String, String> REGISTRY = Map.of(
            "001544289000035", "Atlas Digital",
            "001234567000012", "Nova RH",
            "002468135000098", "HireDesk Services"
    );

    private static final Set<String> FREE_EMAIL_DOMAINS = Set.of(
            "gmail.com", "googlemail.com", "yahoo.com", "yahoo.fr", "hotmail.com",
            "hotmail.fr", "outlook.com", "outlook.fr", "live.com", "icloud.com",
            "proton.me", "protonmail.com", "mail.com", "aol.com", "yandex.com"
    );

    public record Result(
            String ice,
            String companyName,
            boolean verified,
            String status,
            boolean corporateEmail,
            String message
    ) {}

    private final UserRepository userRepository;

    public CompanyVerificationService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public Result verify(String rawIce, String companyName) {
        return verify(rawIce, companyName, null, null);
    }

    public Result verify(String rawIce, String companyName, Long currentUserId) {
        return verify(rawIce, companyName, currentUserId, null);
    }

    public Result verify(String rawIce, String companyName, Long currentUserId, String email) {
        if (rawIce == null || rawIce.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "ICE obligatoire (inscription libre, mais ICE requis)");
        }
        if (companyName == null || companyName.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Raison sociale obligatoire");
        }
        String ice = rawIce.replaceAll("\\s+", "");
        if (!ice.matches("\\d{9,15}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "ICE invalide (9 à 15 chiffres)");
        }

        var existing = userRepository.findByCompanyIce(ice);
        if (existing.isPresent() && (currentUserId == null || !existing.get().getId().equals(currentUserId))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Cet ICE est déjà lié à un autre compte HireLink");
        }

        String name = companyName.trim();
        boolean corporateEmail = isCorporateEmail(email);

        // Filtre n°1 LinkedIn-like : email gratuit → jamais de badge auto
        if (!corporateEmail) {
            return new Result(ice, name, false, "NEED_CORPORATE_EMAIL", false,
                    "Compte créé. Badge / publication bloqués : utilisez un e-mail professionnel (@entreprise.ma), pas Gmail/Yahoo/Outlook.");
        }

        String registryName = REGISTRY.get(ice);
        if (registryName == null) {
            return new Result(ice, name, false, "WAITING_REGISTRY", true,
                    "Compte créé. E-mail pro OK — vérification registre (mock OMPIC) en attente. Publication encore bloquée.");
        }
        if (similar(name, registryName) < 0.72) {
            return new Result(ice, name, false, "NAME_MISMATCH", true,
                    "Raison sociale différente du registre pour cet ICE. Publication bloquée jusqu’à correction / revue.");
        }
        return new Result(ice, registryName, true, "REGISTRY_MATCH", true,
                "Entreprise vérifiée (matching registre + e-mail pro). Publication autorisée.");
    }

    public boolean isCorporateEmail(String email) {
        if (email == null || !email.contains("@")) {
            return false;
        }
        String domain = email.substring(email.indexOf('@') + 1).toLowerCase(Locale.ROOT).trim();
        return !domain.isEmpty() && !FREE_EMAIL_DOMAINS.contains(domain);
    }

    public String publishBlockReason(String verificationStatus) {
        if (verificationStatus == null || verificationStatus.isBlank()) {
            return "Entreprise non vérifiée — publication impossible.";
        }
        return switch (verificationStatus) {
            case "NEED_CORPORATE_EMAIL" ->
                    "Publication refusée (NEED_CORPORATE_EMAIL) : fournissez un e-mail professionnel lié au domaine de l’entreprise.";
            case "WAITING_REGISTRY" ->
                    "Publication refusée (WAITING_REGISTRY) : vérification registre en cours.";
            case "NAME_MISMATCH" ->
                    "Publication refusée (NAME_MISMATCH) : alignez la raison sociale sur le registre ICE.";
            case "PENDING" ->
                    "Publication refusée (PENDING) : complétez ICE + raison sociale + e-mail pro.";
            default ->
                    "Entreprise non vérifiée (" + verificationStatus + ") — inscription libre ≠ publication libre.";
        };
    }

    private double similar(String a, String b) {
        String na = norm(a);
        String nb = norm(b);
        if (na.equals(nb)) return 1.0;
        if (na.contains(nb) || nb.contains(na)) return 0.85;
        var ta = java.util.Set.of(na.split("\\s+"));
        var tb = java.util.Set.of(nb.split("\\s+"));
        long inter = ta.stream().filter(tb::contains).count();
        java.util.HashSet<String> uni = new java.util.HashSet<>(ta);
        uni.addAll(tb);
        return uni.isEmpty() ? 0 : (double) inter / uni.size();
    }

    private String norm(String s) {
        String n = Normalizer.normalize(s, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9\\s]", " ")
                .replaceAll("\\s+", " ")
                .trim();
        return n.replace(" sarl", "").replace(" sa", "").replace(" sas", "").trim();
    }
}
