package ma.ensat.hirelink.controller;

import ma.ensat.hirelink.service.OrgLogoStorage;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/media")
public class MediaController {

    private final OrgLogoStorage orgLogoStorage;

    public MediaController(OrgLogoStorage orgLogoStorage) {
        this.orgLogoStorage = orgLogoStorage;
    }

    @GetMapping("/org-logos/{orgFolder}/{fileName}")
    public ResponseEntity<Resource> orgLogo(
            @PathVariable String orgFolder,
            @PathVariable String fileName
    ) {
        String relative = orgFolder + "/" + fileName;
        Resource resource;
        try {
            resource = orgLogoStorage.load(relative);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
        if (resource == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Logo introuvable");
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "public, max-age=86400")
                .contentType(MediaType.parseMediaType(orgLogoStorage.mediaType(relative)))
                .body(resource);
    }
}
