package ma.ensat.hirelink.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Actualisation planifiée des boards ATS allowlistés (Greenhouse / Lever / Ashby).
 */
@Component
@ConditionalOnProperty(name = "hirelink.external-jobs.ats.scheduled-sync", havingValue = "true", matchIfMissing = true)
public class AtsBoardsSyncJob {

    private static final Logger log = LoggerFactory.getLogger(AtsBoardsSyncJob.class);

    private final ExternalSourceCatalog catalog;
    private final ExternalJobSyncFacade sync;

    public AtsBoardsSyncJob(ExternalSourceCatalog catalog, ExternalJobSyncFacade sync) {
        this.catalog = catalog;
        this.sync = sync;
    }

    @Scheduled(cron = "${hirelink.external-jobs.ats.cron:0 30 * * * *}")
    public void sync() {
        for (ExternalSourceCatalog.SourceInfo source : catalog.enabled()) {
            String code = source.code();
            if (!ExternalSourceCatalog.GREENHOUSE.equals(code)
                    && !ExternalSourceCatalog.LEVER.equals(code)
                    && !ExternalSourceCatalog.ASHBY.equals(code)) {
                continue;
            }
            try {
                ExternalJobImportService.SyncReport report = sync.syncOne(code);
                log.info("Synchronisation ATS {} : {}", code, report.message());
            } catch (RuntimeException e) {
                log.warn("Synchronisation ATS {} non effectuée ({})", code, e.getMessage());
            }
        }
    }
}
