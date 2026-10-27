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

    public OrganizationService(OrganizationRepository organizationRepository,
                               MembershipRepository membershipRepository,
                               UserRepository userRepository,
                               CompanyVerificationService companyVerificationService,
                               JobOfferRepository jobOfferRepository) {
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

    @Transactional(readOnly = true)
    public Membership requireMembership(User user) {
        Membership m = membershipRepository.findByUserIdAndActiveTrue(user.getId()).stream()
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Aucune organisation (R02)"));
        m.getOrganization().getName();
        m.getUser().getEmail();
        return m;
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
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Utilisateur introuvable — créez le compte d'abord"));
        if (u.getRole() != Role.RECRUTEUR) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "L'invité doit être un compte recruteur");
        }
        Optional<Membership> existing = membershipRepository.findByOrganizationIdAndUserId(om.getOrganization().getId(), u.getId());
        if (existing.isPresent()) {
            Membership m = existing.get();
            m.setActive(true);
            m.setOrgRole(role);
            return membershipRepository.save(m);
        }
        Membership m = new Membership();
        m.setOrganization(om.getOrganization());
        m.setUser(u);
        m.setOrgRole(role);
        return membershipRepository.save(m);
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
        Membership m = requireMembership(user);
        if (m.getOrgRole() != OrgRole.OWNER && m.getOrgRole() != OrgRole.RECRUTEUR) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Modification réservée Owner/Recruteur");
        }
        Organization org = m.getOrganization();
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
        if (org.getIce() != null && !org.getIce().isBlank() && org.getName() != null) {
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
