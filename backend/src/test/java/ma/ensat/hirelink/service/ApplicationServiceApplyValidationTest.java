package ma.ensat.hirelink.service;

import ma.ensat.hirelink.dto.ApiDtos;
import ma.ensat.hirelink.model.JobOffer;
import ma.ensat.hirelink.model.Role;
import ma.ensat.hirelink.model.User;
import ma.ensat.hirelink.repository.ApplicationRepository;
import ma.ensat.hirelink.repository.JobOfferRepository;
import ma.ensat.hirelink.repository.MembershipRepository;
import ma.ensat.hirelink.repository.RefusalReasonRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Non-régression : jobOfferId absent / ≤0 → 400, jamais un 500 JPA. */
class ApplicationServiceApplyValidationTest {

    private JobOfferRepository jobOfferRepository;
    private ApplicationRepository applicationRepository;
    private ApplicationService service;
    private User candidat;

    @BeforeEach
    void setUp() {
        jobOfferRepository = mock(JobOfferRepository.class);
        applicationRepository = mock(ApplicationRepository.class);
        when(applicationRepository.countByCandidateIdAndSubmittedAtAfter(anyLong(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(0L);
        service = new ApplicationService(
                applicationRepository,
                jobOfferRepository,
                mock(SkillMatchingService.class),
                mock(TimelineService.class),
                mock(RefusalReasonRepository.class),
                mock(NotificationService.class),
                mock(OrganizationService.class),
                mock(MembershipRepository.class),
                mock(CvStorage.class),
                mock(AuditService.class),
                mock(InterviewService.class),
                mock(EventNotifier.class),
                mock(ResponsivenessService.class));
        candidat = new User();
        candidat.setId(42L);
        candidat.setRole(Role.CANDIDAT);
        candidat.setFullName("Candidat Test");
        candidat.setEmail("candidat@test.ma");
    }

    @Test
    void apply_nullJobOfferId_returns400() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.apply(candidat, new ApiDtos.ApplyRequest(null, null, null, "bonjour", null, null)));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertTrue(ex.getReason() != null && ex.getReason().contains("jobOfferId"));
        verify(jobOfferRepository, never()).findById(anyLong());
    }

    @Test
    void apply_zeroOrNegativeJobOfferId_returns400() {
        for (long id : new long[]{0L, -1L, -999001L}) {
            ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                    () -> service.apply(candidat, new ApiDtos.ApplyRequest(id, null, null, "bonjour", null, null)));
            assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode(), "id=" + id);
            verify(jobOfferRepository, never()).findById(id);
        }
    }

    @Test
    void apply_unknownNumericOffer_returns404() {
        when(jobOfferRepository.findById(99999999L)).thenReturn(Optional.empty());
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.apply(candidat, new ApiDtos.ApplyRequest(99999999L, null, null, "bonjour", null, null)));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    void apply_unpublishedOffer_returns400() {
        JobOffer offer = new JobOffer();
        offer.setId(13L);
        offer.setPublished(false);
        when(jobOfferRepository.findById(13L)).thenReturn(Optional.of(offer));
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.apply(candidat, new ApiDtos.ApplyRequest(13L, null, null, "bonjour", null, null)));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertTrue(ex.getReason() != null && ex.getReason().toLowerCase().contains("publi"));
    }
}
