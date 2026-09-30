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
 * Anti-fraude employeur :
 * 1) Format ICE
 * 2) Unicité (un ICE = une organisation)
 * 3) Raison sociale obligatoire + rapprochement registre (mock / API OMPIC à brancher)
 * 4) Statut PENDING tant que non confirmé → pas de publication d'offres
 */
@Service
public class CompanyVerificationService {

    /** Extrait registre (remplacer par API officielle). */
    private static final Map<String, String> REGISTRY = Map.of(
            "001544289000035", "Atlas Digital",
            "001234567000012", "Nova RH",
            "002468135000098", "HireDesk Services"
    );

    public record Result(String ice, String companyName, boolean verified, String status) {}

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
                    "Identifiant entreprise obligatoire");
        }
        if (companyName == null || companyName.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Raison sociale officielle obligatoire");
        }
        String ice = rawIce.replaceAll("\\s+", "");
        if (!ice.matches("\\d{9,15}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Identifiant invalide (9 à 15 chiffres)");
        }

        var existing = userRepository.findByCompanyIce(ice);
        if (existing.isPresent() && (currentUserId == null || !existing.get().getId().equals(currentUserId))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Cet identifiant est déjà lié à un autre compte HireLink");
        }

        String name = companyName.trim();
        String registryName = REGISTRY.get(ice);
        boolean verified = false;
        String status = "PENDING_REVIEW";

        boolean corporateEmail = isCorporateEmail(email);

        if (registryName != null) {
            if (similar(name, registryName) >= 0.72) {
                if (corporateEmail) {
                    verified = true;
                    status = "REGISTRY_MATCH";
                    name = registryName;
                } else {
                    status = "NEED_CORPORATE_EMAIL";
                    name = registryName;
                }
            } else {
                status = "NAME_MISMATCH";
            }
        }

        return new Result(ice, name, verified, status);
    }

    private boolean isCorporateEmail(String email) {
        if (email == null || !email.contains("@")) {
            return true; // seed / admin paths without email still OK
        }
        String domain = email.substring(email.indexOf('@') + 1).toLowerCase(Locale.ROOT);
        return !Set.of(
                "gmail.com", "googlemail.com", "yahoo.com", "yahoo.fr", "hotmail.com",
                "outlook.com", "live.com", "icloud.com", "proton.me", "protonmail.com",
                "mail.com", "aol.com", "yandex.com"
        ).contains(domain);
    }

    private double similar(String a, String b) {
        String na = norm(a);
        String nb = norm(b);
        if (na.equals(nb)) return 1.0;
        if (na.contains(nb) || nb.contains(na)) return 0.85;
        // Jaccard tokens
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
