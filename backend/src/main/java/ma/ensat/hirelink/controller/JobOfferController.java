package ma.ensat.hirelink.controller;

import jakarta.validation.Valid;
import ma.ensat.hirelink.dto.ApiDtos;
import ma.ensat.hirelink.model.User;
import ma.ensat.hirelink.service.JobOfferService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/offers")
public class JobOfferController {

    private final JobOfferService jobOfferService;

    public JobOfferController(JobOfferService jobOfferService) {
        this.jobOfferService = jobOfferService;
    }

    @GetMapping("/public")
    public List<ApiDtos.JobOfferResponse> listPublic(@AuthenticationPrincipal User user) {
        return jobOfferService.listPublic(user);
    }

    @org.springframework.security.access.prepost.PreAuthorize("hasRole('RECRUTEUR')")
    @GetMapping("/mine")
    public List<ApiDtos.JobOfferResponse> mine(@AuthenticationPrincipal User user) {
        return jobOfferService.listMine(user);
    }

    @GetMapping("/{id}")
    public ApiDtos.JobOfferResponse get(@PathVariable Long id, @AuthenticationPrincipal User user) {
        return jobOfferService.get(id, user);
    }

    @org.springframework.security.access.prepost.PreAuthorize("hasRole('RECRUTEUR')")
    @PostMapping
    public ApiDtos.JobOfferResponse create(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody ApiDtos.JobOfferRequest body
    ) {
        return jobOfferService.create(user, body);
    }

    @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('RECRUTEUR','ADMINISTRATEUR')")
    @PutMapping("/{id}")
    public ApiDtos.JobOfferResponse update(
            @AuthenticationPrincipal User user,
            @PathVariable Long id,
            @Valid @RequestBody ApiDtos.JobOfferRequest body
    ) {
        return jobOfferService.update(user, id, body);
    }

    @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('RECRUTEUR','ADMINISTRATEUR')")
    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@AuthenticationPrincipal User user, @PathVariable Long id) {
        jobOfferService.delete(user, id);
        return Map.of("status", "DELETED", "id", id);
    }

    /** Cycle de vie : chaque action a son verbe, aucun ne passe par la réécriture complète de l'offre. */
    @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('RECRUTEUR','ADMINISTRATEUR')")
    @PostMapping("/{id}/publish")
    public ApiDtos.JobOfferResponse publish(@AuthenticationPrincipal User user, @PathVariable Long id) {
        return jobOfferService.publish(user, id);
    }

    @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('RECRUTEUR','ADMINISTRATEUR')")
    @PostMapping("/{id}/unpublish")
    public ApiDtos.JobOfferResponse unpublish(@AuthenticationPrincipal User user, @PathVariable Long id) {
        return jobOfferService.unpublish(user, id);
    }

    @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('RECRUTEUR','ADMINISTRATEUR')")
    @PostMapping("/{id}/close")
    public ApiDtos.JobOfferResponse close(@AuthenticationPrincipal User user, @PathVariable Long id) {
        return jobOfferService.close(user, id);
    }

    @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('RECRUTEUR','ADMINISTRATEUR')")
    @PostMapping("/{id}/reopen")
    public ApiDtos.JobOfferResponse reopen(@AuthenticationPrincipal User user, @PathVariable Long id) {
        return jobOfferService.reopen(user, id);
    }

    @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('RECRUTEUR','ADMINISTRATEUR')")
    @PostMapping("/{id}/archive")
    public ApiDtos.JobOfferResponse archive(@AuthenticationPrincipal User user, @PathVariable Long id) {
        return jobOfferService.archive(user, id);
    }

    @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('RECRUTEUR','ADMINISTRATEUR')")
    @PostMapping("/{id}/unarchive")
    public ApiDtos.JobOfferResponse unarchive(@AuthenticationPrincipal User user, @PathVariable Long id) {
        return jobOfferService.unarchive(user, id);
    }

    @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('RECRUTEUR','ADMINISTRATEUR')")
    @PostMapping("/{id}/duplicate")
    public ApiDtos.JobOfferResponse duplicate(@AuthenticationPrincipal User user, @PathVariable Long id) {
        return jobOfferService.duplicate(user, id);
    }

    @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('RECRUTEUR','ADMINISTRATEUR')")
    @PostMapping("/{id}/extend")
    public ApiDtos.JobOfferResponse extend(@AuthenticationPrincipal User user, @PathVariable Long id,
                                           @RequestBody Map<String, Integer> body) {
        return jobOfferService.extendDeadline(user, id, body.get("days"));
    }
}
