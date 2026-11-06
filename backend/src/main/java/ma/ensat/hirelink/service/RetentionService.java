package ma.ensat.hirelink.service;

import ma.ensat.hirelink.repository.AuditLogRepository;
import ma.ensat.hirelink.repository.EmailOtpRepository;
import ma.ensat.hirelink.repository.InAppNotificationRepository;
import ma.ensat.hirelink.repository.PasswordResetTokenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

/**
 * Les tables d'horodatage grossissent sans que personne ne relise le passé : la cloche lue, l'audit
 * ancien et les jetons périmés sont purgés ici une fois par jour. Deux choses sont volontairement
 * intactes : l'historique de décision d'une candidature (export RGPD et timeline) et la consommation
 * de crédits, que l'écran « IA » agrège sur toute sa durée.
 */
@Service
public class RetentionService {

    private static final Logger log = LoggerFactory.getLogger(RetentionService.class);

    private static final Duration NOTIF_LUES = Duration.ofDays(90);
    private static final Duration NOTIF_TOUTES = Duration.ofDays(365);
    private static final Duration AUDIT = Duration.ofDays(365);

    private final InAppNotificationRepository notifications;
    private final AuditLogRepository auditLogs;
    private final EmailOtpRepository otps;
    private final PasswordResetTokenRepository resetTokens;

    public RetentionService(InAppNotificationRepository notifications,
                            AuditLogRepository auditLogs,
                            EmailOtpRepository otps,
                            PasswordResetTokenRepository resetTokens) {
        this.notifications = notifications;
        this.auditLogs = auditLogs;
        this.otps = otps;
        this.resetTokens = resetTokens;
    }

    @Scheduled(cron = "${hirelink.retention.cron:0 15 3 * * *}")
    @Transactional
    public void purge() {
        Instant now = Instant.now();
        int lues = notifications.purgeReadBefore(now.minus(NOTIF_LUES));
        int vieilles = notifications.purgeBefore(now.minus(NOTIF_TOUTES));
        int jetons = otps.purgeUsedOrExpired(now) + resetTokens.purgeOlderThan(now);
        int audit = auditLogs.purgeOlderThan(now.minus(AUDIT));
        if (lues + vieilles + jetons + audit == 0) return;
        log.info("Rétention : {} notifications lues, {} anciennes, {} jetons périmés, {} lignes d'audit supprimés",
                lues, vieilles, jetons, audit);
    }
}
