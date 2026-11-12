package ma.ensat.hirelink.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Une passe par heure, seulement si l'import est explicitement activé. */
@Component
@ConditionalOnProperty(name = "hirelink.external-jobs.remoteok.enabled", havingValue = "true")
public class RemoteOkSyncJob {

    private static final Logger log = LoggerFactory.getLogger(RemoteOkSyncJob.class);
    private final RemoteOkClient client;
    private final RemoteOkParser parser = new RemoteOkParser();
    private final ExternalJobImportService imports;

    public RemoteOkSyncJob(RemoteOkClient client, ExternalJobImportService imports) {
        this.client = client;
        this.imports = imports;
    }

    @Scheduled(cron = "${hirelink.external-jobs.remoteok.cron:0 15 * * * *}")
    public void sync() {
        try {
            ExternalJobImportService.SyncReport report = imports.importSnapshot(RemoteOkParser.SOURCE, parser.parse(client.fetch()));
            log.info("Synchronisation Remote OK : {}", report.message());
        } catch (RuntimeException e) {
            log.warn("Synchronisation Remote OK non effectuée ({})", e.getClass().getSimpleName());
        }
    }
}
