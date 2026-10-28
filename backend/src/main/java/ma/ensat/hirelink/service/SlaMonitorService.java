package ma.ensat.hirelink.service;

import ma.ensat.hirelink.model.Application;
import ma.ensat.hirelink.model.ApplicationStatus;
import ma.ensat.hirelink.repository.ApplicationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Alertes SLA pipeline — growth + confiance (CDC fiabilité / délais).
 */
@Service
public class SlaMonitorService {

    private static final Logger log = LoggerFactory.getLogger(SlaMonitorService.class);
    private static final Set<ApplicationStatus> TERMINAL = EnumSet.of(
            ApplicationStatus.INTERVIEW, ApplicationStatus.REJECTED, ApplicationStatus.WITHDRAWN
    );

    private final ApplicationRepository applicationRepository;
    private final NotificationService notificationService;

    public SlaMonitorService(ApplicationRepository applicationRepository,
                             NotificationService notificationService) {
        this.applicationRepository = applicationRepository;
        this.notificationService = notificationService;
    }

    /** Toutes les 30 min — une notification max par dossier (flag sla_breach_notified). */
    @Scheduled(fixedDelayString = "${hirelink.sla.poll-ms:1800000}")
    @Transactional
    public void notifyBreaches() {
        List<Application> breaches = applicationRepository.findSlaBreaches(Instant.now(), TERMINAL);
        for (Application app : breaches) {
            Long recruiterId = app.getJobOffer().getRecruiter().getId();
            String title = "SLA dépassé";
            String body = "« " + app.getJobOffer().getTitle() + " » — "
                    + app.getCandidate().getFullName()
                    + " attend une décision (fiabilité / conversion).";
            notificationService.push(recruiterId, title, body, "SLA");
            if (app.getAssignedHmUserId() != null) {
                notificationService.push(app.getAssignedHmUserId(), title, body, "SLA");
            }
            app.setSlaBreachNotified(true);
            applicationRepository.save(app);
            log.info("SLA breach notified appId={} recruiter={}", app.getId(), recruiterId);
        }
    }
}
