package ma.ensat.hirelink.service;

import ma.ensat.hirelink.model.*;
import ma.ensat.hirelink.repository.JobOfferRepository;
import ma.ensat.hirelink.repository.MembershipRepository;
import ma.ensat.hirelink.repository.OrganizationRepository;
import ma.ensat.hirelink.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

@Service
public class OrganizationService {
    private final OrganizationRepository organizationRepository;
    private final MembershipRepository membershipRepository;
    private final UserRepository userRepository;
    private final CompanyVerificationService companyVerificationService;
    private final JobOfferRepository jobOfferRepository;
    private final BillingCatalogService billingCatalogService;
    private final NotificationService notificationService;

    public OrganizationService(OrganizationRepository organizationRepository,
                               MembershipRepository membershipRepository,
                               UserRepository userRepository,
                               CompanyVerificationService companyVerificationService,
                               JobOfferRepository jobOfferRepository,
                               BillingCatalogService billingCatalogService,
                               NotificationService notificationService) {
        this.notificationService = notificationService;
        this.billingCatalogService = billingCatalogService;
        this.organizationRepository = organizationRepository;
        this.membershipRepository = membershipRepository;
        this.userRepository = userRepository;
        this.companyVerificationService = companyVerificationService;
        this.jobOfferRepository = jobOfferRepository;
    }

    @Transactional
    public Organization ensureForRecruiter(User recruiter) {
        return membershipRepository.findByUserIdAndActiveTrue(recruiter.getId()).stream()
                .findFirst()
                .map(m -> {
                    Organization o = m.getOrganization();
                    // force init (open-in-view=false)
                    o.getName();
                    o.getPlanTier();
                    return o;
                })
                .orElseGet(() -> createOwned(recruiter,
                        recruiter.getCompany() != null ? recruiter.getCompany() : "Organisation",
                        recruiter.getCompanyIce()));
    }

    @Transactional
    public Organization createOwned(User owner, String name, String ice) {
        Organization org = new Organization();
        org.setName(name == null || name.isBlank() ? "Organisation" : name.trim());
        org.setIce(ice);
        org.setOwner(owner);
        org.setPlanTier(PlanTier.ACCES);
        org.setVerified(owner.isCompanyVerified());
        org.setVerificationStatus(owner.getVerificationStatus() != null
                ? owner.getVerificationStatus()
                : (owner.isCompanyVerified() ? "REGISTRY_MATCH" : "PENDING"));
        org.setVerificationMethod(owner.isCompanyVerified() ? "AUTO_REGISTRY" : "PENDING");
        if (owner.getCompanyPresentation() != null) org.setPresentation(owner.getCompanyPresentation());
        if (owner.getPreferredLocations() != null) org.setCity(owner.getPreferredLocations());
        companyVerificationService.enrichProfile(org);
        organizationRepository.save(org);
        Membership m = new Membership();
        m.setOrganization(org);
        m.setUser(owner);
        m.setOrgRole(OrgRole.OWNER);
        membershipRepository.save(m);
        return org;
    }

    /**
     * Rattache un compte existant à une organisation (ex. compte seed « recruteur@hirelink.ma »).
     * Idempotent : désactive les autres appartenances et active/crée celle-ci.
     */
    @Transactional
    public Membership joinOrganization(User user, Organization org, OrgRole role) {
        for (Membership other : membershipRepository.findByUserIdAndActiveTrue(user.getId())) {
            if (!other.getOrganization().getId().equals(org.getId())) {
                other.setActive(false);
                membershipRepository.save(other);
            }
        }
        Membership m = membershipRepository.findByOrganizationIdAndUserId(org.getId(), user.getId())
                .orElseGet(() -> {
                    Membership created = new Membership();
                    created.setOrganization(org);
                    created.setUser(user);
                    return created;
                });
        m.setActive(true);
        m.setOrgRole(role);
        if (user.getRole() == Role.RECRUTEUR) {
            user.setPlanTier(org.getPlanTier());
            userRepository.save(user);
        }
        return membershipRepository.save(m);
    }

    /** Change le plan de l'organisation, l'applique à ses recruteurs et émet la facture correspondante. */
    @Transactional
    public BillingInvoice changePlan(Organization org, PlanTier tier, String cycle) {
        org.setPlanTier(tier);
        organizationRepository.save(org);
        for (Membership m : membershipRepository.findByOrganizationIdAndActiveTrue(org.getId())) {
            if (m.getUser().getRole() == Role.RECRUTEUR) {
                m.getUser().setPlanTier(tier);
                userRepository.save(m.getUser());
            }
        }
        return billingCatalogService.issueForPlanChange(org, tier, cycle);
    }

    /** Identifiants des comptes actifs de l'organisation de l'utilisateur (au minimum lui-même). */
    @Transactional(readOnly = true)
    public List<Long> orgMemberIds(User user) {
        List<Long> ids = membershipRepository.findByUserIdAndActiveTrue(user.getId()).stream()
                .findFirst()
                .map(m -> membershipRepository.findByOrganizationIdAndActiveTrue(m.getOrganization().getId()).stream()
                        .map(x -> x.getUser().getId())
                        .toList())
                .orElse(List.of());
        if (ids.isEmpty()) return List.of(user.getId());
        return ids;
    }

    /** Destinataires d'une alerte « dossier » : l'auteur de l'offre + les Owners actifs de son organisation. */
    @Transactional(readOnly = true)
    public Set<Long> offerNotificationTargets(JobOffer offer) {
        Set<Long> ids = new LinkedHashSet<>();
        Long authorId = offer.getRecruiter().getId();
        ids.add(authorId);
        membershipRepository.findByUserIdAndActiveTrue(authorId).stream().findFirst()
                .ifPresent(m -> membershipRepository.findByOrganizationIdAndActiveTrue(m.getOrganization().getId())
                        .stream().filter(x -> x.getOrgRole() == OrgRole.OWNER)
                        .forEach(x -> ids.add(x.getUser().getId())));
        return ids;
    }

    /** L'organisation de l'utilisateur est-elle vérifiée ? (un membre invité hérite du badge de l'organisation). */
    @Transactional(readOnly = true)
    public boolean isOrgVerified(User user) {
        return membershipRepository.findByUserIdAndActiveTrue(user.getId()).stream().findFirst()
                .map(m -> m.getOrganization().isVerified())
                .orElse(false);
    }

    /** Owner/Recruteur de l'organisation peut gérer les offres de ses collègues (pas le Hiring Manager). */
    @Transactional(readOnly = true)
    public boolean canManageOffer(User actor, JobOffer offer) {
        if (actor.getRole() == Role.ADMINISTRATEUR) return true;
        Long authorId = offer.getRecruiter().getId();
        if (authorId.equals(actor.getId())) return true;
        return membershipRepository.findByUserIdAndActiveTrue(actor.getId()).stream().findFirst()
                .filter(m -> m.getOrgRole() != OrgRole.HIRING_MANAGER)
                .flatMap(m -> membershipRepository.findByOrganizationIdAndUserId(m.getOrganization().getId(), authorId))
                .filter(Membership::isActive)
                .isPresent();
    }

    @Transactional(readOnly = true)
    public Membership requireMembership(User user) {
        Membership m = membershipRepository.findByUserIdAndActiveTrue(user.getId()).stream()
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Aucune organisation (R02)"));
        m.getOrganization().getName();
        m.getUser().getEmail();
        return m;
    }

    /** Accès à un dossier : même organisation que l'offre, et un Hiring Manager ne voit que les dossiers qui lui sont assignés (R14). */
    @Transactional(readOnly = true)
    public void assertCanAccessApplication(User actor, ma.ensat.hirelink.model.Application app) {
        if (actor.getRole() == Role.ADMINISTRATEUR) return;
        assertSameOrg(actor, app.getJobOffer());
        Membership m = requireMembership(actor);
        if (m.getOrgRole() == OrgRole.HIRING_MANAGER && !actor.getId().equals(app.getAssignedHmUserId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Dossier non assigné (R14)");
        }
    }

    @Transactional(readOnly = true)
    public void assertSameOrg(User actor, JobOffer offer) {
        if (actor.getRole() == Role.ADMINISTRATEUR) return;
        Membership m = requireMembership(actor);
        // Offre liée au recruteur propriétaire : même org via membership owner/recruiter
        Long offerRecruiterId = offer.getRecruiter().getId();
        boolean same = membershipRepository.findByOrganizationIdAndUserId(m.getOrganization().getId(), offerRecruiterId)
                .filter(Membership::isActive).isPresent()
                || offerRecruiterId.equals(actor.getId());
        if (!same) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Hors organisation (R02)");
        }
        if (m.getOrgRole() == OrgRole.HIRING_MANAGER) {
            // HM ne crée pas d'offre ; actions limitées ailleurs (R14)
        }
    }

    @Transactional
    public Membership invite(User owner, String email, OrgRole role) {
        Membership om = requireMembership(owner);
        if (om.getOrgRole() != OrgRole.OWNER) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Seul l'Owner invite (R13)");
        }
        if (role == OrgRole.OWNER) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Impossible d'inviter un Owner");
        }
        User u = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Aucun compte recruteur avec cet e-mail. La personne doit d’abord créer son compte (rôle Recruteur)"));
        if (u.getRole() != Role.RECRUTEUR) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "L'invité doit être un compte recruteur");
        }
        Organization org = om.getOrganization();
        for (Membership other : membershipRepository.findByUserIdAndActiveTrue(u.getId())) {
            Organization theirs = other.getOrganization();
            if (theirs.getId().equals(org.getId())) continue;
            // Une organisation vide (compte tout juste créé) peut être quittée ; sinon on ne débauche personne.
            boolean alone = membershipRepository.findByOrganizationIdAndActiveTrue(theirs.getId()).size() == 1;
            if (!alone || !jobOfferRepository.findByRecruiterId(u.getId()).isEmpty()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Ce recruteur fait déjà partie de « " + theirs.getName() + " »");
            }
        }
        boolean already = membershipRepository.findByOrganizationIdAndUserId(org.getId(), u.getId())
                .filter(Membership::isActive).isPresent();
        Membership m = joinOrganization(u, org, role);
        if (!already) {
            notificationService.push(u.getId(), "Vous avez rejoint « " + org.getName() + " »",
                    "Rôle : " + (role == OrgRole.HIRING_MANAGER ? "Hiring Manager" : "Recruteur") + ". Retrouvez l'équipe dans votre espace.", "SYSTEM");
        }
        return m;
    }

    @Transactional
    public void removeMember(User owner, Long userId) {
        Membership om = requireMembership(owner);
        if (om.getOrgRole() != OrgRole.OWNER) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Seul l'Owner retire (R13)");
        }
        if (owner.getId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Impossible de se retirer soi-même");
        }
        Membership m = membershipRepository.findByOrganizationIdAndUserId(om.getOrganization().getId(), userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Membre introuvable"));
        m.setActive(false);
        membershipRepository.save(m);
    }

    @Transactional(readOnly = true)
    public List<Membership> listMembers(User user) {
        Membership m = requireMembership(user);
        List<Membership> list = membershipRepository.findByOrganizationIdAndActiveTrue(m.getOrganization().getId());
        list.forEach(x -> {
            x.getUser().getEmail();
            x.getUser().getFullName();
        });
        return list;
    }

    @Transactional
    public Organization updateMine(User user, String name, String ice) {
        return updateMine(user, name, ice, null, null, null, null, null);
    }

    @Transactional
    public Organization updateMine(
            User user,
            String name,
            String ice,
            String website,
            String sector,
            String city,
            String presentation,
            String employeeRange
    ) {
        return updateMine(user, name, ice, website, sector, city, presentation, employeeRange, null);
    }

    @Transactional
    public Organization updateMine(
            User user,
            String name,
            String ice,
            String website,
            String sector,
            String city,
            String presentation,
            String employeeRange,
            String logoUrl
    ) {
        Membership m = requireMembership(user);
        if (m.getOrgRole() != OrgRole.OWNER && m.getOrgRole() != OrgRole.RECRUTEUR) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Modification réservée Owner/Recruteur");
        }
        Organization org = m.getOrganization();
        String previousName = org.getName();
        String previousIce = org.getIce();
        if (name != null && !name.isBlank()) org.setName(name.trim());
        if (ice != null) org.setIce(ice.isBlank() ? null : ice.trim());
        if (website != null) org.setWebsite(website.isBlank() ? null : website.trim());
        if (sector != null) org.setSector(sector.isBlank() ? null : sector.trim());
        if (city != null) org.setCity(city.isBlank() ? null : city.trim());
        if (presentation != null) {
            String bio = presentation.trim();
            org.setPresentation(bio.length() > 1200 ? bio.substring(0, 1200) : bio);
            user.setCompanyPresentation(org.getPresentation());
        }
        if (employeeRange != null) org.setEmployeeRange(employeeRange.isBlank() ? null : employeeRange.trim());
        if (logoUrl != null) {
            String url = logoUrl.trim();
            if (url.isBlank()) {
                org.setLogoUrl(null);
            } else if (url.length() > 400) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "URL du logo trop longue");
            } else if (!(url.startsWith("http://") || url.startsWith("https://") || url.startsWith("/api/v1/media/"))) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "URL du logo invalide");
            } else {
                org.setLogoUrl(url);
            }
        }
        // L'identité légale (nom + ICE) ne se revérifie que si elle change : enregistrer une présentation, un logo ou une
        // ville ne retire jamais le statut « vérifiée » (y compris par l'admin). Changer le nom ou l'ICE relance la vérification.
        boolean identityChanged = !sameText(previousName, org.getName()) || !sameText(previousIce, org.getIce());
        if (identityChanged && org.getIce() != null && !org.getIce().isBlank() && org.getName() != null) {
            var result = companyVerificationService.verify(org.getIce(), org.getName(), user.getId(), user.getEmail());
            companyVerificationService.apply(user, result);
            companyVerificationService.applyToOrg(org, result);
        } else {
            companyVerificationService.enrichProfile(org);
        }
        user.setCompany(org.getName());
        user.setCompanyIce(org.getIce());
        userRepository.save(user);
        return organizationRepository.save(org);
    }

    /** Nom d'entreprise affiché pour une offre : organisation du recruteur, sinon son entreprise déclarée, sinon son nom. */
    public String companyNameOf(JobOffer offer) {
        User recruiter = offer.getRecruiter();
        return membershipRepository.findByUserIdAndActiveTrue(recruiter.getId()).stream()
                .map(m -> m.getOrganization().getName())
                .filter(n -> n != null && !n.isBlank())
                .findFirst()
                .orElseGet(() -> recruiter.getCompany() != null && !recruiter.getCompany().isBlank()
                        ? recruiter.getCompany() : recruiter.getFullName());
    }

    private static boolean sameText(String a, String b) {
        return (a == null ? "" : a.replaceAll("\\s+", "")).equalsIgnoreCase(b == null ? "" : b.replaceAll("\\s+", ""));
    }

    @Transactional
    public Organization setLogoUrl(User user, String logoUrl) {
        Membership m = requireMembership(user);
        if (m.getOrgRole() != OrgRole.OWNER && m.getOrgRole() != OrgRole.RECRUTEUR) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Modification réservée Owner/Recruteur");
        }
        Organization org = m.getOrganization();
        org.setLogoUrl(logoUrl);
        return organizationRepository.save(org);
    }

    @Transactional
    public Organization syncFromRecruiter(User recruiter) {
        Organization org = ensureForRecruiter(recruiter);
        if (recruiter.getCompany() != null && !recruiter.getCompany().isBlank()) org.setName(recruiter.getCompany());
        if (recruiter.getCompanyIce() != null && !recruiter.getCompanyIce().isBlank()) org.setIce(recruiter.getCompanyIce());
        org.setVerified(recruiter.isCompanyVerified());
        org.setVerificationStatus(recruiter.getVerificationStatus() != null
                ? recruiter.getVerificationStatus()
                : (recruiter.isCompanyVerified() ? "REGISTRY_MATCH" : "PENDING"));
        org.setVerificationMethod(recruiter.isCompanyVerified() ? "AUTO_REGISTRY" : "PENDING");
        if (recruiter.getCompanyPresentation() != null) org.setPresentation(recruiter.getCompanyPresentation());
        companyVerificationService.enrichProfile(org);
        return organizationRepository.save(org);
    }

    @Transactional
    public Organization recheck(User user) {
        Organization org = ensureForRecruiter(user);
        if (org.getIce() == null || org.getIce().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Renseignez l’ICE avant de relancer la vérification");
        }
        var result = companyVerificationService.verify(
                org.getIce(),
                org.getName() != null ? org.getName() : user.getCompany(),
                user.getId(),
                user.getEmail()
        );
        companyVerificationService.apply(user, result);
        companyVerificationService.applyToOrg(org, result);
        userRepository.save(user);
        return organizationRepository.save(org);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listPublic() {
        Map<Long, Map<String, Object>> byOrg = new LinkedHashMap<>();
        for (JobOffer offer : jobOfferRepository.findByPublishedTrueAndSuspendedFalse()) {
            Organization org = membershipRepository.findByUserIdAndActiveTrue(offer.getRecruiter().getId()).stream()
                    .map(Membership::getOrganization)
                    .findFirst()
                    .orElse(null);
            String name = org != null && org.getName() != null ? org.getName()
                    : (offer.getRecruiter().getCompany() != null ? offer.getRecruiter().getCompany() : "Entreprise");
            Long key = org != null ? org.getId() : -offer.getRecruiter().getId();
            Map<String, Object> row = byOrg.get(key);
            if (row == null) {
                row = new LinkedHashMap<>();
                row.put("id", org == null ? null : org.getId());
                row.put("name", name);
                row.put("logoUrl", org != null && org.getLogoUrl() != null && !org.getLogoUrl().isBlank()
                        ? org.getLogoUrl()
                        : CompanyDirectory.logoFor(org == null ? null : org.getIce(), name));
                row.put("website", org == null ? "" : nvl(org.getWebsite()));
                row.put("sector", org == null ? "" : nvl(org.getSector()));
                row.put("city", org != null && org.getCity() != null && !org.getCity().isBlank()
                        ? org.getCity()
                        : firstCity(offer.getLocation()));
                row.put("employees", org == null ? "" : nvl(org.getEmployeeRange()));
                row.put("verified", (org != null && org.isVerified()) || offer.getRecruiter().isCompanyVerified());
                row.put("verificationStatus", org == null ? "" : nvl(org.getVerificationStatus()));
                row.put("presentation", org != null && org.getPresentation() != null && !org.getPresentation().isBlank()
                        ? org.getPresentation()
                        : name + " recrute actuellement sur HireLink.");
                row.put("offerCount", 0);
                byOrg.put(key, row);
            }
            row.put("offerCount", ((Number) row.get("offerCount")).intValue() + 1);
            if (row.get("city") == null || String.valueOf(row.get("city")).isBlank()) {
                row.put("city", firstCity(offer.getLocation()));
            }
        }
        return new ArrayList<>(byOrg.values());
    }

    private static String nvl(String value) {
        return value == null ? "" : value;
    }

    private static String firstCity(String location) {
        if (location == null || location.isBlank()) return "";
        return location.split("[·,|/]")[0].trim();
    }
}
