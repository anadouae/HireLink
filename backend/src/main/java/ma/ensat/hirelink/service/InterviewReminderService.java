package ma.ensat.hirelink.service;

import ma.ensat.hirelink.model.Application;
import ma.ensat.hirelink.repository.ApplicationRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

/** Rappel envoyé une seule fois, dans les 24 heures qui précèdent un entretien planifié. */
@Service
public class InterviewReminderService {

    private final ApplicationRepository applications;
    private final EventNotifier notifier;

    public InterviewReminderService(ApplicationRepository applications, EventNotifier notifier) {
        this.applications = applications;
        this.notifier = notifier;
    }

    @Scheduled(fixedDelayString = "${hirelink.interview.reminder-poll-ms:900000}", initialDelay = 60_000)
    @Transactional
    public void remindUpcoming() {
        Instant now = Instant.now();
        for (Application app : applications.findInterviewsToRemind(now, now.plus(Duration.ofHours(24)), InterviewService.CANCELLED)) {
            notifier.interviewReminder(app);
            app.setInterviewReminderSent(true);
        }
    }
}
