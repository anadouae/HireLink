package ma.ensat.hirelink.service;

import ma.ensat.hirelink.model.JobOffer;
import ma.ensat.hirelink.model.Membership;
import ma.ensat.hirelink.model.Organization;
import ma.ensat.hirelink.model.User;
import ma.ensat.hirelink.repository.JobOfferRepository;
import ma.ensat.hirelink.repository.MembershipRepository;
import ma.ensat.hirelink.repository.OrganizationRepository;
import ma.ensat.hirelink.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Désactivation, réactivation et retrait d'une entreprise par l'administration.
 * Rien n'est supprimé : les comptes des membres sont suspendus (sessions fermées) et les offres
 * publiées sont retirées de la vitrine. La réactivation rétablit exactement ce que la désactivation
 * avait mis en pause. Le retrait est définitif côté interface ; les données (factures, historique) restent.
 */
@Service
public class AdminOrganizationService {

    public static final String DISABLED = "DISABLED";
    public static final String REMOVED = "REMOVED";

    private final OrganizationRepository organizations;
    private final MembershipRepository memberships;
    private final JobOfferRepository offers;
    private final UserRepository users;
    private final SessionService sessions;
    private final AuditService audit;

    public AdminOrganizationService(OrganizationRepository organizations, MembershipRepository memberships,
                                    JobOfferRepository offers, UserRepository users, SessionService sessions, AuditService audit) {
        this.organizations = organizations;
        this.memberships = memberships;
        this.offers = offers;
        this.users = users;
        this.sessions = sessions;
        this.audit = audit;
    }

    @Transactional
    public Organization deactivate(User admin, Long id) {
        Organization org = require(id);
        if (org.isRemoved()) throw new ResponseStatusException(HttpStatus.CONFLICT, "Cette entreprise a déjà été retirée.");
        if (DISABLED.equals(org.getAccessStatus())) return org;
        pause(org, admin, "ORG_DISABLED");
        org.setAccessStatus(DISABLED);
        org.setAccessChangedAt(Instant.now());
        audit.ok("ORG_DEACTIVATED", admin, "ORGANIZATION", org.getId(), org.getName());
        return organizations.save(org);
    }

    @Transactional
    public Organization reactivate(User admin, Long id) {
        Organization org = require(id);
        if (org.isRemoved()) throw new ResponseStatusException(HttpStatus.CONFLICT, "Une entreprise retirée ne peut pas être réactivée.");
        if (org.isAccessActive()) return org;
        for (Long uid : ids(org.getPausedMemberIds())) {
            users.findById(uid).ifPresent(u -> {
                if (!u.isActive()) {
                    u.setActive(true);
                    users.save(u);
                }
            });
        }
        for (Long oid : ids(org.getPausedOfferIds())) {
            offers.findById(oid).ifPresent(o -> {
                if (!o.isPublished() && !o.isSuspended() && o.getArchivedAt() == null && o.getClosedAt() == null) {
                    o.setPublished(true);
                    offers.save(o);
                }
            });
        }
        org.setPausedMemberIds(null);
        org.setPausedOfferIds(null);
        org.setAccessStatus(null);
        org.setAccessChangedAt(Instant.now());
        audit.ok("ORG_REACTIVATED", admin, "ORGANIZATION", org.getId(), org.getName());
        return organizations.save(org);
    }

    @Transactional
    public Organization remove(User admin, Long id) {
        Organization org = require(id);
        if (org.isRemoved()) return org;
        if (org.isAccessActive()) pause(org, admin, "ORG_REMOVED");
        org.setAccessStatus(REMOVED);
        org.setAccessChangedAt(Instant.now());
        org.setVerified(false);
        audit.ok("ORG_REMOVED", admin, "ORGANIZATION", org.getId(), org.getName());
        return organizations.save(org);
    }

    /** Déconnecte un compte de tous ses appareils. */
    @Transactional
    public int revokeSessions(User admin, Long userId) {
        User u = users.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Compte introuvable"));
        if (u.getId().equals(admin.getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Utilisez « Se déconnecter partout » depuis votre propre compte.");
        }
        int n = sessions.revokeAll(u.getId(), "ADMIN_REVOKE_SESSIONS", null);
        audit.ok("ADMIN_REVOKE_SESSIONS", admin, "USER", u.getId(), u.getEmail() + " · " + n + " session(s)");
        return n;
    }

    private void pause(Organization org, User admin, String reason) {
        Set<Long> pausedUsers = new LinkedHashSet<>(ids(org.getPausedMemberIds()));
        Set<Long> memberIds = new LinkedHashSet<>();
        for (Membership m : memberships.findByOrganizationIdAndActiveTrue(org.getId())) {
            User u = m.getUser();
            memberIds.add(u.getId());
            if (u.getId().equals(admin.getId())) continue;
            if (u.isActive()) {
                u.setActive(false);
                users.save(u);
                pausedUsers.add(u.getId());
            }
            sessions.revokeAll(u.getId(), reason, null);
        }
        Set<Long> pausedOffers = new LinkedHashSet<>(ids(org.getPausedOfferIds()));
        List<JobOffer> list = new ArrayList<>(offers.findByOrganizationId(org.getId()));
        if (!memberIds.isEmpty()) list.addAll(offers.findByRecruiterIdIn(memberIds));
        for (JobOffer o : list) {
            if (o.isPublished() && !pausedOffers.contains(o.getId())) {
                o.setPublished(false);
                offers.save(o);
                pausedOffers.add(o.getId());
            }
        }
        org.setPausedMemberIds(join(pausedUsers));
        org.setPausedOfferIds(join(pausedOffers));
    }

    private Organization require(Long id) {
        return organizations.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Entreprise introuvable"));
    }

    static List<Long> ids(String raw) {
        if (raw == null || raw.isBlank()) return List.of();
        return Arrays.stream(raw.split(","))
                .map(String::trim).filter(s -> s.matches("\\d+")).map(Long::valueOf).toList();
    }

    static String join(Set<Long> ids) {
        return ids.isEmpty() ? null : ids.stream().map(String::valueOf).collect(Collectors.joining(","));
    }

    /** Libellé d'état pour l'administration. */
    public static String accessLabel(Organization org) {
        if (org.isRemoved()) return "Retirée";
        if (!org.isAccessActive()) return "Désactivée";
        return "Active";
    }
}
