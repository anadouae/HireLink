package ma.ensat.hirelink.controller;

import ma.ensat.hirelink.model.ExternalJobOffer;
import ma.ensat.hirelink.model.ExternalSyncRun;
import ma.ensat.hirelink.repository.ExternalJobOfferRepository;
import ma.ensat.hirelink.repository.ExternalSyncRunRepository;
import ma.ensat.hirelink.service.RemoteOkParser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Pilotage de la source externe. Ne modifie pas le texte de l'annonce d'origine. */
@RestController
@RequestMapping("/api/v1/admin/external-offers")
@PreAuthorize("hasRole('ADMINISTRATEUR')")
public class ExternalJobAdminController {

    private final ExternalJobOfferRepository offers;
    private final ExternalSyncRunRepository runs;
    private final boolean enabled;

    public ExternalJobAdminController(ExternalJobOfferRepository offers,
                                      ExternalSyncRunRepository runs,
                                      @Value("${hirelink.external-jobs.remoteok.enabled:false}") boolean enabled) {
        this.offers = offers;
        this.runs = runs;
        this.enabled = enabled;
    }

    @GetMapping("/status")
    public Map<String, Object> status() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("enabled", enabled);
        body.put("source", RemoteOkParser.SOURCE);
        body.put("sourceLabel", RemoteOkParser.SOURCE_LABEL);
        body.put("availableCount", offers.findByAvailableTrueOrderByPublishedAtDesc().size());
        body.put("hiddenCount", offers.findBySource(RemoteOkParser.SOURCE).stream().filter(ExternalJobOffer::isHiddenByAdmin).count());
        runs.findFirstBySourceOrderByFinishedAtDesc(RemoteOkParser.SOURCE).ifPresent(run -> body.put("last", runOf(run)));
        body.put("offers", offers.findByAvailableTrueOrderByPublishedAtDesc().stream().limit(20).map(this::brief).toList());
        return body;
    }

    @PostMapping("/{publicId}/hide")
    public Map<String, Object> hide(@PathVariable String publicId) {
        ExternalJobOffer offer = offers.findById(numericId(publicId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Offre externe introuvable"));
        offer.setHiddenByAdmin(true);
        offer.setAvailable(false);
        offers.save(offer);
        return Map.of("id", "ext-" + offer.getId(), "hidden", true);
    }

    private Map<String, Object> brief(ExternalJobOffer offer) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", "ext-" + offer.getId());
        row.put("title", offer.getTitle());
        row.put("companyName", offer.getCompany());
        row.put("sourceLabel", RemoteOkParser.SOURCE_LABEL);
        return row;
    }

    private static Map<String, Object> runOf(ExternalSyncRun run) {
        Map<String, Object> row = new LinkedHashMap<>();
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
