package ma.ensat.hirelink.controller;

import ma.ensat.hirelink.model.ExternalJobOffer;
import ma.ensat.hirelink.model.Role;
import ma.ensat.hirelink.model.User;
import ma.ensat.hirelink.repository.ExternalJobOfferRepository;
import ma.ensat.hirelink.service.ExternalJobImportService;
import ma.ensat.hirelink.service.RemoteOkClient;
import ma.ensat.hirelink.service.RemoteOkParser;
import ma.ensat.hirelink.service.SkillMatchingService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/external-offers")
public class ExternalJobController {

    private final ExternalJobOfferRepository offers;
    private final ExternalJobImportService imports;
    private final RemoteOkClient client;
    private final RemoteOkParser parser = new RemoteOkParser();
    private final SkillMatchingService matching;
    private final boolean enabled;

    public ExternalJobController(ExternalJobOfferRepository offers,
                                 ExternalJobImportService imports,
                                 RemoteOkClient client,
                                 SkillMatchingService matching,
                                 @Value("${hirelink.external-jobs.remoteok.enabled:false}") boolean enabled) {
        this.offers = offers;
        this.imports = imports;
        this.client = client;
        this.matching = matching;
        this.enabled = enabled;
    }

    @GetMapping
    public List<Map<String, Object>> list(@AuthenticationPrincipal User user) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (ExternalJobOffer offer : offers.findByAvailableTrueOrderByPublishedAtDesc()) out.add(toPublic(offer, user));
        return out;
    }

    @PostMapping("/sync")
    @PreAuthorize("hasRole('ADMINISTRATEUR')")
    public ExternalJobImportService.SyncReport sync() {
        if (!enabled) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "L'import Remote OK est désactivé : aucune requête n'a été envoyée.");
        }
        try {
            return imports.importSnapshot(RemoteOkParser.SOURCE, parser.parse(client.fetch()));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Le flux Remote OK a un format inattendu. Les offres déjà importées sont conservées.");
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, e.getMessage());
        }
    }

    private Map<String, Object> toPublic(ExternalJobOffer offer, User user) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("external", true);
        body.put("id", "ext-" + offer.getId());
        body.put("companyVerified", false);
        body.put("source", offer.getSource());
        body.put("sourceLabel", RemoteOkParser.SOURCE_LABEL);
        body.put("sourceHome", RemoteOkParser.SOURCE_HOME);
        body.put("title", offer.getTitle());
        body.put("companyName", offer.getCompany());
        body.put("location", offer.getLocation());
        body.put("contractType", offer.getContractLabel());
        body.put("description", offer.getDescription());
        body.put("requiredSkills", offer.getSkills());
        body.put("originUrl", offer.getOriginUrl());
        body.put("applyUrl", offer.getApplyUrl());
        body.put("publishedAt", offer.getPublishedAt());
        body.put("createdAt", offer.getPublishedAt());
        if (offer.getSalaryMin() != null) body.put("salaryMin", offer.getSalaryMin());
        if (offer.getSalaryMax() != null) body.put("salaryMax", offer.getSalaryMax());
        boolean available = user != null && user.getRole() == Role.CANDIDAT
                && offer.getSkills() != null && !offer.getSkills().isBlank()
                && matching.hasConfirmedSkills(user);
        body.put("matchAvailable", available);
        body.put("matchLabel", available
                ? "Indicatif : compétences citées par la source, sans validation de l'entreprise."
                : "Correspondance non disponible");
        if (available) body.put("matchScore", matching.explain(user.getSkills(), offer.getSkills()).score());
        return body;
    }
}
