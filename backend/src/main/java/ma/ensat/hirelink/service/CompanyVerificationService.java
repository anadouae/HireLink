package ma.ensat.hirelink.service;

import ma.ensat.hirelink.model.Organization;
import ma.ensat.hirelink.model.User;
import ma.ensat.hirelink.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Set;

/**
 * Vérification employeur — même logique que LinkedIn / Indeed :
 * 1) e-mail professionnel (pas Gmail)
 * 2) ICE + raison sociale confrontés au registre
 * 3) si tout matche → badge automatique
 * 4) sinon file d’attente admin (espace Administration)
 */
@Service
public class CompanyVerificationService {

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
            String method,
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
                    "ICE obligatoire pour lancer la vérification");
        }
        if (companyName == null || companyName.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Raison sociale obligatoire");
        }
        String ice = rawIce.replaceAll("\\s+", "");
        if (!ice.matches("\\d{9,15}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ICE invalide (9 à 15 chiffres)");
        }
        String name = companyName.trim();

        // Les membres d'une même entreprise partagent son ICE : conflit seulement si aucun autre compte portant cet ICE n'a la même raison sociale.
        var others = userRepository.findAllByCompanyIce(ice).stream()
                .filter(u -> currentUserId == null || !u.getId().equals(currentUserId)).toList();
        if (!others.isEmpty() && others.stream().noneMatch(u -> similar(u.getCompany() == null ? "" : u.getCompany(), name) >= 0.72)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cet ICE est déjà lié à un autre compte HireLink");
        }
        boolean corporateEmail = isCorporateEmail(email);
        var known = CompanyDirectory.byIce(ice);

        if (!corporateEmail) {
            return new Result(ice, name, false, "NEED_CORPORATE_EMAIL", false, "PENDING",
                    "Compte créé. Pour le badge automatique, utilisez un e-mail professionnel (@entreprise.ma). L’admin peut quand même valider le dossier.");
        }
        if (known.isEmpty()) {
            return new Result(ice, name, false, "WAITING_REGISTRY", true, "PENDING",
                    "E-mail professionnel accepté. ICE inconnu du registre automatique — un admin confirme l’identité.");
        }
        String registryName = known.get().name();
        if (similar(name, registryName) < 0.72) {
            return new Result(ice, name, false, "NAME_MISMATCH", true, "PENDING",
                    "La raison sociale ne correspond pas au registre pour cet ICE. Corrigez le nom ou attendez la revue admin.");
        }
        return new Result(ice, registryName, true, "REGISTRY_MATCH", true, "AUTO_REGISTRY",
                "Entreprise vérifiée automatiquement (ICE + registre + e-mail professionnel). Publication autorisée.");
    }

    public void apply(User user, Result result) {
        if (user == null || result == null) return;
        user.setCompanyIce(result.ice());
        user.setCompany(result.companyName());
        user.setCompanyVerified(result.verified());
        user.setVerificationStatus(result.status());
    }

    public void applyToOrg(Organization org, Result result) {
        if (org == null || result == null) return;
        org.setIce(result.ice());
        org.setName(result.companyName());
        org.setVerified(result.verified());
        org.setVerificationStatus(result.status());
        org.setVerificationMethod(result.method());
        enrichProfile(org);
    }

    public void enrichProfile(Organization org) {
        if (org == null) return;
        CompanyDirectory.byIce(org.getIce())
                .or(() -> CompanyDirectory.byName(org.getName()))
                .ifPresent(entry -> {
                    if (blank(org.getWebsite())) org.setWebsite(entry.website());
                    if (blank(org.getLogoUrl())) org.setLogoUrl(entry.logoUrl());
                    if (blank(org.getSector())) org.setSector(entry.sector());
                    if (blank(org.getCity())) org.setCity(entry.city());
                    if (blank(org.getEmployeeRange())) org.setEmployeeRange(entry.employees());
                    if (blank(org.getName()) || similar(org.getName(), entry.name()) > 0.85) {
                        org.setName(entry.name());
                    }
                });
        if (blank(org.getLogoUrl())) {
            org.setLogoUrl(CompanyDirectory.logoFor(org.getIce(), org.getName()));
        }
    }

    public void markAdminDecision(Organization org, boolean verified) {
        org.setVerified(verified);
        org.setVerificationStatus(verified ? "ADMIN_APPROVED" : "PENDING");
        org.setVerificationMethod(verified ? "ADMIN" : "PENDING");
        if (org.getOwner() != null) {
            org.getOwner().setCompanyVerified(verified);
            org.getOwner().setVerificationStatus(org.getVerificationStatus());
        }
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
                    "Publication refusée : fournissez un e-mail professionnel lié au domaine de l’entreprise, ou attendez la validation admin.";
            case "WAITING_REGISTRY" ->
                    "Publication refusée : vérification registre en cours. Un admin peut valider le dossier.";
            case "NAME_MISMATCH" ->
                    "Publication refusée : alignez la raison sociale sur le registre ICE.";
            case "PENDING" ->
                    "Publication refusée : complétez ICE + raison sociale. Un admin peut aussi valider.";
            default ->
                    "Entreprise non vérifiée (" + verificationStatus + ") — l’inscription ne donne pas le droit de publier.";
        };
    }

    public String statusLabel(String status, boolean verified) {
        if (verified) {
            if ("ADMIN_APPROVED".equals(status)) return "Vérifiée par l’admin";
            return "Vérifiée automatiquement";
        }
        return switch (status == null ? "" : status) {
            case "NEED_CORPORATE_EMAIL" -> "E-mail professionnel requis";
            case "WAITING_REGISTRY" -> "En attente du registre";
            case "NAME_MISMATCH" -> "Nom différent du registre";
            default -> "En attente";
        };
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
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
        String n = Normalizer.normalize(s == null ? "" : s, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9\\s]", " ")
                .replaceAll("\\s+", " ")
                .trim();
        return n.replace(" sarl", "").replace(" sa", "").replace(" sas", "").trim();
    }
}
