package ma.ensat.hirelink.integration;

import ma.ensat.hirelink.model.AiUsageLog;
import ma.ensat.hirelink.model.PlanTier;
import ma.ensat.hirelink.model.Role;
import ma.ensat.hirelink.model.User;
import ma.ensat.hirelink.repository.AiUsageLogRepository;
import ma.ensat.hirelink.repository.UserRepository;
import ma.ensat.hirelink.service.AiCreditService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Vague 6 — N réservations concurrentes sur le dernier crédit : un seul gagnant.
 * Échec / release → pas de débit injustifié.
 */
@SpringBootTest
class AiCreditConcurrencyIntegrationTest {

    @Autowired AiCreditService credits;
    @Autowired UserRepository users;
    @Autowired AiUsageLogRepository logs;

    private User freeCandidateWithOneCreditLeft() {
        String seed = Long.toHexString(System.nanoTime());
        User u = new User();
        u.setEmail("ai-" + seed + "@test.hirelink.local");
        u.setFullName("AI Concurrency");
        u.setRole(Role.CANDIDAT);
        u.setPlanTier(PlanTier.GRATUIT); // 5 crédits
        u.setEmailVerified(true);
        u.setProfileComplete(true);
        u.setCreatedAt(java.time.Instant.now().minus(java.time.Duration.ofDays(1)));
        u.setPasswordHash("$2a$10$integrationtestnopasswordhashunused000000000000000000");
        u = users.saveAndFlush(u);
        // Consomme 4 crédits via le ledger (même chemin que la prod).
        for (int i = 0; i < 4; i++) {
            AiUsageLog reserved = credits.reserve(u, "prefill-" + i);
            reserved.setProvider("local-test");
            reserved.setSuccess(true);
            reserved.setCreditsSpent(1);
            logs.saveAndFlush(reserved);
        }
        return users.findById(u.getId()).orElseThrow();
    }

    @Test
    void onlyOneConcurrentReserveOnLastCredit() throws Exception {
        User u = freeCandidateWithOneCreditLeft();
        var before = credits.status(u);
        assertEquals(1, before.remaining(), "précondition : 1 crédit restant (used=" + before.used() + "/" + before.limit() + ")");
        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch go = new CountDownLatch(1);
        AtomicInteger wins = new AtomicInteger();
        AtomicInteger rejects = new AtomicInteger();
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            futures.add(pool.submit(() -> {
                ready.countDown();
                go.await();
                try {
                    credits.reserve(u, "concurrency-probe");
                    wins.incrementAndGet();
                } catch (ResponseStatusException e) {
                    if (e.getStatusCode().value() == 402) rejects.incrementAndGet();
                    else throw e;
                }
                return null;
            }));
        }
        ready.await();
        go.countDown();
        for (Future<?> f : futures) f.get();
        pool.shutdownNow();
        assertEquals(1, wins.get(), "exactement une réservation doit réussir");
        assertEquals(threads - 1, rejects.get());
    }

    @Test
    void releaseRestoresCredit() {
        User u = freeCandidateWithOneCreditLeft();
        AiUsageLog reserved = credits.reserve(u, "will-fail");
        credits.release(reserved);
        AiUsageLog again = credits.reserve(u, "retry-ok");
        assertTrue(again.getId() != null);
        credits.release(again);
    }
}
