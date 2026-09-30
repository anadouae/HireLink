package ma.ensat.hirelink.service;

import ma.ensat.hirelink.model.*;
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

    public OrganizationService(OrganizationRepository organizationRepository,
                               MembershipRepository membershipRepository,
                               UserRepository userRepository) {
        this.organizationRepository = organizationRepository;
        this.membershipRepository = membershipRepository;
        this.userRepository = userRepository;
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
}
