package ma.ensat.hirelink.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Les tâches planifiées (rappel J-1, relance SMTP, purge de rétention, moniteur SLA) sont actives par
 * défaut. {@code hirelink.scheduling.enabled=false} les coupe pour une instance donnée : c'est ce qui
 * permet à un test d'intégration de déclencher lui-même le job, sans course avec l'horloge.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "hirelink.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class SchedulingConfig {
}
