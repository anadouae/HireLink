package ma.ensat.hirelink.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import ma.ensat.hirelink.service.SkillCatalogService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Référentiel de compétences : recherche tolérante aux fautes (saisie libre) et classement par catégorie. */
@RestController
@RequestMapping("/api/v1/skills")
@Validated
public class SkillController {

    private static final int SUGGEST_LIMIT = 10;

    public record ClassifyRequest(@Size(max = 2000) String skills) {}

    private final SkillCatalogService catalog;

    public SkillController(SkillCatalogService catalog) {
        this.catalog = catalog;
    }

    @PreAuthorize("hasAnyRole('CANDIDAT','RECRUTEUR','ADMINISTRATEUR')")
    @GetMapping("/suggest")
    public List<SkillCatalogService.Suggestion> suggest(@RequestParam @Size(max = 80) String q) {
        return catalog.search(q, SUGGEST_LIMIT);
    }

    @PreAuthorize("hasAnyRole('CANDIDAT','RECRUTEUR','ADMINISTRATEUR')")
    @PostMapping("/classify")
    public List<SkillCatalogService.Group> classify(@Valid @RequestBody ClassifyRequest body) {
        return catalog.classify(body.skills());
    }
}
