package ma.ensat.hirelink.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DatabaseTargetGuardTest {

    @Test
    void allowsDefaultTestDatabase() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("spring.datasource.url",
                        "jdbc:mysql://127.0.0.1:3306/hirelink_test?useSSL=false");

        assertDoesNotThrow(() -> DatabaseTargetGuard.validate(environment));
    }

    @Test
    void rejectsLocalRealDatabaseWithoutRealdbProfile() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("spring.datasource.url", "jdbc:mysql://127.0.0.1:3306/hirelink");

        assertThrows(IllegalStateException.class, () -> DatabaseTargetGuard.validate(environment));
    }

    @Test
    void realdbProfileRequiresConfirmationAndTheRealDatabase() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("spring.datasource.url", "jdbc:mysql://127.0.0.1:3306/hirelink")
                .withProperty("hirelink.realdb.confirmation", DatabaseTargetGuard.REALDB_CONFIRMATION);
        environment.setActiveProfiles("realdb");

        assertDoesNotThrow(() -> DatabaseTargetGuard.validate(environment));
    }

    @Test
    void realdbProfileRejectsOtherDatabasesAndAutomaticCreation() {
        MockEnvironment wrongDatabase = new MockEnvironment()
                .withProperty("spring.datasource.url", "jdbc:mysql://127.0.0.1:3306/hirelink_test")
                .withProperty("hirelink.realdb.confirmation", DatabaseTargetGuard.REALDB_CONFIRMATION);
        wrongDatabase.setActiveProfiles("realdb");

        MockEnvironment automaticCreation = new MockEnvironment()
                .withProperty("spring.datasource.url",
                        "jdbc:mysql://127.0.0.1:3306/hirelink?createDatabaseIfNotExist=true")
                .withProperty("hirelink.realdb.confirmation", DatabaseTargetGuard.REALDB_CONFIRMATION);
        automaticCreation.setActiveProfiles("realdb");

        assertThrows(IllegalStateException.class, () -> DatabaseTargetGuard.validate(wrongDatabase));
        assertThrows(IllegalStateException.class, () -> DatabaseTargetGuard.validate(automaticCreation));
    }

    @Test
    void realdbProfileRejectsMissingConfirmation() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("spring.datasource.url", "jdbc:mysql://127.0.0.1:3306/hirelink");
        environment.setActiveProfiles("realdb");

        assertThrows(IllegalStateException.class, () -> DatabaseTargetGuard.validate(environment));
    }
}
