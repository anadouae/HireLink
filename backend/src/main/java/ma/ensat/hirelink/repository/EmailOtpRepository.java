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

    /**
     * Essaie compté à la base (B6) : l'incrément et la limite sont une seule écriture atomique, donc des
     * tentatives simultanées ne peuvent pas partager le même compteur et dépasser MAX_ATTEMPTS.
     * Renvoie 0 quand le code est déjà consommé ou que le quota d'essais est atteint.
     */
    @Modifying
    @Query("""
            UPDATE EmailOtp o SET o.attempts = o.attempts + 1
            WHERE o.id = :id AND o.consumed = false AND o.attempts < :maxAttempts
            """)
    int reserveAttempt(@Param("id") Long id, @Param("maxAttempts") int maxAttempts);

    /** Consommation unique : un seul appelant concurrent gagne le code à usage unique. */
    @Modifying
    @Query("UPDATE EmailOtp o SET o.consumed = true WHERE o.id = :id AND o.consumed = false")
    int consumeOnce(@Param("id") Long id);

    /** Un code périmé ou déjà consommé ne peut plus rien débloquer : il n'a plus rien à faire en base. */
    @Modifying
    @Query("delete from EmailOtp o where o.expiresAt < :now or o.consumed = true")
    int purgeUsedOrExpired(@Param("now") Instant now);
}
