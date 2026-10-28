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

    @org.springframework.security.access.prepost.PreAuthorize("hasRole('CANDIDAT')")
    @PostMapping
    public ApiDtos.ApplicationResponse apply(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody ApiDtos.ApplyRequest body
    ) {
        return applicationService.apply(user, body);
    }

    @org.springframework.security.access.prepost.PreAuthorize("hasRole('CANDIDAT')")
    @GetMapping("/mine")
    public List<ApiDtos.ApplicationResponse> mine(@AuthenticationPrincipal User user) {
        return applicationService.mineAsCandidate(user);
    }

    @org.springframework.security.access.prepost.PreAuthorize("hasRole('RECRUTEUR')")
    @GetMapping("/pipeline")
    public List<ApiDtos.ApplicationResponse> pipeline(@AuthenticationPrincipal User user) {
        return applicationService.pipelineForRecruiter(user);
    }

    @org.springframework.security.access.prepost.PreAuthorize("hasRole('RECRUTEUR')")
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

    @org.springframework.security.access.prepost.PreAuthorize("hasRole('RECRUTEUR')")
    @PatchMapping("/{id}/assign-hm")
    public ApiDtos.ApplicationResponse assignHm(
            @AuthenticationPrincipal User user,
            @PathVariable Long id,
            @jakarta.validation.Valid @RequestBody AssignHmRequest body
    ) {
        return applicationService.assignHm(user, id, body.hmUserId());
    }

    /** Le candidat joint un fichier CV à sa candidature (choix « Nouveau CV » de l'assistant). */
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('CANDIDAT')")
    @PostMapping(value = "/{id}/cv", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiDtos.ApplicationResponse attachCv(
            @AuthenticationPrincipal User user,
            @PathVariable Long id,
            @RequestPart("file") MultipartFile file,
            @RequestParam(value = "sections", required = false) String sections
    ) {
        return applicationService.attachCv(user, id, file, sections);
    }

    /** CV joint : le candidat lui-même ou l'équipe de l'organisation qui a publié l'offre. */
    @GetMapping("/{id}/cv")
    public ResponseEntity<Resource> downloadCv(@AuthenticationPrincipal User user, @PathVariable Long id) {
        ApplicationService.CvFile cv = applicationService.loadApplicationCv(user, id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + cv.filename().replaceAll("[^\\p{L}\\p{N}._ -]", "_") + "\"")
                .contentType(MediaType.parseMediaType(cv.mediaType()))
                .body(cv.resource());
    }
}
