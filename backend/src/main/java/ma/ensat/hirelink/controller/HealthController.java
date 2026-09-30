package ma.ensat.hirelink.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.Connection;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Endpoint de santé — valide le démarrage Spring Boot et la connexion MySQL (Séance 1).
 */
@RestController
@RequestMapping("/api/v1")
public class HealthController {

    private final DataSource dataSource;

    @Autowired
    public HealthController(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("application", "HireLink");
        body.put("status", "UP");
        body.put("timestamp", Instant.now().toString());
        body.put("database", probeDatabase());
        return ResponseEntity.ok(body);
    }

    private Map<String, Object> probeDatabase() {
        Map<String, Object> db = new LinkedHashMap<>();
        try (Connection connection = dataSource.getConnection()) {
            db.put("vendor", "MySQL");
            db.put("connected", true);
            db.put("catalog", connection.getCatalog());
            db.put("product", connection.getMetaData().getDatabaseProductName());
            db.put("version", connection.getMetaData().getDatabaseProductVersion());
        } catch (Exception ex) {
            db.put("vendor", "MySQL");
            db.put("connected", false);
            db.put("error", ex.getMessage());
        }
        return db;
    }
}
