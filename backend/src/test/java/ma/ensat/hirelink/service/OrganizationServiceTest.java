package ma.ensat.hirelink.service;

import ma.ensat.hirelink.model.Membership;
import ma.ensat.hirelink.model.OrgRole;
import ma.ensat.hirelink.model.Organization;
import ma.ensat.hirelink.model.User;
import ma.ensat.hirelink.repository.JobOfferRepository;
import ma.ensat.hirelink.repository.MembershipRepository;
import ma.ensat.hirelink.repository.OrganizationRepository;
import ma.ensat.hirelink.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrganizationServiceTest {

    private CompanyVerificationService verification;
    private OrganizationService service;
    private User owner;
    private Organization org;

    @BeforeEach
    void setUp() {
        OrganizationRepository orgs = mock(OrganizationRepository.class);
        MembershipRepository memberships = mock(MembershipRepository.class);
        UserRepository users = mock(UserRepository.class);
        verification = mock(CompanyVerificationService.class);
        service = new OrganizationService(orgs, memberships, users, verification, mock(JobOfferRepository.class),
                mock(BillingCatalogService.class), mock(NotificationService.class));

        owner = new User();
        owner.setId(7L);
        owner.setEmail("rh@atlas.ma");
        org = new Organization();
        org.setName("Atlas Digital");
        org.setIce("002100000000011");
        org.setVerified(true);
        org.setVerificationStatus("ADMIN_APPROVED");
        Membership m = new Membership();
        m.setOrganization(org);
        m.setUser(owner);
        m.setOrgRole(OrgRole.OWNER);
        when(memberships.findByUserIdAndActiveTrue(anyLong())).thenReturn(List.of(m));
        when(orgs.save(any(Organization.class))).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    void enregistrerLaFicheSansChangerIdentiteGardeLaVerificationAdmin() {
        service.updateMine(owner, "Atlas Digital", "002100000000011", null, null, "Tétouan", "Nouvelle présentation", null);

        verify(verification, never()).verify(anyString(), anyString(), any(), anyString());
        assertTrue(org.isVerified());
        assertEquals("ADMIN_APPROVED", org.getVerificationStatus());
        assertEquals("Tétouan", org.getCity());
    }

    @Test
    void uneIceSaisieAvecDesEspacesNEstPasUnChangement() {
        service.updateMine(owner, "Atlas Digital", "0021 0000 0000 011", null, null, null, null, null);

        verify(verification, never()).verify(anyString(), anyString(), any(), anyString());
        assertEquals("ADMIN_APPROVED", org.getVerificationStatus());
    }

    @Test
    void changerLIceRelanceLaVerification() {
        when(verification.verify(anyString(), anyString(), any(), anyString())).thenReturn(
                new CompanyVerificationService.Result("002100000000099", "Atlas Digital", false, "WAITING_REGISTRY", true, "PENDING", "à revoir"));

        service.updateMine(owner, "Atlas Digital", "002100000000099", null, null, null, null, null);

        verify(verification).verify("002100000000099", "Atlas Digital", 7L, "rh@atlas.ma");
    }
}
