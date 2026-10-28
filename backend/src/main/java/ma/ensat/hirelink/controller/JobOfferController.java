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
}
