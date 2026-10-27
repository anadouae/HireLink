package ma.ensat.hirelink.controller;

import jakarta.validation.Valid;
import ma.ensat.hirelink.dto.ApiDtos;
import ma.ensat.hirelink.model.User;
import ma.ensat.hirelink.service.ApplicationService;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/v1/applications")
public class ApplicationController {

    private final ApplicationService applicationService;

    public ApplicationController(ApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    @PostMapping
    public ApiDtos.ApplicationResponse apply(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody ApiDtos.ApplyRequest body
    ) {
        return applicationService.apply(user, body);
    }

    @GetMapping("/mine")
    public List<ApiDtos.ApplicationResponse> mine(@AuthenticationPrincipal User user) {
        return applicationService.mineAsCandidate(user);
    }

    @GetMapping("/pipeline")
    public List<ApiDtos.ApplicationResponse> pipeline(@AuthenticationPrincipal User user) {
        return applicationService.pipelineForRecruiter(user);
    }

    @GetMapping("/by-offer/{offerId}")
    public List<ApiDtos.ApplicationResponse> byOffer(
            @AuthenticationPrincipal User user,
            @PathVariable Long offerId
    ) {
        return applicationService.forOffer(user, offerId);
    }

    @PatchMapping("/{id}/status")
    public ApiDtos.ApplicationResponse advance(
            @AuthenticationPrincipal User user,
            @PathVariable Long id,
            @Valid @RequestBody ApiDtos.AdvanceRequest body
    ) {
        return applicationService.advance(user, id, body);
    }

    public record AssignHmRequest(Long hmUserId) {}

    @PatchMapping("/{id}/assign-hm")
    public ApiDtos.ApplicationResponse assignHm(
            @AuthenticationPrincipal User user,
            @PathVariable Long id,
            @RequestBody AssignHmRequest body
    ) {
        return applicationService.assignHm(user, id, body.hmUserId());
    }
}
