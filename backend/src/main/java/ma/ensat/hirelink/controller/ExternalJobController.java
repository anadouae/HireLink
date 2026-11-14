package ma.ensat.hirelink.controller;

import ma.ensat.hirelink.model.ExternalJobOffer;
import ma.ensat.hirelink.model.Role;
import ma.ensat.hirelink.model.User;
import ma.ensat.hirelink.repository.ExternalJobOfferRepository;
import ma.ensat.hirelink.service.ExternalJobImportService;
import ma.ensat.hirelink.service.ExternalJobSyncFacade;
import ma.ensat.hirelink.service.ExternalSourceCatalog;
import ma.ensat.hirelink.service.SkillMatchingService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/external-offers")
public class ExternalJobController {

    private final ExternalJobOfferRepository offers;
    private final ExternalJobSyncFacade sync;
    private final ExternalSourceCatalog catalog;
    private final SkillMatchingService matching;

    public ExternalJobController(ExternalJobOfferRepository offers,
                                 ExternalJobSyncFacade sync,
                                 ExternalSourceCatalog catalog,
                                 SkillMatchingService matching) {
        this.offers = offers;
        this.sync = sync;
        this.catalog = catalog;
        this.matching = matching;
    }

    @GetMapping
    public List<Map<String, Object>> list(@AuthenticationPrincipal User user) {
        // Vitrine = offres available (hors quarantaine / hors masquage). Une source desactivee
        // bloque la sync, pas l'affichage des lignes deja importees et encore fiables.
        List<Map<String, Object>> out = new ArrayList<>();
        for (ExternalJobOffer offer : offers.findByAvailableTrueOrderByPublishedAtDesc()) {
            if (offer.isQuarantined()) continue;
            out.add(toPublic(offer, user));
        }
        return out;
    }

    @GetMapping("/sources")
    public List<Map<String, Object>> sources() {
        return catalog.all().stream().map(catalog::toMap).toList();
    }

    @PostMapping("/sync")
    @PreAuthorize("hasRole('ADMINISTRATEUR')")
    public Map<String, Object> sync() {
        List<ExternalJobImportService.SyncReport> reports = sync.syncAllEnabled();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("reports", reports);
        body.put("synced", reports.size());
        return body;
    }

    private Map<String, Object> toPublic(ExternalJobOffer offer, User user) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("external", true);
        body.put("id", "ext-" + offer.getId());
        body.put("companyVerified", false);
        body.put("source", offer.getSource());
        body.put("sourceLabel", catalog.labelOf(offer.getSource()));
        body.put("sourceHome", catalog.homeOf(offer.getSource()));
        body.put("boardKey", offer.getBoardKey());
        body.put("countryCode", offer.getCountryCode());
        body.put("title", offer.getTitle());
        body.put("companyName", offer.getCompany());
        body.put("location", offer.getLocation());
        body.put("contractType", offer.getContractLabel());
        body.put("description", offer.getDescription());
        body.put("requiredSkills", offer.getSkills());
        body.put("originUrl", offer.getOriginUrl());
        // applyUrl peut etre null : le client ne doit pas inventer un lien de candidature.
        body.put("applyUrl", offer.getApplyUrl());
        String quality = offer.getUrlQuality() == null ? "UNKNOWN" : offer.getUrlQuality();
        body.put("urlQuality", quality);
        body.put("urlQualityReason", offer.getUrlQualityReason());
        boolean direct = "DEEP".equals(quality);
        body.put("linkReliable", direct);
        body.put("directAccessTrusted", direct);
        body.put("publishedAt", offer.getPublishedAt());
        body.put("createdAt", offer.getPublishedAt());
        if (offer.getSalaryMin() != null) body.put("salaryMin", offer.getSalaryMin());
        if (offer.getSalaryMax() != null) body.put("salaryMax", offer.getSalaryMax());
        boolean available = user != null && user.getRole() == Role.CANDIDAT
                && offer.getSkills() != null && !offer.getSkills().isBlank()
                && matching.hasConfirmedSkills(user);
        body.put("matchAvailable", available);
        body.put("matchLabel", available
                ? "Indicatif : competences citees par la source, sans validation de l'entreprise."
                : "Correspondance non disponible");
        if (available) body.put("matchScore", matching.explain(user.getSkills(), offer.getSkills()).score());
        return body;
    }
}
