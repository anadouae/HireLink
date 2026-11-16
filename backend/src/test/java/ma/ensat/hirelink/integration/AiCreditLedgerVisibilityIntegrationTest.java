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

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class AiCreditLedgerVisibilityIntegrationTest {

    @Autowired AiCreditService credits;
    @Autowired UserRepository users;
    @Autowired AiUsageLogRepository logs;

    @Test
    void reservedCreditIsVisibleInStatusImmediately() {
        String seed = Long.toHexString(System.nanoTime());
        User u = new User();
        u.setEmail("vis-" + seed + "@test.hirelink.local");
        u.setFullName("Ledger Vis");
        u.setRole(Role.CANDIDAT);
        u.setPlanTier(PlanTier.GRATUIT);
        u.setEmailVerified(true);
        u.setProfileComplete(true);
        u.setPasswordHash("$2a$10$integrationtestnopasswordhashunused000000000000000000");
        final User saved = users.saveAndFlush(u);

        var empty = credits.status(saved);
        assertEquals(0, empty.used());
        assertEquals(5, empty.limit());

        AiUsageLog reserved = credits.reserve(saved, "vis-1");
        assertTrue(reserved.getId() != null);
        assertEquals(1, reserved.getCreditsSpent());
        assertEquals(saved.getId(), reserved.getUserId());

        long rows = logs.findAll().stream().filter(r -> saved.getId().equals(r.getUserId())).count();
        assertEquals(1, rows, "ligne ledger absente en base après reserve");

        Instant from = Instant.now().minus(40, ChronoUnit.DAYS);
        Instant to = Instant.now().plus(1, ChronoUnit.DAYS);
        Integer sum = logs.sumCreditsSpent(saved.getId(), from, to);
        assertEquals(1, sum == null ? 0 : sum, "sumCreditsSpent from=" + from + " to=" + to);

        var after = credits.status(saved);
        assertEquals(1, after.used(), "la réservation doit apparaître dans le solde (used)");
        assertEquals(4, after.remaining());
    }
}
