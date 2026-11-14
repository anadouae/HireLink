package ma.ensat.hirelink.controller;

import ma.ensat.hirelink.model.ExternalJobOffer;
import ma.ensat.hirelink.model.ExternalSyncRun;
import ma.ensat.hirelink.repository.ExternalJobOfferRepository;
import ma.ensat.hirelink.repository.ExternalSyncRunRepository;
import ma.ensat.hirelink.service.CatalogListingIndexer;
import ma.ensat.hirelink.service.ExternalJobSyncFacade;
import ma.ensat.hirelink.service.ExternalSourceCatalog;
import ma.ensat.hirelink.service.external.ExternalBoardDefinition;
import ma.ensat.hirelink.service.external.ExternalBoardRegistry;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Pilotage des sources externes. Ne modifie pas le texte de l'annonce d'origine. */
@RestController
@RequestMapping("/api/v1/admin/external-offers")
@PreAuthorize("hasRole('ADMINISTRATEUR')")
public class ExternalJobAdminController {

    private final ExternalJobOfferRepository offers;
    private final ExternalSyncRunRepository runs;
    private final ExternalSourceCatalog catalog;
    private final ExternalBoardRegistry boards;
    private final ExternalJobSyncFacade sync;
    private final CatalogListingIndexer catalogIndexer;

    public ExternalJobAdminController(ExternalJobOfferRepository offers,
                                      ExternalSyncRunRepository runs,
                                      ExternalSourceCatalog catalog,
                                      ExternalBoardRegistry boards,
                                      ExternalJobSyncFacade sync,
                                      CatalogListingIndexer catalogIndexer) {
        this.offers = offers;
        this.runs = runs;
        this.catalog = catalog;
        this.boards = boards;
        this.sync = sync;
        this.catalogIndexer = catalogIndexer;
    }

    @GetMapping("/status")
    public Map<String, Object> status() {
        Set<String> live = catalog.enabled().stream()
                .map(ExternalSourceCatalog.SourceInfo::code)
                .collect(Collectors.toSet());
        List<ExternalJobOffer> visible = offers.findByAvailableTrueOrderByPublishedAtDesc().stream()
                .filter(o -> !o.isQuarantined())
                .toList();
        long hidden = catalog.all().stream()
                .mapToLong(s -> offers.findBySource(s.code()).stream().filter(ExternalJobOffer::isHiddenByAdmin).count())
                .sum();
        long quarantined = catalog.all().stream()
                .mapToLong(s -> offers.findBySource(s.code()).stream().filter(ExternalJobOffer::isQuarantined).count())
                .sum();

        List<Map<String, Object>> sources = new ArrayList<>();
        for (ExternalSourceCatalog.SourceInfo s : catalog.all()) {
            Map<String, Object> row = catalog.toMap(s);
            row.put("availableCount", offers.findBySource(s.code()).stream().filter(ExternalJobOffer::isAvailable).count());
            row.put("hiddenCount", offers.findBySource(s.code()).stream().filter(ExternalJobOffer::isHiddenByAdmin).count());
            row.put("quarantinedCount", offers.findBySource(s.code()).stream().filter(ExternalJobOffer::isQuarantined).count());
            runs.findFirstBySourceOrderByFinishedAtDesc(s.code()).ifPresent(run -> row.put("last", runOf(run)));
            sources.add(row);
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("enabled", !live.isEmpty());
        body.put("sources", sources);
        body.put("availableCount", visible.size());
        body.put("hiddenCount", hidden);
        body.put("quarantinedCount", quarantined);
        body.put("boards", boards.all().stream().map(this::boardRow).toList());
        body.put("offers", visible.stream().limit(40).map(this::brief).toList());
        if (!sources.isEmpty()) {
            Object last = sources.stream()
                    .filter(s -> s.get("last") != null)
                    .map(s -> s.get("last"))
                    .findFirst()
                    .orElse(null);
            if (last != null) body.put("last", last);
        }
        return body;
    }

    @GetMapping("/boards")
    public List<Map<String, Object>> listBoards() {
        return boards.all().stream().map(this::boardRow).toList();
    }

    @PostMapping("/boards/{provider}/{boardKey}/sync")
    public Map<String, Object> syncBoard(@PathVariable String provider, @PathVariable String boardKey) {
        var report = sync.syncBoard(provider, boardKey);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("report", report);
        return body;
    }

    @PostMapping("/{publicId}/hide")
    public Map<String, Object> hide(@PathVariable String publicId) {
        ExternalJobOffer offer = offers.findById(numericId(publicId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Offre externe introuvable"));
        offer.setHiddenByAdmin(true);
        offer.setAvailable(false);
        offers.save(offer);
        catalogIndexer.syncExternal(offer);
        return Map.of("id", "ext-" + offer.getId(), "hidden", true);
    }

    private Map<String, Object> boardRow(ExternalBoardDefinition b) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("provider", b.provider());
        row.put("boardKey", b.boardKey());
        row.put("companyName", b.companyName());
        row.put("referenceUrl", b.referenceUrl());
        row.put("regionHint", b.regionHint());
        row.put("enabled", b.enabled());
        row.put("attributionNotes", b.attributionNotes());
        row.put("verificationNotes", b.verificationNotes());
        return row;
    }

    private Map<String, Object> brief(ExternalJobOffer offer) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", "ext-" + offer.getId());
        row.put("title", offer.getTitle());
        row.put("companyName", offer.getCompany());
        row.put("source", offer.getSource());
        row.put("sourceLabel", catalog.labelOf(offer.getSource()));
        row.put("boardKey", offer.getBoardKey());
        row.put("urlQuality", offer.getUrlQuality());
        row.put("quarantined", offer.isQuarantined());
        row.put("originUrl", offer.getOriginUrl());
        row.put("applyUrl", offer.getApplyUrl());
        return row;
    }

    private static Map<String, Object> runOf(ExternalSyncRun run) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("source", run.getSource());
        row.put("created", run.getCreatedCount());
        row.put("updated", run.getUpdatedCount());
        row.put("withdrawn", run.getWithdrawnCount());
        row.put("rejected", run.getRejectedCount());
        row.put("duplicates", run.getDuplicateCount());
        row.put("keptPrevious", run.isKeptPrevious());
        row.put("message", run.getMessage());
        row.put("finishedAt", run.getFinishedAt());
        return row;
    }

    static long numericId(String publicId) {
        String raw = publicId == null ? "" : publicId;
        if (raw.startsWith("ext-")) raw = raw.substring(4);
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Identifiant externe illisible");
        }
    }
}