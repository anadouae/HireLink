package ma.ensat.hirelink;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Point d'entrée HireLink — Spring Boot + MySQL + scheduling SLA.
 */
@SpringBootApplication
@EnableScheduling
public class HireLinkApplication {

    public static void main(String[] args) {
        SpringApplication.run(HireLinkApplication.class, args);
    }
}
