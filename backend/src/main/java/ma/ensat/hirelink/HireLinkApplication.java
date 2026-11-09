package ma.ensat.hirelink;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Point d'entrée HireLink — Spring Boot + MySQL + scheduling SLA.
 * Le scheduling est conditionnel : voir {@link ma.ensat.hirelink.config.SchedulingConfig}.
 */
@SpringBootApplication
public class HireLinkApplication {

    public static void main(String[] args) {
        SpringApplication.run(HireLinkApplication.class, args);
    }
}
