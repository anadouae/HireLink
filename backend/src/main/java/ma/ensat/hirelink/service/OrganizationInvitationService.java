package ma.ensat.hirelink.service;

import ma.ensat.hirelink.model.*;
import ma.ensat.hirelink.repository.*;
import ma.ensat.hirelink.security.PasswordPolicy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class OrganizationInvitationService {
    private static final long TTL_HOURS = 72;

    private final OrganizationInvitationRepository invitations;
    private final UserRepository users;
    private final MembershipRepository memberships;
    private final OrganizationService organizations;
    private final PasswordEncoder encoder;
    private final MailService mail;
    private final NotificationService notifications;
    private final AuditService audit;
    private final SecureRandom random = new SecureRandom();

    @Value("${hirelink.frontend.base-url:http://localhost:5500}")
    private String frontendBase;

    public OrganizationInvitationService(OrganizationInvitationRepository invitations,
                                         UserRepository users,
                                         MembershipRepository memberships,
                                         OrganizationService organizations,
                                         PasswordEncoder encoder,
                                         MailService mail,
                                         NotificationService notifications,
                                         AuditService audit) {
        this.invitations = invitations;
        this.users = users;
        this.memberships = memberships;
        this.organizations = organizations;
        this.encoder = encoder;
        this.mail = mail;
        this.notifications = notifications;
        this.audit = audit;
    }

    @Transactional
    public Map<String, Object> create(User owner, String emailRaw, OrgRole role) {
        Membership ownerMembership = organizations.requireMembership(owner);
        if (ownerMembership.getOrgRole() != OrgRole.OWNER) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Seul le créateur de l'espace peut inviter des membres");
        }
        if (role == OrgRole.OWNER) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Un seul compte crée l'espace : utilisez le transfert de rôle");
        }
        String email = emailRaw == null ? "" : emailRaw.trim().toLowerCase();
        if (!email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Adresse e-mail invalide");
        }
        Organization org = ownerMembership.getOrganization();
        organizations.lockOrganization(org.getId());
        int maxMembers = (org.getPlanTier() == null ? PlanTier.ACCES : org.getPlanTier()).seatLimit();
        // un compte en pause occupe toujours son siège : il peut être rétabli, ce n'est pas un départ
        long seatCount = memberships.countTeamSeats(org.getId());
        if (seatCount >= maxMembers) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Capacité d'équipe atteinte (" + seatCount + "/" + maxMembers + " sièges). "
                            + "Retirez un compte en pause ou passez à la formule supérieure.");
        }
        User user = users.findByEmailIgnoreCase(email).orElse(null);
        if (user != null && user.getRole() == Role.CANDIDAT) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cette adresse appartient déjà à un compte candidat.");
        }
        if (user != null && user.getRole() == Role.ADMINISTRATEUR) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Un compte administrateur ne peut pas être invité dans une équipe.");
        }
        if (user != null && memberships.findByUserIdAndActiveTrue(user.getId()).stream()
                .anyMatch(m -> !m.getOrganization().getId().equals(org.getId()))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ce recruteur fait déjà partie d'une autre organisation.");
        }
        if (user != null && memberships.findByOrganizationIdAndUserId(org.getId(), user.getId())
                .filter(Membership::isActive).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cette personne appartient déjà à l'organisation.");
        }
        java.util.Optional<Membership> existing = user == null ? java.util.Optional.empty()
                : memberships.findByOrganizationIdAndUserId(org.getId(), user.getId());
        if (existing.filter(Membership::isSuspended).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Ce compte est en pause dans votre espace. Rétablissez son accès depuis la page Équipe plutôt que de le réinviter.");
        }
        if (user == null) {
            user = new User();
            user.setEmail(email);
            user.setFullName(email.substring(0, email.indexOf('@')).replace('.', ' '));
            user.setRole(Role.RECRUTEUR);
            user.setPasswordHash(null);
            user.setMustSetPassword(true);
            user.setEmailVerified(false);
            user.setCompany(org.getName());
            user.setCompanyIce(org.getIce());
            user.setAssignedOrgId(org.getId());
            user.setProfileComplete(true);
            user.setPlanTier(org.getPlanTier());
            user = users.save(user);
        }
        final User invitedUser = user;
        Membership membership = memberships.findByOrganizationIdAndUserId(org.getId(), invitedUser.getId()).orElseGet(() -> {
            Membership created = new Membership();
            created.setOrganization(org);
            created.setUser(invitedUser);
            return created;
        });
        membership.setOrgRole(role);
        membership.setActive(false);
        membership.setAcceptedAt(null);
        memberships.save(membership);

        invitations.revokePending(org.getId(), email, Instant.now());
        byte[] raw = new byte[32];
        random.nextBytes(raw);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        OrganizationInvitation invitation = new OrganizationInvitation();
        invitation.setOrganization(org);
        invitation.setUser(user);
        invitation.setEmail(email);
        invitation.setOrgRole(role);
        invitation.setTokenHash(SessionService.sha256(token));
        invitation.setExpiresAt(Instant.now().plus(Duration.ofHours(TTL_HOURS)));
        invitations.save(invitation);

        String link = frontendBase.replaceAll("/$", "") + "/invitation.html?token=" + token;
        mail.send(email, "HireLink — invitation à rejoindre " + org.getName(),
                "Vous avez été invité(e) à rejoindre « " + org.getName() + " ».\n\n"
                        + "Ouvrez ce lien pour accepter l'invitation et choisir votre mot de passe personnel (valable "
                        + TTL_HOURS + " heures) :\n" + link, true);
        audit.ok("INVITATION_SENT", owner, "MEMBERSHIP", membership.getId(),
                email + " → " + role.libelleFr() + " · « " + org.getName() + " »");
        return Map.of("email", email, "orgRole", role.name(), "roleLabel", role.libelleFr(),
                "status", "INVITATION_SENT", "expiresAt", invitation.getExpiresAt());
    }

    @Transactional(readOnly = true)
    public Map<String, Object> details(String rawToken) {
        OrganizationInvitation invitation = findUsable(rawToken);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("email", invitation.getEmail());
        result.put("organization", invitation.getOrganization().getName());
        result.put("orgRole", invitation.getOrgRole().name());
        result.put("roleLabel", invitation.getOrgRole().libelleFr());
        result.put("expiresAt", invitation.getExpiresAt());
        return result;
    }

    @Transactional
    public void accept(String rawToken, String password) {
        OrganizationInvitation invitation = findUsable(rawToken);
        User user = invitation.getUser();
        PasswordPolicy.validate(password, user.getEmail(), user.getFullName());
        organizations.lockOrganization(invitation.getOrganization().getId());
        Membership membership = memberships.findByOrganizationIdAndUserId(
                invitation.getOrganization().getId(), user.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invitation invalide."));
        boolean alreadySeated = membership.isActive() || membership.getSuspendedAt() != null;
        PlanTier tier = invitation.getOrganization().getPlanTier() == null
                ? PlanTier.ACCES : invitation.getOrganization().getPlanTier();
        if (!alreadySeated && memberships.countTeamSeats(invitation.getOrganization().getId()) >= tier.seatLimit()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Capacité d'équipe atteinte (" + tier.seatLimit() + " sièges). L'invitation ne peut pas être acceptée.");
        }
        membership.setOrgRole(invitation.getOrgRole());
        membership.setActive(true);
        membership.setAcceptedAt(Instant.now());
        memberships.save(membership);
        user.setPasswordHash(encoder.encode(password));
        user.setMustSetPassword(false);
        user.setEmailVerified(true);
        user.setPasswordChangedAt(Instant.now());
        users.save(user);
        invitation.setAcceptedAt(Instant.now());
        invitations.save(invitation);
        audit.ok("MEMBER_JOINED", user, "MEMBERSHIP", membership.getId(),
                user.getEmail() + " a rejoint « " + invitation.getOrganization().getName() + " » comme "
                        + invitation.getOrgRole().libelleFr());
        // le créateur suit ses invitations depuis la page Équipe : il doit savoir quand l'une d'elles aboutit
        memberships.findByOrganizationIdAndActiveTrue(invitation.getOrganization().getId()).stream()
                .filter(m -> m.getOrgRole() == OrgRole.OWNER)
                .findFirst()
                .ifPresent(creator -> notifications.push("team", creator.getUser().getId(),
                        "Invitation acceptée — « " + invitation.getOrganization().getName() + " »",
                        user.getFullName() + " a rejoint l'équipe en tant que " + invitation.getOrgRole().libelleFr() + ".",
                        "SYSTEM"));
    }

    private OrganizationInvitation findUsable(String rawToken) {
        String token = rawToken == null ? "" : rawToken.trim();
        OrganizationInvitation invitation = invitations.findByTokenHash(SessionService.sha256(token)).orElse(null);
        if (invitation == null || invitation.getAcceptedAt() != null || invitation.getRevokedAt() != null
                || invitation.getExpiresAt().isBefore(Instant.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invitation invalide ou expirée.");
        }
        return invitation;
    }
}
