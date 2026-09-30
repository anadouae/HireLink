package ma.ensat.hirelink;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Point d'entrée HireLink — Séance 1 (initialisation Spring Boot + MySQL).
 */
@SpringBootApplication
public class HireLinkApplication {

    public static void main(String[] args) {
        SpringApplication.run(HireLinkApplication.class, args);
    }
}
