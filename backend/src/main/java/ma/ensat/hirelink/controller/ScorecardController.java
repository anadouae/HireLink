package ma.ensat.hirelink.controller;

import ma.ensat.hirelink.model.*;
import ma.ensat.hirelink.service.ScorecardService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/v1")
public class ScorecardController {

    private final ScorecardService scorecardService;

    public ScorecardController(ScorecardService scorecardService) {
        this.scorecardService = scorecardService;
    }

    public record CriteriaRequest(List<Map<String, Object>> criteria) {}
    public record RateRequest(Long criterionId, Integer score, String comment) {}

    @GetMapping("/offers/{offerId}/scorecard-criteria")
    public List<Map<String, Object>> getCriteria(@PathVariable Long offerId) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (ScorecardCriterion c : scorecardService.criteria(offerId)) {
            out.add(Map.of(
                    "id", c.getId(),
                    "label", c.getLabel(),
                    "weight", c.getWeight(),
                    "veto", c.isVeto()
            ));
        }
        return out;
    }

    @PutMapping("/offers/{offerId}/scorecard-criteria")
    public List<Map<String, Object>> setCriteria(
            @AuthenticationPrincipal User user,
            @PathVariable Long offerId,
            @RequestBody CriteriaRequest body
    ) {
        List<ScorecardCriterion> saved = scorecardService.setCriteria(
                user, offerId, body.criteria() == null ? List.of() : body.criteria());
        List<Map<String, Object>> out = new ArrayList<>();
        for (ScorecardCriterion c : saved) {
            out.add(Map.of("id", c.getId(), "label", c.getLabel(), "weight", c.getWeight(), "veto", c.isVeto()));
        }
        return out;
    }

    @GetMapping("/applications/{appId}/scorecard")
    public List<Map<String, Object>> entries(@PathVariable Long appId) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (ScorecardEntry e : scorecardService.entries(appId)) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", e.getId());
            row.put("criterionId", e.getCriterionId());
            row.put("evaluatorUserId", e.getEvaluatorUserId());
            row.put("score", e.getScore());
            row.put("comment", e.getComment());
            row.put("createdAt", e.getCreatedAt().toString());
            out.add(row);
        }
        return out;
    }

    @PostMapping("/applications/{appId}/scorecard")
    public Map<String, Object> rate(
            @AuthenticationPrincipal User user,
            @PathVariable Long appId,
            @RequestBody RateRequest body
    ) {
        ScorecardEntry e = scorecardService.rate(user, appId, body.criterionId(),
                body.score() == null ? 3 : body.score(), body.comment());
        return Map.of(
                "id", e.getId(),
                "criterionId", e.getCriterionId(),
                "score", e.getScore(),
                "status", "SAVED"
        );
    }
}
