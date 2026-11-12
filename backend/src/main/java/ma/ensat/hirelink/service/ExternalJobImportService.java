package ma.ensat.hirelink.service;

import ma.ensat.hirelink.model.ExternalJobOffer;
import ma.ensat.hirelink.model.ExternalSyncRun;
import ma.ensat.hirelink.repository.ExternalJobOfferRepository;
import ma.ensat.hirelink.repository.ExternalSyncRunRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;

@Service
public class ExternalJobImportService {

    private static final Logger log = LoggerFactory.getLogger(ExternalJobImportService.class);

    /** Le flux Remote OK ne montre qu'une fenêtre récente. Une absence isolée n'est pas une suppression. */
    public static final Duration ABSENCE_BEFORE_HIDE = Duration.ofDays(7);

    public record SyncReport(String source, int created, int updated, int withdrawn, int rejected, int duplicates, boolean keptPrevious, String message) {}

    private final ExternalJobOfferRepository offers;
    private final ExternalSyncRunRepository runs;
    private final ReentrantLock syncLock = new ReentrantLock();

    public ExternalJobImportService(ExternalJobOfferRepository offers, ExternalSyncRunRepository runs) {
        this.offers = offers;
        this.runs = runs;
    }

    /**
     * Un flux vide, illisible ou sans offre acceptable ne retire rien.
     * Une annonce absente d'un flux valide reste visible tant qu'elle a été vue depuis moins de sept jours.
     * Un masquage administrateur n'est pas annulé par la synchronisation.
     */
    @Transactional
    public SyncReport importSnapshot(String source, List<RemoteOkParser.ParsedJob> jobs) {
        if (!syncLock.tryLock()) {
            return new SyncReport(source, 0, 0, 0, 0, 0, true, "Une synchronisation est deja en cours.");
        }
        boolean releaseAfterCommit = false;
        try {
            SyncReport report = importLocked(source, jobs);
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCompletion(int status) {
                        syncLock.unlock();
                    }
                });
                releaseAfterCommit = true;
            }
            return report;
        } finally {
            if (!releaseAfterCommit) syncLock.unlock();
        }
    }

    private SyncReport importLocked(String source, List<RemoteOkParser.ParsedJob> jobs) {
        if (jobs == null || jobs.isEmpty()) {
            log.info("Import {} : flux vide, offres precedentes conservees", source);
            return remember(new SyncReport(source, 0, 0, 0, 0, 0, true, "Flux vide : les offres deja importees sont conservees."));
        }
        Map<String, ExternalJobOffer> existing = new LinkedHashMap<>();
        for (ExternalJobOffer row : offers.findBySource(source)) existing.put(row.getExternalId(), row);
        Set<String> seen = new HashSet<>();
        int created = 0;
        int updated = 0;
        int rejected = 0;
        int duplicates = 0;
        Instant now = Instant.now();
        for (RemoteOkParser.ParsedJob job : jobs) {
            if (!acceptable(job)) {
                rejected++;
                continue;
            }
            boolean duplicate = !seen.add(job.externalId());
            if (duplicate) duplicates++;
            ExternalJobOffer row = existing.get(job.externalId());
            if (row == null) {
                row = new ExternalJobOffer();
                row.setSource(source);
                row.setExternalId(job.externalId());
                if (!duplicate) created++;
            } else if (!duplicate) {
                updated++;
            }
            copy(row, job, now);
            offers.save(row);
            existing.put(job.externalId(), row);
        }
        int withdrawn = 0;
        if (!seen.isEmpty()) withdrawn = hideLongAbsences(source, seen, now);
        if (created == 0 && updated == 0) {
            log.info("Import {} : aucune offre acceptable, offres precedentes conservees", source);
            return remember(new SyncReport(source, 0, 0, 0, rejected, duplicates, true, "Aucune offre acceptable : rien n'a ete retire."));
        }
        log.info("Import {} : creees={}, maj={}, retirees={}, rejetees={}", source, created, updated, withdrawn, rejected);
        return remember(new SyncReport(source, created, updated, withdrawn, rejected, duplicates, false, "Synchronisation termin\u00e9e."));
    }

    private int hideLongAbsences(String source, Set<String> seen, Instant now) {
        Instant limit = now.minus(ABSENCE_BEFORE_HIDE);
        int withdrawn = 0;
        for (ExternalJobOffer row : offers.findBySource(source)) {
            if (seen.contains(row.getExternalId()) || !row.isAvailable() || row.isHiddenByAdmin()) continue;
            Instant seenAt = row.getLastSeenAt() != null ? row.getLastSeenAt() : row.getLastSyncedAt();
            if (seenAt != null && seenAt.isBefore(limit)) {
                row.setAvailable(false);
                offers.save(row);
                withdrawn++;
            }
        }
        return withdrawn;
    }

    private SyncReport remember(SyncReport report) {
        ExternalSyncRun run = new ExternalSyncRun();
        run.setSource(report.source());
        run.setCreatedCount(report.created());
        run.setUpdatedCount(report.updated());
        run.setWithdrawnCount(report.withdrawn());
        run.setRejectedCount(report.rejected());
        run.setDuplicateCount(report.duplicates());
        run.setKeptPrevious(report.keptPrevious());
        run.setMessage(report.message());
        run.setFinishedAt(Instant.now());
        runs.save(run);
        return report;
    }

    private static boolean acceptable(RemoteOkParser.ParsedJob job) {
        return job != null
                && job.externalId() != null && !job.externalId().isBlank()
                && job.title() != null && !job.title().isBlank()
                && job.company() != null && !job.company().isBlank()
                && job.originUrl() != null && job.originUrl().startsWith("https://");
    }

    private static void copy(ExternalJobOffer row, RemoteOkParser.ParsedJob job, Instant now) {
        row.setTitle(job.title());
        row.setCompany(job.company());
        row.setOriginUrl(job.originUrl());
        row.setApplyUrl(job.applyUrl() == null ? job.originUrl() : job.applyUrl());
        row.setLocation(job.location());
        row.setContractLabel(job.contractLabel());
        row.setDescription(job.description());
        row.setSkills(job.skills());
        row.setSalaryMin(job.salaryMin());
        row.setSalaryMax(job.salaryMax());
        row.setPublishedAt(job.publishedAt());
        row.setLastSyncedAt(now);
        row.setLastSeenAt(now);
        row.setAvailable(!row.isHiddenByAdmin());
    }
}
