package ma.ensat.hirelink.controller;

import ma.ensat.hirelink.model.User;
import ma.ensat.hirelink.service.JobOfferService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/offers")
public class OfferReportController {

    private final JobOfferService jobOfferService;

    public OfferReportController(JobOfferService jobOfferService) {
        this.jobOfferService = jobOfferService;
    }

    public record ReportBody(String reason) {}

    @PostMapping("/{id}/report")
    public Map<String, Object> report(
            @AuthenticationPrincipal User user,
            @PathVariable Long id,
            @RequestBody(required = false) ReportBody body
    ) {
        return jobOfferService.report(user, id, body == null ? null : body.reason());
    }
}
