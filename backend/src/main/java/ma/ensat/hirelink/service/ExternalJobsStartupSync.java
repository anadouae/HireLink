package ma.ensat.hirelink.service;

import ma.ensat.hirelink.repository.ExternalJobOfferRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Premiere synchronisation silencieuse des sources activees si le catalogue est vide.
 * Evite une vitrine externe vide apres un redemarrage propre.
 */
@Component
@Order(80)
@ConditionalOnProperty(name = "hirelink.external-jobs.startup-sync", havingValue = "true", matchIfMissing = true)
public class ExternalJobsStartupSync implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ExternalJobsStartupSync.class);

    private final ExternalJobOfferRepository offers;
    private final ExternalSourceCatalog catalog;
    private final ExternalJobSyncFacade sync;

    public ExternalJobsStartupSync(ExternalJobOfferRepository offers,
                                   ExternalSourceCatalog catalog,
                                   ExternalJobSyncFacade sync) {
        this.offers = offers;
        this.catalog = catalog;
        this.sync = sync;
    }

    @Override
    public void run(ApplicationArguments args) {
        for (ExternalSourceCatalog.SourceInfo source : catalog.enabled()) {
            if (!offers.findBySource(source.code()).isEmpty()) continue;
            try {
                ExternalJobImportService.SyncReport report = sync.syncOne(source.code());
                log.info("Demarrage {} : creees={}", source.code(), report.created());
            } catch (Exception e) {
                log.warn("Demarrage {} : import reporte - {}", source.code(), e.getMessage());
            }
        }
    }
}
