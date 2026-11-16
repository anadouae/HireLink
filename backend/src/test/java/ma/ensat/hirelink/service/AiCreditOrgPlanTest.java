package ma.ensat.hirelink.service;

import ma.ensat.hirelink.model.Organization;
import ma.ensat.hirelink.model.PlanTier;
import ma.ensat.hirelink.model.Role;
import ma.ensat.hirelink.model.User;
import ma.ensat.hirelink.repository.AiUsageLogRepository;
import ma.ensat.hirelink.repository.OrganizationRepository;
import ma.ensat.hirelink.repository.UserRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** P1 abonnements : crédits IA suivent le plan org, pas un user désynchronisé. */
class AiCreditOrgPlanTest {

    @Test
    void recruiterUsesOrganizationPlanWhenUserPlanStale() {
        OrganizationRepository orgs = mock(OrganizationRepository.class);
        AiCreditService credits = new AiCreditService(
                mock(AiUsageLogRepository.class),
                mock(BillingCatalogService.class),
                mock(UserRepository.class),
                orgs,
                mock(jakarta.persistence.EntityManager.class));

        Organization org = new Organization();
        org.setId(1L);
        org.setPlanTier(PlanTier.PROFESSIONNEL);
        when(orgs.findById(1L)).thenReturn(Optional.of(org));

        User owner = new User();
        owner.setId(2L);
        owner.setRole(Role.RECRUTEUR);
        owner.setAssignedOrgId(1L);
        owner.setPlanTier(PlanTier.ESSENTIEL); // désynchronisé

        assertEquals(PlanTier.PROFESSIONNEL, credits.resolveTier(owner));
    }

    @Test
    void candidateKeepsPersonalPlan() {
        AiCreditService credits = new AiCreditService(
                mock(AiUsageLogRepository.class),
                mock(BillingCatalogService.class),
                mock(UserRepository.class),
                mock(OrganizationRepository.class),
                mock(jakarta.persistence.EntityManager.class));

        User cand = new User();
        cand.setRole(Role.CANDIDAT);
        cand.setPlanTier(PlanTier.PERFORMANCE);

        assertEquals(PlanTier.PERFORMANCE, credits.resolveTier(cand));
    }
}
