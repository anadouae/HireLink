package ma.ensat.hirelink.service;

import ma.ensat.hirelink.model.*;
import ma.ensat.hirelink.repository.JobOfferRepository;
import ma.ensat.hirelink.repository.MembershipRepository;
import ma.ensat.hirelink.repository.OrganizationInvitationRepository;
import ma.ensat.hirelink.repository.OrganizationRepository;
import ma.ensat.hirelink.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;

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
    private final OrganizationInvitationRepository invitationRepository;
    private final org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;
    private final AuditService audit;

    @org.springframework.beans.factory.annotation.Value("${hirelink.frontend.base-url:http://localhost:5500}")
    private String frontendBase = "http://localhost:5500";

    @org.springframework.beans.factory.annotation.Autowired
    public OrganizationService(OrganizationRepository organizationRepository,
                               MembershipRepository membershipRepository,
                               UserRepository userRepository,
                               CompanyVerificationService companyVerificationService,
                               JobOfferRepository jobOfferRepository,
                               BillingCatalogService billingCatalogService,
                               NotificationService notificationService,
                               OrganizationInvitationRepository invitationRepository,
                               AuditService audit) {
        this(organizationRepository, membershipRepository, userRepository, companyVerificationService, jobOfferRepository, billingCatalogService, notificationService, invitationRepository, new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder(12), audit);
    }

    public OrganizationService(OrganizationRepository organizationRepository,
                               MembershipRepository membershipRepository,
                               UserRepository userRepository,
                               CompanyVerificationService companyVerificationService,
                               JobOfferRepository jobOfferRepository,
                               BillingCatalogService billingCatalogService,
                               NotificationService notificationService,
                               OrganizationInvitationRepository invitationRepository,
                               org.springframework.security.crypto.password.PasswordEncoder passwordEncoder,
                               AuditService audit) {
        this.notificationService = notificationService;
        this.billingCatalogService = billingCatalogService;
        this.organizationRepository = organizationRepository;
        this.membershipRepository = membershipRepository;
        this.userRepository = userRepository;
        this.companyVerificationService = companyVerificationService;
        this.jobOfferRepository = jobOfferRepository;
        this.invitationRepository = invitationRepository;
        this.passwordEncoder = passwordEncoder;
        this.audit = audit;
    }

    /**
     * Espace recruteur d'un compte. Consulter ne crée rien (A2) : un compte dont l'appartenance a été
     * retirée ou mise en pause ne récupère pas un espace OWNER d'un simple GET /organizations/mine, d'un
     * envoi de logo ou d'une modification de profil. La création reste réservée au premier espace d'un
     * compte qui n'a jamais appartenu à une organisation.
     */
    @Transactional
    public Organization ensureForRecruiter(User recruiter) {
        List<Membership> actives = activeMemberships(recruiter);
        if (!actives.isEmpty()) {
            Organization o = actives.get(0).getOrganization();
            // force init (open-in-view=false)
            o.getName();
            o.getPlanTier();
            return o;
        }
        if (!membershipRepository.findAllByUserId(recruiter.getId()).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Votre accès à un espace recruteur a été retiré (R02) : demandez sa réactivation à un administrateur.");
        }
        return createOwned(recruiter,
                recruiter.getCompany() != null ? recruiter.getCompany() : "Organisation",
                recruiter.getCompanyIce());
    }

    @Transactional
    public Organization createOwned(User owner, String name, String ice) {
        Organization org = new Organization();
        org.setName(name == null || name.isBlank() ? "Organisation" : name.trim());
        org.setIce(ice);
        org.setOwner(owner);
        org.setPlanTier(PlanTier.ACCES);
        // A2 : le badge « vérifiée » s'obtient par une vérification portant sur CETTE organisation (ICE
        // contrôlée ou décision d'administration), jamais par héritage du profil de son créateur.
        org.setVerified(false);
        org.setVerificationStatus("PENDING");
        org.setVerificationMethod("PENDING");
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

    /** Verrou de ligne : deux invitations ou deux acceptations ne lisent pas le même nombre de sièges. */
    @Transactional
    public void lockOrganization(Long organizationId) {
        organizationRepository.findByIdForUpdate(organizationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Organisation introuvable"));
    }

    /**
     * Applique une formule déjà payée. N'émet pas de facture et n'est pas un raccourci d'administration.
     */
    @Transactional
    public void applyPaidPlan(Long orgId, PlanTier tier, java.time.Instant endsAt) {
        Organization locked = organizationRepository.findByIdForUpdate(orgId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Organisation introuvable"));
        locked.setPlanTier(tier);
        locked.setSubscriptionEndsAt(endsAt);
        locked.setCancelAtPeriodEnd(false);
        organizationRepository.save(locked);
        for (Membership m : membershipRepository.findByOrganizationIdAndActiveTrue(orgId)) {
            if (m.getUser().getRole() == Role.RECRUTEUR) {
                m.getUser().setPlanTier(tier);
                m.getUser().setSubscriptionEndsAt(endsAt);
                m.getUser().setCancelAtPeriodEnd(false);
                userRepository.save(m.getUser());
            }
        }
    }

    /** Fin de période demandée : retour à la formule gratuite permanente, sans remboursement. */
    @Transactional
    public void downgradeExpired(Long orgId) {
        Organization locked = organizationRepository.findByIdForUpdate(orgId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Organisation introuvable"));
        locked.setPlanTier(PlanTier.ACCES);
        locked.setSubscriptionEndsAt(null);
        locked.setCancelAtPeriodEnd(false);
        organizationRepository.save(locked);
        for (Membership m : membershipRepository.findByOrganizationIdAndActiveTrue(orgId)) {
            if (m.getUser().getRole() == Role.RECRUTEUR) {
                m.getUser().setPlanTier(PlanTier.ACCES);
                m.getUser().setSubscriptionEndsAt(null);
                m.getUser().setCancelAtPeriodEnd(false);
                userRepository.save(m.getUser());
            }
        }
    }

    /** Identifiants des comptes actifs de l'organisation de l'utilisateur (au minimum lui-même). */
    @Transactional(readOnly = true)
    public List<Long> orgMemberIds(User user) {
        List<Long> ids = activeMemberships(user).stream().findFirst()
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
        Organization org = organizationOf(offer);
        if (org != null) {
            membershipRepository.findByOrganizationIdAndActiveTrue(org.getId()).stream()
                    .filter(x -> x.getOrgRole() == OrgRole.OWNER)
                    .forEach(x -> ids.add(x.getUser().getId()));
        }
        return ids;
    }

    /** L'organisation de l'utilisateur est-elle vérifiée ? (un membre invité hérite du badge de l'organisation). */
    @Transactional(readOnly = true)
    public boolean isOrgVerified(User user) {
        return activeMemberships(user).stream().findFirst()
                .map(m -> m.getOrganization().isVerified())
                .orElse(false);
    }

    /**
     * Gérer une offre = appartenir ACTIVEMENT à son organisation, avec un rôle qui publie (pas le
     * Responsable du poste). Être l'auteur historique ne suffit plus (A1) : un recruteur sorti de
     * l'entreprise ne garde aucun droit sur les offres qu'il y a publiées.
     */
    @Transactional(readOnly = true)
    public boolean canManageOffer(User actor, JobOffer offer) {
        if (actor.getRole() == Role.ADMINISTRATEUR) return true;
        Organization org = organizationOf(offer);
        if (org == null) return false;
        return membershipRepository.findByOrganizationIdAndUserId(org.getId(), actor.getId())
                .filter(Membership::isActive)
                .filter(m -> m.getOrgRole() != OrgRole.HIRING_MANAGER)
                .isPresent();
    }

    /** L'organisation gravée sur l'offre ; à défaut, l'appartenance active de son auteur (donnée héritée). */
    @Transactional(readOnly = true)
    public Organization organizationOf(JobOffer offer) {
        Organization direct = offer.getOrganization();
        if (direct != null) {
            direct.getName();
            return direct;
        }
        return activeMemberships(offer.getRecruiter()).stream()
                .findFirst()
                .map(Membership::getOrganization)
                .orElse(null);
    }

    /**
     * L'organisation active d'un compte, ou rien. Utilisé pour graver le propriétaire d'une offre neuve :
     * un administrateur sans appartenance crée une offre sans organisation (il n'a pas d'espace entreprise).
     */
    @Transactional(readOnly = true)
    public Organization currentOrganization(User user) {
        return activeMemberships(user).stream()
                .findFirst()
                .map(m -> {
                    Organization o = m.getOrganization();
                    o.getName();
                    return o;
                })
                .orElse(null);
    }

    /**
     * Appartenances actives d'un compte. Le modèle n'admet qu'un seul espace actif par personne
     * (invitation et réintégration désactivent les précédents) : plus d'une appartenance est une donnée
     * incohérente, pas un choix à faire à l'aveugle.
     */
    @Transactional(readOnly = true)
    public List<Membership> activeMemberships(User user) {
        List<Membership> actives = membershipRepository.findByUserIdAndActiveTrue(user.getId());
        if (actives.size() > 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Appartenances multiples (R02) : signalez cette situation à un administrateur.");
        }
        return actives;
    }

    @Transactional(readOnly = true)
    public Membership requireMembership(User user) {
        Membership m = activeMemberships(user).stream()
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

    /** Comparaison des deux organisations, sans dépendre de qui a écrit l'offre (A1). */
    @Transactional(readOnly = true)
    public void assertSameOrg(User actor, JobOffer offer) {
        if (actor.getRole() == Role.ADMINISTRATEUR) return;
        Membership m = requireMembership(actor);
        Organization org = organizationOf(offer);
        if (org == null || !org.getId().equals(m.getOrganization().getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Hors organisation (R02)");
        }
    }

    @Transactional
    public Membership invite(User owner, String email, OrgRole role) {
        return invite(owner, email, role, true);
    }

    /** Ajoute ou invite un compte recruteur à l'organisation ; si le compte n'existe pas encore, il est provisionné automatiquement. */
    @Transactional
    public Membership invite(User owner, String email, OrgRole role, boolean mail) {
        Membership om = requireMembership(owner);
        if (om.getOrgRole() != OrgRole.OWNER) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Seul le créateur de l'espace peut inviter des membres");
        }
        if (role == OrgRole.OWNER) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Un seul compte crée l'espace : utilisez le transfert de rôle");
        }
        if (role == OrgRole.HIRING_MANAGER) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ce rôle n'est plus proposé. Invitez un recruteur.");
        }
        if (email == null || email.isBlank() || !email.contains("@")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Adresse e-mail invalide");
        }
        String cleanEmail = email.trim().toLowerCase();
        Organization org = om.getOrganization();
        // B6 : le verrou de ligne sur l'entreprise sérialise les invitations simultanées. Sans lui, deux
        // requêtes lues en parallèle voient la même place libre et dépassent le plafond de sièges.
        organizationRepository.findByIdForUpdate(org.getId());
        PlanTier tier = org.getPlanTier() != null ? org.getPlanTier() : PlanTier.ACCES;
        int maxMembers = tier.seatLimit();
        List<Membership> activeMembers = membershipRepository.findByOrganizationIdAndActiveTrue(org.getId());
        boolean alreadyInOrg = activeMembers.stream().anyMatch(m -> m.getUser().getEmail().equalsIgnoreCase(cleanEmail));
        // Même décompte qu'à l'acceptation d'invitation : un compte en pause occupe toujours son siège,
        // sinon une invitation créée ici serait refusée plus tard au moment où le membre essaie d'entrer.
        long seats = membershipRepository.countTeamSeats(org.getId());
        if (!alreadyInOrg && seats >= maxMembers) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Capacité d'équipe atteinte (" + seats + "/" + maxMembers + " sièges). Retirez un compte en pause ou passez à la formule supérieure.");
        }

        boolean wasNew = false;
        User u = userRepository.findByEmailIgnoreCase(cleanEmail).orElse(null);
        if (u == null) {
            wasNew = true;
            u = new User();
            u.setEmail(cleanEmail);
            String local = cleanEmail.split("@")[0].replace(".", " ").replace("_", " ").replace("-", " ");
            String[] words = local.split("\\s+");
            StringBuilder sb = new StringBuilder();
            for (String w : words) {
                if (!w.isBlank()) {
                    sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1)).append(" ");
                }
            }
            String name = sb.toString().trim();
            u.setFullName(name.isBlank() ? cleanEmail : name);
            u.setRole(Role.RECRUTEUR);
            // An invited account must authenticate with the e-mail code first and
            // choose its own password; never provision a shared/default secret.
            u.setPasswordHash(null);
            u.setMustSetPassword(true);
            u.setCompany(org.getName());
            u.setCompanyIce(org.getIce());
            u.setAssignedOrgId(org.getId());
            u.setEmailVerified(true);
            u.setProfileComplete(true);
            u.setPlanTier(tier);
            u = userRepository.save(u);
        } else {
            if (u.getRole() == Role.CANDIDAT) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Cette adresse appartient déjà à un compte candidat. "
                                + "Le candidat doit utiliser une adresse distincte pour rejoindre une équipe recruteur.");
            }
            for (Membership other : membershipRepository.findByUserIdAndActiveTrue(u.getId())) {
                Organization theirs = other.getOrganization();
                if (theirs.getId().equals(org.getId())) continue;
                boolean alone = membershipRepository.findByOrganizationIdAndActiveTrue(theirs.getId()).size() <= 1;
                if (alone && jobOfferRepository.findByRecruiterId(u.getId()).isEmpty()) {
                    other.setActive(false);
                    membershipRepository.save(other);
                    audit.ok("MEMBER_RELEASED", owner, "MEMBERSHIP", other.getId(),
                            cleanEmail + " retiré de « " + theirs.getName() + " » (espace vide) pour rejoindre « " + org.getName() + " »");
                } else {
                    throw new ResponseStatusException(HttpStatus.CONFLICT,
                            "Ce recruteur fait déjà partie de « " + theirs.getName() + " »");
                }
            }
        }

        Membership m = joinOrganization(u, org, role);
        if (!alreadyInOrg) {
            audit.ok("MEMBER_INVITED", owner, "MEMBERSHIP", m.getId(),
                    cleanEmail + " → " + role.libelleFr() + " · « " + org.getName() + " »" + (wasNew ? " · compte provisionné" : ""));
        }
        if (!alreadyInOrg) {
            String roleFr = role.libelleFr();
            String title = "Vous avez rejoint « " + org.getName() + " »";
            String body = "Rôle : " + roleFr + ". Retrouvez l'équipe dans votre espace.";
            if (mail) {
                String rights = "En tant que Recruteur, vous accédez aux offres, au pipeline, aux entretiens et à l'agenda de l'entreprise.";
                String credsInfo = wasNew
                        ? " Utilisez « Connexion par code e-mail » avec cette adresse, puis choisissez votre mot de passe personnel."
                        : " Connectez-vous avec vos identifiants habituels.";
                notificationService.pushAndMail("team", u.getId(), title, body, "SYSTEM", MailService.Kind.ACCOUNT,
                        "vous avez rejoint " + org.getName(),
                        EmailTemplates.Email.of("Bienvenue dans l'équipe " + org.getName(),
                                        owner.getFullName() + " vous a ajouté(e) à l'espace recruteur de « " + org.getName() + " » (rôle : " + roleFr + ").",
                                        rights + credsInfo)
                                .withButton("Ouvrir l'espace équipe", frontendBase.replaceAll("/$", "") + "/app/recruiter-team.html"),
                        null);
            } else {
                notificationService.push("team", u.getId(), title, body, "SYSTEM");
            }
        }
        return m;
    }

    @Transactional
    public void removeMember(User owner, Long userId) {
        Membership m = requireManagedMember(owner, userId);
        if (m.getOrgRole() == OrgRole.OWNER) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Transférez d'abord le rôle de créateur à un autre membre");
        }
        if (isWaiting(m)) {
            // l'invité n'a jamais eu d'accès : on annule l'invitation, il n'y a rien à retirer
            invitationRepository.revokePending(m.getOrganization().getId(), m.getUser().getEmail(), Instant.now());
            audit.ok("INVITATION_REVOKED", owner, "MEMBERSHIP", m.getId(),
                    m.getUser().getEmail() + " · « " + m.getOrganization().getName() + " »");
            notificationService.push("team", m.getUser().getId(),
                    "Invitation annulée — « " + m.getOrganization().getName() + " »",
                    ownerName(owner) + " a annulé l'invitation qui vous était adressée.", "SYSTEM");
            return;
        }
        m.setActive(false);
        m.setSuspendedAt(null); // retiré : ce n'est plus une pause que l'on peut annuler
        membershipRepository.save(m);
        audit.ok("MEMBER_REMOVED", owner, "MEMBERSHIP", m.getId(),
                m.getUser().getEmail() + " (" + m.getOrgRole().libelleFr() + ") retiré de « " + m.getOrganization().getName() + " »");
        notificationService.push("team", m.getUser().getId(),
                "Vous n'êtes plus membre de « " + m.getOrganization().getName() + " »",
                owner.getFullName() + " vous a retiré de l'équipe.", "SYSTEM");
    }

    /** Ligne de la liste d'équipe sans accès en cours et sans pause : l'invité n'a pas encore répondu. */
    private static boolean isWaiting(Membership m) {
        return !m.isActive() && m.getSuspendedAt() == null;
    }

    /** Rôle, pause et transfert supposent un accès réel ; une attente s'annule, elle ne se suspend pas. */
    private static void requireAccepted(Membership m) {
        if (isWaiting(m)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Cette personne n'a pas encore répondu à son invitation : annulez-la plutôt que de changer son accès.");
        }
    }

    /** Le créateur agit sur un autre membre de son propre espace, jamais sur lui-même. */
    @Transactional(readOnly = true)
    protected Membership requireManagedMember(User actor, Long userId) {
        Membership om = requireMembership(actor);
        if (om.getOrgRole() != OrgRole.OWNER) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Seul le créateur de l'espace gère l'équipe (R13)");
        }
        if (actor.getId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Impossible d'agir sur son propre compte ici");
        }
        return membershipRepository.findByOrganizationIdAndUserId(om.getOrganization().getId(), userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Membre introuvable"));
    }

    /** Promotion / rétrogradation d'un membre. Le rôle de créateur ne s'octroie pas ici : il se transfère. */
    @Transactional
    public Membership changeMemberRole(User actor, Long userId, OrgRole newRole) {
        Membership m = requireManagedMember(actor, userId);
        if (newRole == null || newRole == OrgRole.OWNER) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Rôle invalide : utilisez le transfert du rôle de créateur");
        }
        if (newRole == OrgRole.HIRING_MANAGER) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Ce rôle n'est plus attribué. Les membres qui l'ont déjà le conservent.");
        }
        if (m.getOrgRole() == OrgRole.OWNER) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ce compte est le créateur de l'espace : transférez-lui d'abord le rôle");
        }
        requireAccepted(m);
        String previousRole = m.getOrgRole().libelleFr();
        m.setOrgRole(newRole);
        if (!m.isActive()) {
            m.setActive(true);
            m.setSuspendedAt(null); // un rôle nouveau vaut rétablissement : pas de « Responsable du poste » sans accès
        }
        membershipRepository.save(m);
        audit.ok("MEMBER_ROLE_CHANGED", actor, "MEMBERSHIP", m.getId(),
                m.getUser().getEmail() + " · " + previousRole + " → " + newRole.libelleFr());
        notificationService.push("team", m.getUser().getId(),
                "Nouveau rôle chez « " + m.getOrganization().getName() + " »",
                "Vous êtes désormais " + newRole.libelleFr() + ".", "SYSTEM");
        return m;
    }

    /** Mise en pause : le compte garde son rôle et ses dossiers, il perd seulement l'accès, de façon réversible. */
    @Transactional
    public void suspendMember(User actor, Long userId) {
        Membership m = requireManagedMember(actor, userId);
        if (m.getOrgRole() == OrgRole.OWNER) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Le créateur de l'espace ne peut pas être mis en pause");
        }
        requireAccepted(m);
        if (!m.isActive()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ce membre est déjà en pause");
        }
        m.setActive(false);
        m.setSuspendedAt(Instant.now());
        membershipRepository.save(m);
        audit.ok("MEMBER_SUSPENDED", actor, "MEMBERSHIP", m.getId(),
                m.getUser().getEmail() + " (" + m.getOrgRole().libelleFr() + ") · « " + m.getOrganization().getName() + " »");
        notificationService.push("team", m.getUser().getId(),
                "Accès en pause — « " + m.getOrganization().getName() + " »",
                ownerName(actor) + " a mis votre accès en pause. Un créateur peut le rétablir à tout moment.", "SYSTEM");
    }

    @Transactional
    public void reinstateMember(User actor, Long userId) {
        Membership m = requireManagedMember(actor, userId);
        requireAccepted(m);
        if (m.isActive()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ce membre a déjà accès");
        }
        m.setActive(true);
        m.setSuspendedAt(null);
        membershipRepository.save(m);
        audit.ok("MEMBER_REINSTATED", actor, "MEMBERSHIP", m.getId(),
                m.getUser().getEmail() + " (" + m.getOrgRole().libelleFr() + ") · « " + m.getOrganization().getName() + " »");
        notificationService.push("team", m.getUser().getId(),
                "Accès rétabli — « " + m.getOrganization().getName() + " »",
                ownerName(actor) + " vous a redonné l'accès à l'espace.", "SYSTEM");
    }

    /** Un seul compte gouverne l'espace : le transfert déplace le rôle, il n'en crée pas un second. */
    @Transactional
    public Membership transferOwnership(User actor, Long userId) {
        Membership next = requireManagedMember(actor, userId);
        Membership om = requireMembership(actor);
        if (om.getOrgRole() != OrgRole.OWNER) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Seul le créateur de l'espace peut le transférer");
        }
        if (isWaiting(next)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Ce compte n'a pas encore répondu à son invitation : le rôle de créateur ne se transfère qu'à un membre actif.");
        }
        if (!next.isActive()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Rétablissez d'abord l'accès de ce membre");
        }
        om.setOrgRole(OrgRole.RECRUTEUR);
        membershipRepository.save(om);
        next.setOrgRole(OrgRole.OWNER);
        membershipRepository.save(next);
        audit.ok("OWNER_TRANSFERRED", actor, "MEMBERSHIP", next.getId(),
                actor.getEmail() + " → " + next.getUser().getEmail() + " · « " + next.getOrganization().getName() + " »");
        notificationService.pushAndMail("team", next.getUser().getId(),
                "Vous êtes le créateur de « " + next.getOrganization().getName() + " »",
                "Vous gérez l'équipe, la fiche entreprise et la formule.", "SYSTEM", MailService.Kind.ACCOUNT,
                "vous êtes désormais le créateur de " + next.getOrganization().getName(),
                EmailTemplates.Email.of("Vous dirigez l'espace " + next.getOrganization().getName(),
                                actor.getFullName() + " vous a transféré la gestion de l'espace recruteur.",
                                "En tant que Créateur, vous pouvez inviter et retirer des membres, changer leur rôle, "
                                        + "modifier la fiche entreprise et choisir la formule. "
                                        + "Votre propre compte reste le seul à pouvoir transmettre cette responsabilité.")
                        .withButton("Ouvrir l'espace équipe", frontendBase.replaceAll("/$", "") + "/app/recruiter-team.html"),
                null);
        return next;
    }

    private static String ownerName(User actor) {
        return actor.getFullName() == null || actor.getFullName().isBlank() ? "Un administrateur de l'espace" : actor.getFullName();
    }

    /**
     * Sièges occupés au sens de la formule : membres actifs **et** comptes en pause, car un compte en pause
     * peut être rétabli et occupe toujours sa place. C'est le même décompte que celui qui refuse une invitation.
     */
    @Transactional(readOnly = true)
    public int seatCount(Organization org) {
        return (int) membershipRepository.countTeamSeats(org.getId());
    }

    @Transactional(readOnly = true)
    public List<Membership> listMembers(User user) {
        Membership m = requireMembership(user);
        List<Membership> list = membershipRepository.findTeamRows(m.getOrganization().getId());
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
        if (ice != null) {
            if (ice.isBlank()) {
                org.setIce(null);
                org.setIceFormatValid(null);
                org.setIceLegalName(null);
                org.setIceAddress(null);
                org.setIceSource(null);
                org.setIceCheckedAt(null);
            } else {
                // Normalisation stricte 15 chiffres (zéros de tête conservés) via la vérif.
                org.setIce(IceFormatValidator.normalize(ice));
            }
        }
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

    /** Nom d'entreprise affiché pour une offre : organisation de l'offre, sinon l'entreprise déclarée du recruteur, sinon son nom. */
    public String companyNameOf(JobOffer offer) {
        User recruiter = offer.getRecruiter();
        Organization org = organizationOf(offer);
        if (org != null && org.getName() != null && !org.getName().isBlank()) return org.getName();
        return recruiter.getCompany() != null && !recruiter.getCompany().isBlank()
                ? recruiter.getCompany() : recruiter.getFullName();
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

    /**
     * Aligne la fiche entreprise sur les champs saisis par le compte (nom, ICE, présentation).
     * Le badge « vérifiée » n'en fait pas partie (A2) : il se décide par une vérification portant sur
     * l'ICE de CETTE organisation (recheck / décision d'administration), jamais en copiant le drapeau
     * d'un compte sur l'organisation — ni dans un sens ni dans l'autre.
     */
    @Transactional
    public Organization syncFromRecruiter(User recruiter) {
        Organization org = ensureForRecruiter(recruiter);
        if (recruiter.getCompany() != null && !recruiter.getCompany().isBlank()) org.setName(recruiter.getCompany());
        if (recruiter.getCompanyIce() != null && !recruiter.getCompanyIce().isBlank()) org.setIce(recruiter.getCompanyIce());
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
        Map<String, Map<String, Object>> byName = new LinkedHashMap<>();
        Map<String, Integer> liveOffers = new LinkedHashMap<>();
        for (JobOffer offer : jobOfferRepository.findByPublishedTrueAndSuspendedFalse()) {
            // une offre close ou archivée ne fait plus vivre la fiche publique de l'entreprise
            if (offer.getClosedAt() != null || offer.getArchivedAt() != null) continue;
            Organization org = organizationOf(offer);
            String name = org != null && org.getName() != null ? org.getName()
                    : (offer.getRecruiter().getCompany() != null ? offer.getRecruiter().getCompany() : "Entreprise");
            // un recruteur sorti de son équipe n'a plus d'organisation active : son offre porte malgré tout
            // le nom de l'entreprise, saisi dans son compte. Regrouper par nom évite une fiche jumelle sans id.
            String key = name.toLowerCase();
            liveOffers.merge(key, 1, Integer::sum);
            Map<String, Object> row = byName.get(key);
            if (row == null || (row.get("id") == null && org != null)) row = publicSheet(org, offer, name, row);
            if (row.get("city") == null || String.valueOf(row.get("city")).isBlank()) {
                row.put("city", firstCity(offer.getLocation()));
            }
            byName.put(key, row);
        }
        List<Map<String, Object>> sheets = new ArrayList<>(byName.values());
        for (Map<String, Object> sheet : sheets) {
            sheet.put("offerCount", liveOffers.getOrDefault(String.valueOf(sheet.get("name")).toLowerCase(), 0));
        }
        return sheets;
    }

    /**
     * Fiche publique d'une entreprise. {@code previous} est la fiche déjà constituée sous ce nom, souvent par
     * un recruteur sans organisation : ses offres sont comptées, mais ses champs d'identité sont vides.
     */
    private Map<String, Object> publicSheet(Organization org, JobOffer offer, String name, Map<String, Object> previous) {
        Map<String, Object> row = previous == null ? new LinkedHashMap<>() : previous;
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
        return row;
    }

    private static String nvl(String value) {
        return value == null ? "" : value;
    }

    private static String firstCity(String location) {
        if (location == null || location.isBlank()) return "";
        return location.split("[·,|/]")[0].trim();
    }
}
