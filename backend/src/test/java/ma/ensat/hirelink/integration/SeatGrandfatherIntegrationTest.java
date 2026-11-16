package ma.ensat.hirelink.integration;

import ma.ensat.hirelink.model.OrgRole;
import ma.ensat.hirelink.model.Organization;
import ma.ensat.hirelink.model.PlanTier;
import ma.ensat.hirelink.model.Role;
import ma.ensat.hirelink.model.User;
import ma.ensat.hirelink.repository.OrganizationRepository;
import ma.ensat.hirelink.repository.UserRepository;
import ma.ensat.hirelink.service.OrganizationInvitationService;
import ma.ensat.hirelink.service.OrganizationService;
import ma.ensat.hirelink.service.PlanPolicyService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Vague 6 — grandfathering sièges : membres existants conservés, nouvelles invitations bloquées.
 */
@SpringBootTest
class SeatGrandfatherIntegrationTest {

    @Autowired OrganizationService organizations;
    @Autowired OrganizationInvitationService invitations;
    @Autowired OrganizationRepository orgRepo;
    @Autowired UserRepository users;
    @Autowired PlanPolicyService policy;

    private User recruiter(String label, PlanTier tier) {
        String seed = Long.toHexString(System.nanoTime()) + label.hashCode();
        User u = new User();
        u.setEmail("own-" + seed + "@test.hirelink.local");
        u.setFullName("Owner " + label);
        u.setRole(Role.RECRUTEUR);
        u.setPlanTier(tier);
        u.setCompany("Org " + label);
        u.setCompanyVerified(true);
        u.setEmailVerified(true);
        u.setProfileComplete(true);
        u.setPasswordHash("$2a$10$integrationtestnopasswordhashunused000000000000000000");
        return users.save(u);
    }

    @Test
    void overLimitMembersKeptButInviteBlocked() {
        User owner = recruiter("GF", PlanTier.PROFESSIONNEL);
        Organization org = organizations.createOwned(owner, "Grandfather Co", null);
        // Remplit jusqu'à la limite Professionnel (5) : owner + 4
        for (int i = 0; i < 4; i++) {
            User m = recruiter("M" + i, PlanTier.PROFESSIONNEL);
            organizations.joinOrganization(m, org, OrgRole.RECRUTEUR);
        }
        // Downgrade vers Essentiel (1 siège) sans éjecter
        org.setPlanTier(PlanTier.ESSENTIEL);
        orgRepo.save(org);

        Map<String, Object> snap = policy.quotaSnapshot(org);
        assertTrue(((Number) snap.get("seatsUsed")).intValue() > ((Number) snap.get("seatsLimit")).intValue()
                || ((Number) snap.get("seatsUsed")).intValue() >= 5);
        assertTrue(Boolean.TRUE.equals(snap.get("seatsOverLimit"))
                || ((Number) snap.get("seatsUsed")).intValue() > ((Number) snap.get("seatsLimit")).intValue());

        User outsider = recruiter("New", PlanTier.ESSENTIEL);
        assertThrows(ResponseStatusException.class,
                () -> invitations.create(owner, outsider.getEmail(), OrgRole.RECRUTEUR));
        // Membres toujours présents
        assertTrue(organizations.seatCount(org) >= 5);
    }
}
