package ma.ensat.hirelink.controller;

import jakarta.validation.Valid;
import ma.ensat.hirelink.dto.ApiDtos;
import ma.ensat.hirelink.model.User;
import ma.ensat.hirelink.service.JobOfferService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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

    @GetMapping("/mine")
    public List<ApiDtos.JobOfferResponse> mine(@AuthenticationPrincipal User user) {
        return jobOfferService.listMine(user);
    }

    @GetMapping("/{id}")
    public ApiDtos.JobOfferResponse get(@PathVariable Long id, @AuthenticationPrincipal User user) {
        return jobOfferService.get(id, user);
    }

    @PostMapping
    public ApiDtos.JobOfferResponse create(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody ApiDtos.JobOfferRequest body
    ) {
        return jobOfferService.create(user, body);
    }

    @PutMapping("/{id}")
    public ApiDtos.JobOfferResponse update(
            @AuthenticationPrincipal User user,
            @PathVariable Long id,
            @Valid @RequestBody ApiDtos.JobOfferRequest body
    ) {
        return jobOfferService.update(user, id, body);
    }
}
