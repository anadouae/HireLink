package ma.ensat.hirelink.service;

import ma.ensat.hirelink.model.Application;
import ma.ensat.hirelink.repository.ApplicationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

/** Rappel envoyé une seule fois, dans les 24 heures qui précèdent un entretien planifié. */
@Service
public class InterviewReminderService {

    private static final Logger log = LoggerFactory.getLogger(InterviewReminderService.class);

    private final ApplicationRepository applications;
    private final EventNotifier notifier;

    /** Une réservation non aboutie plus ancienne que cette fenêtre est reprise par un autre cycle. */
    @Value("${hirelink.interview.reminder-claim-timeout-minutes:10}")
    private long claimTimeoutMinutes = 10;

    public InterviewReminderService(ApplicationRepository applications, EventNotifier notifier) {
        this.applications = applications;
        this.notifier = notifier;
    }

    @Scheduled(fixedDelayString = "${hirelink.interview.reminder-poll-ms:900000}",
            initialDelayString = "${hirelink.interview.reminder-initial-delay-ms:60000}")
    @Transactional
    public void remindUpcoming() {
        Instant now = Instant.now();
        Instant staleBefore = now.minus(Duration.ofMinutes(claimTimeoutMinutes));
        for (Application app : applications.findInterviewsToRemind(now, now.plus(Duration.ofHours(24)), InterviewService.CANCELLED)) {
            if (applications.claimInterviewReminder(app.getId(), now, staleBefore) != 1) {
                continue;
            }
            app.setInterviewReminderClaimedAt(now);
            try {
                notifier.interviewReminder(app);
                app.setInterviewReminderSent(true);
            } catch (RuntimeException e) {
                applications.releaseInterviewReminder(app.getId());
                app.setInterviewReminderClaimedAt(null);
                log.warn("Rappel J-1 échoué pour la candidature {} — réessai au cycle suivant : {}", app.getId(), e.toString());
            }
        }
    }
}
