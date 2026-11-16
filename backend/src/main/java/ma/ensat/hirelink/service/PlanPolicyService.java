package ma.ensat.hirelink.service;

import ma.ensat.hirelink.model.Organization;
import ma.ensat.hirelink.model.PlanTier;
import ma.ensat.hirelink.model.Role;
import ma.ensat.hirelink.model.User;
import ma.ensat.hirelink.repository.JobOfferRepository;
import ma.ensat.hirelink.repository.MembershipRepository;
import ma.ensat.hirelink.repository.OrganizationRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.Map;

/** Quotas serveur Vague 2 : sièges, offres actives, résolution de formule. */
@Service
public class PlanPolicyService {

    private final OrganizationRepository organizations;
    private final MembershipRepository memberships;
    private final JobOfferRepository offers;

    public PlanPolicyService(OrganizationRepository organizations,
                             MembershipRepository memberships,
                             JobOfferRepository offers) {
        this.organizations = organizations;
        this.memberships = memberships;
        this.offers = offers;
    }

    public PlanTier resolveOrgTier(Organization org) {
        if (org == null) return PlanTier.ESSENTIEL;
        return PlanTier.normalize(org.getPlanTier(), Role.RECRUTEUR);
    }

    public PlanTier resolveUserTier(User user) {
        if (user == null) return PlanTier.GRATUIT;
        if (user.getRole() == Role.RECRUTEUR && user.getAssignedOrgId() != null) {
            Organization org = organizations.findById(user.getAssignedOrgId()).orElse(null);
            if (org != null) return resolveOrgTier(org);
        }
        return PlanTier.normalize(user.getPlanTier(), user.getRole() == null ? Role.CANDIDAT : user.getRole());
    }

    public void assertCanInviteSeat(Organization org) {
        PlanTier tier = resolveOrgTier(org);
        int max = tier.seatLimit();
        long seats = memberships.countTeamSeats(org.getId());
        if (seats >= max) {
            throw new ResponseStatusException(HttpStatus.PAYMENT_REQUIRED,
                    "Capacité d’équipe atteinte (" + seats + "/" + max + " sièges sur "
                            + tier.displayLabel() + "). Les membres existants sont conservés ; "
                            + "passez à une formule supérieure pour inviter de nouveaux collègues.");
        }
    }

    public void assertCanPublishOffer(User recruiter, Organization org, Long excludeOfferId) {
        if (recruiter != null && recruiter.getRole() == Role.ADMINISTRATEUR) return;
        if (org == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Publiez depuis un espace entreprise pour appliquer les quotas d’offres.");
        }
        PlanTier tier = resolveOrgTier(org);
        int max = tier.activeOfferLimit();
        long active = excludeOfferId == null
                ? offers.countActiveByOrganizationId(org.getId())
                : offers.countActiveByOrganizationIdExcluding(org.getId(), excludeOfferId);
        if (active >= max) {
            String hint = tier.unlimitedOffers()
                    ? "Garde-fou technique atteint (" + max + " offres actives)."
                    : "Quota d’offres actives atteint (" + active + "/" + max + " sur " + tier.displayLabel() + ").";
            throw new ResponseStatusException(HttpStatus.PAYMENT_REQUIRED,
                    hint + " Dépubliez une offre ou passez à une formule supérieure. "
                            + "Les candidatures déjà reçues ne sont pas limitées.");
        }
    }

    public Map<String, Object> quotaSnapshot(Organization org) {
        PlanTier tier = resolveOrgTier(org);
        long seats = memberships.countTeamSeats(org.getId());
        long active = offers.countActiveByOrganizationId(org.getId());
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("plan", tier.name());
        row.put("planLabel", tier.displayLabel());
        row.put("seatsUsed", seats);
        row.put("seatsLimit", tier.seatLimit());
        row.put("seatsRemaining", Math.max(0, tier.seatLimit() - seats));
        row.put("seatsOverLimit", seats > tier.seatLimit());
        row.put("activeOffers", active);
        row.put("activeOfferLimit", tier.activeOfferLimit());
        row.put("offersUnlimited", tier.unlimitedOffers());
        row.put("receivedApplicationsUnlimited", true);
        return row;
    }
}
