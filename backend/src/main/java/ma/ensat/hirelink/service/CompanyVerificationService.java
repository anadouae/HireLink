package ma.ensat.hirelink.service;

import ma.ensat.hirelink.model.Organization;
import ma.ensat.hirelink.model.User;
import ma.ensat.hirelink.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.text.Normalizer;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
            String message,
            boolean formatValid,
            Boolean checkDigitAdvisoryOk,
            String legalName,
            String address,
            String city,
            String source,
            java.time.Instant checkedAt
    ) {}

    private final UserRepository userRepository;
    private final IceFormatValidator iceFormatValidator;
    private final IceIdentityLookup iceIdentityLookup;
    private final ma.ensat.hirelink.security.AuthThrottle throttle;

    public CompanyVerificationService(
            UserRepository userRepository,
            IceFormatValidator iceFormatValidator,
            IceIdentityLookup iceIdentityLookup,
            ma.ensat.hirelink.security.AuthThrottle throttle
    ) {
        this.userRepository = userRepository;
        this.iceFormatValidator = iceFormatValidator;
        this.iceIdentityLookup = iceIdentityLookup;
        this.throttle = throttle;
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
        IceFormatValidator.Result format = iceFormatValidator.validate(rawIce);
        if (!format.formatValid()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, format.message());
        }
        String ice = format.normalized();
        String name = companyName.trim();
        java.time.Instant now = java.time.Instant.now();

        // Les membres d'une même entreprise partagent son ICE : conflit seulement si aucun autre compte portant cet ICE n'a la même raison sociale.
        var others = userRepository.findAllByCompanyIce(ice).stream()
                .filter(u -> currentUserId == null || !u.getId().equals(currentUserId)).toList();
        if (!others.isEmpty() && others.stream().noneMatch(u -> similar(u.getCompany() == null ? "" : u.getCompany(), name) >= 0.72)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cet ICE est déjà lié à un autre compte HireLink");
        }
        boolean corporateEmail = isCorporateEmail(email);
        var hit = iceIdentityLookup.lookup(ice);

        if (!corporateEmail) {
            return new Result(ice, name, false, "NEED_CORPORATE_EMAIL", false, "PENDING",
                    "Format ICE valide. Pour le badge automatique, utilisez un e-mail professionnel (@entreprise.ma). "
                            + IceIdentityLookup.manualPortalHint(),
                    true, format.checkDigitAdvisoryOk(),
                    hit.map(IceIdentityLookup.Hit::legalName).orElse(null),
                    hit.map(IceIdentityLookup.Hit::address).orElse(null),
                    hit.map(IceIdentityLookup.Hit::city).orElse(null),
                    hit.map(IceIdentityLookup.Hit::source).orElse(null),
                    now);
        }
        if (hit.isEmpty()) {
            return new Result(ice, name, false, "WAITING_REGISTRY", true, "PENDING",
                    "Format ICE valide. Entreprise non trouvée dans le registre HireLink — "
                            + IceIdentityLookup.manualPortalHint(),
                    true, format.checkDigitAdvisoryOk(),
                    null, null, null, null, now);
        }
        IceIdentityLookup.Hit known = hit.get();
        if (similar(name, known.legalName()) < 0.72) {
            return new Result(ice, name, false, "NAME_MISMATCH", true, "PENDING",
                    "Format ICE valide, mais la raison sociale ne correspond pas à « " + known.legalName()
                            + " ». Corrigez le nom ou attendez la revue admin.",
                    true, format.checkDigitAdvisoryOk(),
                    known.legalName(), known.address(), known.city(), known.source(), known.checkedAt());
        }
        return new Result(ice, known.legalName(), true, "REGISTRY_MATCH", true, "AUTO_REGISTRY",
                "ICE vérifié : entreprise identifiée (« " + known.legalName() + " »).",
                true, format.checkDigitAdvisoryOk(),
                known.legalName(), known.address(), known.city(), known.source(), known.checkedAt());
    }

    /**
     * Contrôle format + tentative d’identité, sans exiger le badge publication.
     * A4 : le référentiel interne n’est pas un annuaire consultable. L’identité d’une entreprise n’est
     * révélée que pour l’ICE déjà enregistré au nom de l’appelant, ou lorsque la raison sociale qu’il
     * déclare correspond déjà à celle du registre — il n’apprend donc rien sur un tiers en parcourant
     * l’espace des ICE. Le format, règle arithmétique publique, reste toujours renvoyé.
     */
    public Map<String, Object> inspectIce(User caller, Organization org, String rawIce, String companyName) {
        // A4 : consultation limitée, par compte et sur fenêtre glissante — un script ne peut pas balayer
        // l'espace des ICE, même avec la règle de révélation ci-dessous.
        throttle.hit("ice-check:" + caller.getId(), 60, 3600,
                "Trop de consultations du registre cette heure. Réessayez plus tard.");
        IceFormatValidator.Result format = iceFormatValidator.validate(rawIce);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("formatValid", format.formatValid());
        out.put("normalized", format.normalized());
        out.put("enterpriseCode", format.enterpriseCode());
        out.put("establishmentCode", format.establishmentCode());
        out.put("checkDigits", format.checkDigits());
        out.put("checkDigitAdvisoryOk", format.checkDigitAdvisoryOk());
        out.put("formatMessage", format.message());
        out.put("manualPortals", List.of(
                Map.of("label", "ice.gov.ma", "url", "https://www.ice.gov.ma/"),
                Map.of("label", "DirectInfo OMPIC", "url", "https://www.directinfo.ma/"),
                Map.of("label", "OMPIC — ICE", "url", "http://www.ompic.ma/fr/content/identifiant-commun-de-lentreprise")
        ));
        out.put("officialApiAvailable", false);
        out.put("officialApiNote",
                "Aucune API REST gratuite et officielle (OMPIC / DirectInfo / ice.gov.ma) n’est documentée pour un usage applicatif. "
                        + "HireLink ne prétend pas interroger ces plateformes en direct.");
        if (!format.formatValid()) {
            out.put("identityFound", false);
            out.put("identityMessage", "Corrigez d’abord le format ICE.");
            out.put("checkedAt", java.time.Instant.now().toString());
            return out;
        }
        var hit = iceIdentityLookup.lookup(format.normalized());
        boolean own = sameIce(caller.getCompanyIce(), format.normalized())
                || sameIce(org == null ? null : org.getIce(), format.normalized());
        boolean declaredByName = hit.isPresent() && companyName != null && !companyName.isBlank()
                && similar(companyName, hit.get().legalName()) >= 0.72;
        if (!own && !declaredByName) {
            out.put("identityFound", false);
            out.put("checkedAt", java.time.Instant.now().toString());
            out.put("identityMessage",
                    "Format valide. Le registre interne n’est consultable que pour l’ICE de votre propre "
                            + "entreprise : indiquez la raison sociale correspondante ou lancez la vérification "
                            + "depuis votre fiche. " + IceIdentityLookup.manualPortalHint());
            return out;
        }
        out.put("identityFound", hit.isPresent());
        out.put("checkedAt", java.time.Instant.now().toString());
        if (hit.isPresent()) {
            IceIdentityLookup.Hit h = hit.get();
            out.put("legalName", h.legalName());
            out.put("address", h.address());
            out.put("city", h.city());
            out.put("sector", h.sector());
            out.put("website", h.website());
            out.put("source", h.source());
            out.put("identityMessage", "ICE vérifié : entreprise identifiée");
            if (companyName != null && !companyName.isBlank() && similar(companyName, h.legalName()) < 0.72) {
                out.put("nameMatch", false);
                out.put("identityMessage", "Entreprise trouvée, mais la raison sociale saisie diffère de « " + h.legalName() + " »");
            } else {
                out.put("nameMatch", true);
            }
        } else {
            out.put("identityMessage",
                    "Format valide, entreprise non confirmée automatiquement. " + IceIdentityLookup.manualPortalHint());
        }
        return out;
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
        org.setIceFormatValid(result.formatValid());
        org.setIceLegalName(result.legalName());
        org.setIceAddress(result.address());
        if (result.city() != null && !result.city().isBlank() && blank(org.getCity())) {
            org.setCity(result.city());
        }
        org.setIceSource(result.source());
        org.setIceCheckedAt(result.checkedAt() != null ? result.checkedAt() : java.time.Instant.now());
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

    /** Deux ICE sont le même lorsqu'ils ont les mêmes 15 chiffres ; un ICE absent ne correspond jamais. */
    private boolean sameIce(String ownIce, String queriedIce) {
        if (blank(ownIce) || blank(queriedIce)) return false;
        return ownIce.replaceAll("\\D", "").equals(queriedIce.replaceAll("\\D", ""));
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
