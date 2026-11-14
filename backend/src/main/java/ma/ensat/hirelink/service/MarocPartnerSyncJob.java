package ma.ensat.hirelink.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Synchronisation planifiee du flux partenaires Maroc. */
@Component
@ConditionalOnProperty(name = "hirelink.external-jobs.maroc.enabled", havingValue = "true")
public class MarocPartnerSyncJob {

    private static final Logger log = LoggerFactory.getLogger(MarocPartnerSyncJob.class);

    private final ExternalJobSyncFacade sync;

    public MarocPartnerSyncJob(ExternalJobSyncFacade sync) {
        this.sync = sync;
    }

    @Scheduled(cron = "${hirelink.external-jobs.maroc.cron:0 45 * * * *}")
    public void sync() {
        try {
            ExternalJobImportService.SyncReport report = sync.syncOne(ExternalSourceCatalog.MAROC_EMPLOI);
            log.info("Maroc emploi : creees={}, maj={}, retirees={}", report.created(), report.updated(), report.withdrawn());
        } catch (Exception e) {
            log.warn("Maroc emploi : synchronisation reportee - {}", e.getMessage());
        }
    }
}