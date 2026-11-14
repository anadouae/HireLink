package ma.ensat.hirelink.integration;

import ma.ensat.hirelink.model.JobOffer;
import ma.ensat.hirelink.model.PlanTier;
import ma.ensat.hirelink.model.Role;
import ma.ensat.hirelink.model.User;
import ma.ensat.hirelink.model.WorkMode;
import ma.ensat.hirelink.repository.CatalogListingRepository;
import ma.ensat.hirelink.repository.JobOfferRepository;
import ma.ensat.hirelink.repository.UserRepository;
import ma.ensat.hirelink.security.ClientInfo;
import ma.ensat.hirelink.security.JwtService;
import ma.ensat.hirelink.service.CatalogOfferService;
import ma.ensat.hirelink.service.SessionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.util.UriComponentsBuilder;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Perf multi-volumes sur hirelink_ext_phase03 uniquement.
 * Ne touche jamais hirelink / 8081.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:mysql://127.0.0.1:3306/hirelink_ext_perf?createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&connectTimeout=8000",
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
        "hirelink.catalog.for-you-threshold=50",
        "hirelink.catalog.for-you-max-scan=2000",
        "hirelink.catalog.exclude-sources=MAROC_EMPLOI",
        "hirelink.jwt.secret=hirelink-perf-multi-volume-key-not-for-real-use-00000",
        "hirelink.security.bcrypt-strength=4",
        "hirelink.swagger.enabled=false"
})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CatalogPerfMultiVolumeIsolationTest {

    @Autowired TestRestTemplate http;
    @Autowired CatalogOfferService catalog;
    @Autowired CatalogListingRepository listings;
    @Autowired JobOfferRepository jobOffers;
    @Autowired UserRepository users;
    @Autowired SessionService sessions;
    @Autowired JwtService jwt;
    @Autowired PasswordEncoder encoder;

    @BeforeEach
    void clean() {
        listings.deleteAll();
        jobOffers.deleteAll();
    }

    @Test
    void measureVolumes100_1k_5k_10k_andOptional25k() throws Exception {
        User recruiter = compte(Role.RECRUTEUR);
        User candidate = compte(Role.CANDIDAT);
        candidate.setSkills("Java, Spring Boot, SQL");
        candidate.setPreferredContracts("CDI");
        candidate.setPreferredLocations("Casablanca");
        users.save(candidate);
        String token = token(candidate);

        int[] volumes = {100, 1000, 5000, 10000};
        MemoryMXBean mem = ManagementFactory.getMemoryMXBean();
        StringBuilder csv = new StringBuilder("n,rebuildMs,pageMs,countMs,filterMs,forYouMs,heapMb,concurrentP95Ms\n");

        for (int n : volumes) {
            listings.deleteAll();
            jobOffers.deleteAll();
            seed(recruiter, n);

            long t0 = System.nanoTime();
            int indexed = catalog.rebuildIndex();
            long rebuildMs = (System.nanoTime() - t0) / 1_000_000L;
            assertEquals(n, indexed);

            t0 = System.nanoTime();
            Map page = get("/api/v1/catalog/offers?country=ALL&page=0&size=20", null);
            long pageMs = (System.nanoTime() - t0) / 1_000_000L;
            assertEquals(n, ((Number) page.get("totalElements")).intValue());

            t0 = System.nanoTime();
            Map countOnly = get("/api/v1/catalog/offers?country=ALL&page=0&size=1", null);
            long countMs = (System.nanoTime() - t0) / 1_000_000L;
            assertEquals(n, ((Number) countOnly.get("totalElements")).intValue());

            t0 = System.nanoTime();
            Map filtered = get("/api/v1/catalog/offers?country=ALL&city=Casablanca&contract=CDI&size=20", null);
            long filterMs = (System.nanoTime() - t0) / 1_000_000L;
            assertTrue(((Number) filtered.get("totalElements")).intValue() > 0);

            t0 = System.nanoTime();
            ResponseEntity<Map> forYou = http.exchange(
                    "/api/v1/catalog/offers?country=ALL&forYou=true&size=20",
                    HttpMethod.GET, auth(token), Map.class);
            long forYouMs = (System.nanoTime() - t0) / 1_000_000L;
            assertEquals(HttpStatus.OK, forYou.getStatusCode());
            assertFalse(forYou.getBody().containsKey("status") && Integer.valueOf(400).equals(forYou.getBody().get("status")));

            long concurrentP95 = concurrentPageP95(8, 16);
            long heapMb = mem.getHeapMemoryUsage().getUsed() / (1024 * 1024);

            String line = n + "," + rebuildMs + "," + pageMs + "," + countMs + "," + filterMs + "," + forYouMs + "," + heapMb + "," + concurrentP95;
            csv.append(line).append('\n');
            System.out.println("PERF_ROW " + line);

            assertTrue(pageMs < 8_000, "page trop lente n=" + n + " ms=" + pageMs);
            assertTrue(filterMs < 8_000, "filtre trop lent n=" + n + " ms=" + filterMs);
            assertTrue(forYouMs < 30_000, "pourToi trop lent n=" + n + " ms=" + forYouMs);
        }

        // 25k optionnel : saute si heap max < 900MB pour proteger la machine
        long maxHeapMb = mem.getHeapMemoryUsage().getMax() / (1024 * 1024);
        if (maxHeapMb >= 900) {
            listings.deleteAll();
            jobOffers.deleteAll();
            int n = 25000;
            seed(recruiter, n);
            long t0 = System.nanoTime();
            int indexed = catalog.rebuildIndex();
            long rebuildMs = (System.nanoTime() - t0) / 1_000_000L;
            assertEquals(n, indexed);
            t0 = System.nanoTime();
            Map page = get("/api/v1/catalog/offers?country=ALL&page=0&size=20", null);
            long pageMs = (System.nanoTime() - t0) / 1_000_000L;
            System.out.println("PERF_ROW " + n + "," + rebuildMs + "," + pageMs + ",,,,,");
            assertEquals(n, ((Number) page.get("totalElements")).intValue());
            assertTrue(pageMs < 15_000, "page 25k trop lente: " + pageMs);
        } else {
            System.out.println("PERF_SKIP_25K maxHeapMb=" + maxHeapMb);
        }

        System.out.println("PERF_CSV\n" + csv);
    }

    private long concurrentPageP95(int threads, int calls) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        List<Callable<Long>> tasks = new ArrayList<>();
        for (int i = 0; i < calls; i++) {
            final int page = i % 5;
            tasks.add(() -> {
                long t0 = System.nanoTime();
                get("/api/v1/catalog/offers?country=ALL&page=" + page + "&size=20", null);
                return (System.nanoTime() - t0) / 1_000_000L;
            });
        }
        List<Future<Long>> futures = pool.invokeAll(tasks, 120, TimeUnit.SECONDS);
        pool.shutdown();
        List<Long> times = new ArrayList<>();
        for (Future<Long> f : futures) {
            assertTrue(f.isDone());
            times.add(f.get());
        }
        times.sort(Long::compareTo);
        int idx = Math.min(times.size() - 1, (int) Math.ceil(times.size() * 0.95) - 1);
        return times.get(Math.max(0, idx));
    }

    private void seed(User recruiter, int n) {
        List<JobOffer> batch = new ArrayList<>(Math.min(n, 2000));
        for (int i = 0; i < n; i++) {
            JobOffer o = new JobOffer();
            o.setRecruiter(recruiter);
            o.setTitle("PerfVol " + i);
            o.setDescription("Description performance volume " + i + " suffisamment longue.");
            o.setLocation(switch (i % 5) {
                case 0 -> "Casablanca";
                case 1 -> "Rabat";
                case 2 -> "Tanger";
                case 3 -> "Marrakech";
                default -> "Tetouan";
            });
            o.setContractType(i % 2 == 0 ? "CDI" : "Stage");
            o.setRequiredSkills(i % 3 == 0 ? "Java, Spring" : "Python, SQL");
            o.setWorkMode(WorkMode.HYBRID);
            o.setPublished(true);
            o.setCreatedAt(Instant.now().minus(i, ChronoUnit.MINUTES));
            batch.add(o);
            if (batch.size() >= 1000) {
                jobOffers.saveAll(batch);
                batch.clear();
            }
        }
        if (!batch.isEmpty()) jobOffers.saveAll(batch);
    }

    private Map get(String url, String token) {
        ResponseEntity<Map> res = http.exchange(url, HttpMethod.GET, token == null ? null : auth(token), Map.class);
        assertEquals(HttpStatus.OK, res.getStatusCode());
        return res.getBody();
    }

    private User compte(Role role) {
        User user = new User();
        user.setEmail(role.name().toLowerCase() + "-perf-" + Long.toHexString(System.nanoTime()) + "@test.hirelink.local");
        user.setFullName("Perf");
        user.setFirstName("Perf");
        user.setRole(role);
        user.setPlanTier(role == Role.CANDIDAT ? PlanTier.ESSENTIEL : PlanTier.ACCES);
        user.setEmailVerified(true);
        user.setProfileComplete(true);
        user.setPasswordHash(encoder.encode("Phrase-de-validation-2026"));
        return users.save(user);
    }

    private String token(User user) {
        return jwt.generate(user, sessions.create(user, new ClientInfo("127.0.0.1", "perf")).session().getId().toString());
    }

    private HttpEntity<Void> auth(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return new HttpEntity<>(headers);
    }
}