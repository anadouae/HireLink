package ma.ensat.hirelink.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Métadonnées projet exposées à l'API — utile pour la page d'accueil frontend (Séance 1).
 */
@RestController
@RequestMapping("/api/v1/meta")
public class ProjectMetaController {

    @GetMapping
    public ResponseEntity<Map<String, Object>> meta() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", "HireLink");
        body.put("version", "0.1.0");
        body.put("description", "Plateforme bilatérale de gestion du processus de recrutement");
        body.put("module", "Frameworks Technologie Web — ENSA Tétouan");
        body.put("actors", List.of("CANDIDAT", "RECRUTEUR", "ADMINISTRATEUR"));
        body.put("architecture", "Frontend (HTML/CSS/JS) → API REST → Spring Boot → MySQL");
        body.put("constraints", List.of(
                "SGBD: MySQL",
                "Aucune API tierce payante",
                "Aide à la décision locale (appariement de compétences)"
        ));
        return ResponseEntity.ok(body);
    }
}
