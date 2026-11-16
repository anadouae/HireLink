package ma.ensat.hirelink.service;

import ma.ensat.hirelink.model.Application;
import ma.ensat.hirelink.model.ApplicationStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class InterviewPlanningStateTest {

    private final InterviewService svc = new InterviewService();

    @Test
    void interviewWithoutSlotIsToSchedule() {
        Application a = new Application();
        a.setStatus(ApplicationStatus.INTERVIEW);
        assertEquals("TO_SCHEDULE", svc.planningState(a));
        assertEquals("Date non renseignée", svc.when(a));
    }

    @Test
    void scheduledSlot() {
        Application a = new Application();
        a.setStatus(ApplicationStatus.INTERVIEW);
        a.setInterviewAt(Instant.now().plusSeconds(86400));
        a.setInterviewStatus(InterviewService.SCHEDULED);
        a.setInterviewTimeZone("Africa/Casablanca");
        assertEquals("SCHEDULED", svc.planningState(a));
        assertFalse(svc.when(a).contains("1970"));
    }

    @Test
    void cancelledStaysCancelledNotAutoReschedule() {
        Application a = new Application();
        a.setStatus(ApplicationStatus.INTERVIEW);
        a.setInterviewAt(Instant.now().plusSeconds(86400));
        a.setInterviewStatus(InterviewService.CANCELLED);
        assertEquals("CANCELLED", svc.planningState(a));
        assertFalse(svc.isPlanned(a));
        assertFalse(svc.isActive(a));
    }
}
