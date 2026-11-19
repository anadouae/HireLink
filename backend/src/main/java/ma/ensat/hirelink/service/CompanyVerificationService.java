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
 * Vérification employeur, sur le modèle de LinkedIn (pas de registre national) :
 * 1) nom de l'entreprise renseigné ;
 * 2) e-mail professionnel du compte (domaine d'entreprise, pas une messagerie grand public
 *    ni une adresse universitaire) → badge « Entreprise vérifiée » automatique ;
 * 3) ICE facultatif : format contrôlé s'il est saisi, rapproché du référentiel interne
 *    ({@link IceIdentityLookup}) pour compléter la fiche — point d'entrée prévu pour un
 *    fournisseur officiel le jour où il sera branché ;
 * 4) l'administration ne valide jamais à la main : elle peut seulement retirer un badge erroné.
 */
@Service
public class CompanyVerificationService {

    private static final Set<String> FREE_EMAIL_DOMAINS = Set.of(
            "gmail.com", "googlemail.com", "yahoo.com", "yahoo.fr", "hotmail.com",
            "hotmail.fr", "outlook.com", "outlook.fr", "live.com", "icloud.com",
            "proton.me", "protonmail.com", "mail.com", "aol.com", "yandex.com",
            "live.fr", "msn.com", "ymail.com", "gmx.com", "gmx.fr", "laposte.net", "orange.fr",
            "free.fr", "sfr.fr", "menara.ma", "mail.ru", "zoho.com", "tutanota.com", "hey.com"
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
        if (companyName == null || companyName.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nom de l’entreprise obligatoire");
        }
        String name = companyName.trim();
        java.time.Instant now = java.time.Instant.now();

        String ice = null;
        Boolean checkDigitOk = null;
        boolean formatValid = true;
        IceIdentityLookup.Hit known = null;
        if (rawIce != null && !rawIce.isBlank()) {
            IceFormatValidator.Result format = iceFormatValidator.validate(rawIce);
            if (!format.formatValid()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, format.message());
            }
            ice = format.normalized();
            checkDigitOk = format.checkDigitAdvisoryOk();
            // Les membres d'une même entreprise partagent son ICE : conflit seulement si aucun autre compte portant cet ICE n'a la même raison sociale.
            var others = userRepository.findAllByCompanyIce(ice).stream()
                    .filter(u -> currentUserId == null || !u.getId().equals(currentUserId)).toList();
            if (!others.isEmpty() && others.stream().noneMatch(u -> similar(u.getCompany() == null ? "" : u.getCompany(), name) >= 0.72)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Cet ICE est déjà lié à un autre compte HireLink");
            }
            known = iceIdentityLookup.lookup(ice).orElse(null);
        }
        boolean nameMatchesRegistry = known != null && similar(name, known.legalName()) >= 0.72;
        String displayName = nameMatchesRegistry ? known.legalName() : name;

        if (!isCorporateEmail(email)) {
            return new Result(ice, name, false, "NEED_CORPORATE_EMAIL", false, "PENDING",
                    "Pour obtenir le badge, utilisez l’adresse e-mail professionnelle de votre entreprise "
                            + "(par exemple prenom@votre-entreprise.com).",
                    formatValid, checkDigitOk,
                    known != null ? known.legalName() : null,
                    known != null ? known.address() : null,
                    known != null ? known.city() : null,
                    known != null ? known.source() : null,
                    now);
        }
        return new Result(ice, displayName, true, "DOMAIN_EMAIL", true, "AUTO_EMAIL",
                "Entreprise vérifiée grâce à votre adresse e-mail professionnelle.",
                formatValid, checkDigitOk,
                known != null ? known.legalName() : null,
                known != null ? known.address() : null,
                known != null ? known.city() : null,
                known != null ? known.source() : "E-mail professionnel",
                now);
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

    /**
     * Décision d'administration : uniquement le retrait d'un badge erroné. La validation reste automatique
     * (relance de la vérification par l'entreprise).
     */
    public void revokeByAdmin(Organization org) {
        org.setVerified(false);
        org.setVerificationStatus("ADMIN_REVOKED");
        org.setVerificationMethod("ADMIN");
        if (org.getOwner() != null) {
            org.getOwner().setCompanyVerified(false);
            org.getOwner().setVerificationStatus(org.getVerificationStatus());
        }
    }

    public boolean isCorporateEmail(String email) {
        if (email == null || !email.contains("@")) {
            return false;
        }
        String domain = email.substring(email.indexOf('@') + 1).toLowerCase(Locale.ROOT).trim();
        if (domain.isEmpty() || !domain.contains(".") || FREE_EMAIL_DOMAINS.contains(domain)) return false;
        // Adresse universitaire (étudiant, école) : elle ne prouve pas l'appartenance à une entreprise.
        boolean academic = domain.endsWith(".edu") || domain.contains(".ac.") || domain.endsWith(".ac")
                || domain.startsWith("etu.") || domain.contains(".etu.") || domain.startsWith("student.");
        return !academic;
    }

    public String publishBlockReason(String verificationStatus) {
        if (verificationStatus == null || verificationStatus.isBlank()) {
            return "Entreprise non vérifiée : publication impossible pour le moment.";
        }
        return switch (verificationStatus) {
            case "NEED_CORPORATE_EMAIL", "WAITING_REGISTRY", "NAME_MISMATCH" ->
                    "Publication impossible : votre compte doit utiliser l’adresse e-mail professionnelle de l’entreprise. "
                            + "Mettez-la à jour, puis relancez la vérification.";
            case "PENDING" ->
                    "Publication impossible : renseignez le nom de l’entreprise, puis relancez la vérification.";
            default ->
                    "Entreprise non vérifiée : publication impossible pour le moment.";
        };
    }

    /** Libellé affiché partout (admin, espace entreprise) : jamais de code interne. */
    public String statusLabel(String status, boolean verified) {
        if (verified) return "Vérifiée";
        return switch (status == null ? "" : status) {
            case "NEED_CORPORATE_EMAIL", "WAITING_REGISTRY", "NAME_MISMATCH" -> "E-mail professionnel requis";
            case "ADMIN_REVOKED" -> "Vérification retirée";
            default -> "Non vérifiée";
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
