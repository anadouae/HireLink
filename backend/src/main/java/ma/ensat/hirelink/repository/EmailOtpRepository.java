package ma.ensat.hirelink.repository;

import ma.ensat.hirelink.model.EmailOtp;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface EmailOtpRepository extends JpaRepository<EmailOtp, Long> {
    Optional<EmailOtp> findTopByEmailAndPurposeAndConsumedFalseOrderByCreatedAtDesc(String email, String purpose);

    /** Un code périmé ou déjà consommé ne peut plus rien débloquer : il n'a plus rien à faire en base. */
    @Modifying
    @Query("delete from EmailOtp o where o.expiresAt < :now or o.consumed = true")
    int purgeUsedOrExpired(@Param("now") Instant now);
}
