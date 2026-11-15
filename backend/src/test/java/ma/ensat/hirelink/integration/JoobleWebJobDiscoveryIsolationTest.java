package ma.ensat.hirelink.integration;

import ma.ensat.hirelink.service.MultiSourceWebJobDiscovery;
import ma.ensat.hirelink.service.WebJobDiscovery;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Avec web-search.enabled=true, le bean actif est l'agrégateur multi-sources (plus Jooble seul).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:mysql://127.0.0.1:3306/hirelink_jooble_web_iso?createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&connectTimeout=8000",
        "spring.datasource.username=root",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "hirelink.seed.enabled=false",
        "hirelink.scheduling.enabled=false",
        "hirelink.external-jobs.startup-sync=false",
        "hirelink.external-jobs.maroc.enabled=false",
        "hirelink.external-jobs.remoteok.enabled=false",
        "hirelink.external-jobs.greenhouse.enabled=false",
        "hirelink.external-jobs.lever.enabled=false",
        "hirelink.external-jobs.ashby.enabled=false",
        "hirelink.external-jobs.jooble.enabled=false",
        "hirelink.web-search.enabled=true",
        "hirelink.web-search.remotive.enabled=false",
        "hirelink.web-search.arbeitnow.enabled=false",
        "hirelink.web-search.remoteok.enabled=false",
        "hirelink.jwt.secret=hirelink-jooble-web-iso-key-not-for-real-use-00000",
        "hirelink.security.bcrypt-strength=4",
        "hirelink.swagger.enabled=false"
})
class JoobleWebJobDiscoveryIsolationTest {

    @Autowired WebJobDiscovery discovery;

    @Test
    void beanActifEstMultiSource() {
        assertInstanceOf(MultiSourceWebJobDiscovery.class, discovery);
    }

    @Test
    void sansSourcesPretesMessageClair() {
        WebJobDiscovery.DiscoveryResult r = discovery.search("java", "MA", "Casablanca", 5);
        // Remotive/Arbeitnow/RemoteOK off + Jooble sans clé → unavailable ou 0 job
        assertTrue(!r.available() || r.jobs().isEmpty());
        assertNotNull(r.coverageSummary());
        assertFalse(r.coverageSummary().toLowerCase().contains("filtre géographique : ma")
                && r.coverageSummary().toLowerCase().contains("tous les pays"));
    }
}
