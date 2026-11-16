package ma.ensat.hirelink.service;

import ma.ensat.hirelink.model.ExternalJobOffer;
import ma.ensat.hirelink.model.ExternalSyncRun;
import ma.ensat.hirelink.repository.ExternalJobOfferRepository;
import ma.ensat.hirelink.repository.ExternalSyncRunRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;

@Service
public class ExternalJobImportService {

    private static final Logger log = LoggerFactory.getLogger(ExternalJobImportService.class);

    /** Le flux Remote OK ne montre qu'une fenetre recente. Une absence isolee n'est pas une suppression. */
    public static final Duration ABSENCE_BEFORE_HIDE = Duration.ofDays(7);

    public record SyncReport(
            String source,
            int created,
            int updated,
            int withdrawn,
            int rejected,
            int duplicates,
            int quarantined,
            boolean keptPrevious,
            String message
    ) {}

    private final ExternalJobOfferRepository offers;
    private final ExternalSyncRunRepository runs;
    private final UrlQualityService urlQuality;
    private final boolean enforceUrlQuality;
    private final int maxAgeDays;
    private final CatalogListingIndexer catalogIndexer;
    private final ReentrantLock syncLock = new ReentrantLock();

    public ExternalJobImportService(
            ExternalJobOfferRepository offers,
            ExternalSyncRunRepository runs,
            UrlQualityService urlQuality,
            CatalogListingIndexer catalogIndexer,
            @Value("${hirelink.external-jobs.url-quality.enforce:true}") boolean enforceUrlQuality,
            @Value("${hirelink.external-jobs.url-quality.max-age-days:180}") int maxAgeDays) {
        this.offers = offers;
        this.runs = runs;
        this.urlQuality = urlQuality;
        this.catalogIndexer = catalogIndexer;
        this.enforceUrlQuality = enforceUrlQuality;
        this.maxAgeDays = Math.max(1, maxAgeDays);
    }

    /**
     * Un flux vide, illisible ou sans offre acceptable ne retire rien.
     * Une annonce absente d'un flux valide reste visible tant qu'elle a ete vue depuis moins de sept jours.
     * Un masquage administrateur n'est pas annule par la synchronisation.
     * Les offres suspectes sont mises en quarantaine (conservees), jamais effacees ici.
     */
    @Transactional
    public SyncReport importSnapshot(String source, List<ParsedExternalJob> jobs) {
        if (!syncLock.tryLock()) {
            return new SyncReport(source, 0, 0, 0, 0, 0, 0, true, "Une synchronisation est deja en cours.");
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

    private SyncReport importLocked(String source, List<ParsedExternalJob> jobs) {
        if (jobs == null || jobs.isEmpty()) {
            log.info("Import {} : flux vide, offres precedentes conservees", source);
            return remember(new SyncReport(source, 0, 0, 0, 0, 0, 0, true, "Flux vide : les offres deja importees sont conservees."));
        }
        Map<String, ExternalJobOffer> existing = new LinkedHashMap<>();
        for (ExternalJobOffer row : offers.findBySource(source)) existing.put(row.getExternalId(), row);
        Set<String> seen = new HashSet<>();
        int created = 0;
        int updated = 0;
        int rejected = 0;
        int duplicates = 0;
        int quarantined = 0;
        Instant now = Instant.now();
        for (ParsedExternalJob job : jobs) {
            if (!structurallyAcceptable(job)) {
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
            if (row.isQuarantined()) quarantined++;
            offers.save(row);
            catalogIndexer.syncExternal(row);
            existing.put(job.externalId(), row);
        }
        int withdrawn = 0;
        if (!seen.isEmpty()) withdrawn = hideLongAbsences(source, seen, now);
        if (created == 0 && updated == 0) {
            log.info("Import {} : aucune offre acceptable, offres precedentes conservees", source);
            return remember(new SyncReport(source, 0, 0, 0, rejected, duplicates, quarantined, true, "Aucune offre acceptable : rien n'a ete retire."));
        }
        log.info("Import {} : creees={}, maj={}, retirees={}, rejetees={}, quarantaine={}",
                source, created, updated, withdrawn, rejected, quarantined);
        return remember(new SyncReport(source, created, updated, withdrawn, rejected, duplicates, quarantined, false, "Synchronisation termin\u00e9e."));
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
                catalogIndexer.syncExternal(row);
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
        String msg = report.message();
        if (report.quarantined() > 0) {
            String note = " quarantaine=" + report.quarantined();
            msg = msg.length() + note.length() <= 300 ? msg + note : msg;
        }
        run.setMessage(msg);
        run.setFinishedAt(Instant.now());
        runs.save(run);
        return report;
    }

    /** Identite minimale : pas de validation " l'annonce existe encore ". */
    private static boolean structurallyAcceptable(ParsedExternalJob job) {
        return job != null
                && job.externalId() != null && !job.externalId().isBlank()
                && job.title() != null && !job.title().isBlank()
                && job.company() != null && !job.company().isBlank()
                && job.originUrl() != null && !job.originUrl().isBlank();
    }

    private void copy(ExternalJobOffer row, ParsedExternalJob job, Instant now) {
        UrlQualityService.Assessment origin = urlQuality.classifyOrigin(job.originUrl());
        String applyRaw = blankToNull(job.applyUrl());
        // Ne jamais inventer un lien de candidature a partir de l'origine.
        if (applyRaw != null) {
            UrlQualityService.Assessment apply = urlQuality.classifyApply(applyRaw);
            if (apply.quality() == UrlQuality.INVALID) {
                applyRaw = null;
            }
        }

        row.setTitle(job.title());
        row.setCompany(job.company());
        row.setOriginUrl(job.originUrl().trim());
        row.setApplyUrl(applyRaw);
        row.setBoardKey(blankToNull(job.boardKey()));
        row.setCountryCode(normalizeIsoCountry(job.countryCode()));
        row.setUrlQuality(origin.quality().name());
        row.setUrlQualityReason(clip(origin.reason(), 240));
        row.setLocation(job.location());
        row.setContractLabel(job.contractLabel());
        row.setDescription(job.description());
        row.setSkills(job.skills());
        row.setSalaryMin(job.salaryMin());
        row.setSalaryMax(job.salaryMax());
        row.setPublishedAt(job.publishedAt());
        row.setLastSyncedAt(now);
        row.setLastSeenAt(now);
        row.setContentHash(hashOf(job));

        String quarantineWhy = quarantineReason(origin, job, now);
        if (enforceUrlQuality && quarantineWhy != null) {
            row.setQuarantined(true);
            row.setQuarantineReason(clip(quarantineWhy, 240));
            row.setAvailable(false);
        } else if (!row.isHiddenByAdmin()) {
            row.setQuarantined(false);
            row.setQuarantineReason(null);
            row.setAvailable(origin.quality().isPubliclyListable());
        } else {
            row.setAvailable(false);
        }
    }

    private String quarantineReason(UrlQualityService.Assessment origin, ParsedExternalJob job, Instant now) {
        if (origin.quality().isQuarantineCandidate()) {
            return origin.quality().name() + ": " + origin.reason();
        }
        if (job.publishedAt() != null && job.publishedAt().isBefore(now.minus(Duration.ofDays(maxAgeDays)))) {
            return "EXPIRED: publication anterieure a " + maxAgeDays + " jours (presomption, non preuve HTTP)";
        }
        return null;
    }

    private static String hashOf(ParsedExternalJob job) {
        String raw = String.join("|",
                nullToEmpty(job.externalId()),
                nullToEmpty(job.title()),
                nullToEmpty(job.company()),
                nullToEmpty(job.originUrl()),
                nullToEmpty(job.applyUrl()));
        try {
            byte[] dig = MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(dig);
        } catch (Exception e) {
            return null;
        }
    }

    /** ISO-3166 alpha-2 uniquement (colonne country_code VARCHAR(8)). */
    private static String normalizeIsoCountry(String raw) {
        if (raw == null) return null;
        String t = raw.trim();
        if (t.isEmpty()) return null;
        if (t.length() <= 3 && t.chars().allMatch(Character::isLetter)) {
            return t.toUpperCase(java.util.Locale.ROOT);
        }
        String fold = TextMatcher.fold(t);
        if (fold.contains("united states") || fold.equals("usa") || fold.equals("us")) return "US";
        if (fold.contains("united kingdom") || fold.equals("uk") || fold.equals("great britain")) return "GB";
        if (fold.contains("france")) return "FR";
        if (fold.contains("germany") || fold.contains("deutschland")) return "DE";
        if (fold.contains("canada")) return "CA";
        if (fold.contains("morocco") || fold.contains("maroc")) return "MA";
        if (fold.contains("netherlands") || fold.contains("holland")) return "NL";
        if (fold.contains("spain") || fold.contains("espagne")) return "ES";
        if (fold.contains("belgium") || fold.contains("belgique")) return "BE";
        if (fold.contains("switzerland") || fold.contains("suisse")) return "CH";
        if (fold.contains("ireland")) return "IE";
        if (fold.contains("australia")) return "AU";
        if (fold.contains("india")) return "IN";
        return null;
    }

    private static String blankToNull(String value) {
        if (value == null) return null;
        String t = value.trim();
        return t.isEmpty() ? null : t;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String clip(String value, int max) {
        if (value == null) return null;
        return value.length() <= max ? value : value.substring(0, max);
    }
}
