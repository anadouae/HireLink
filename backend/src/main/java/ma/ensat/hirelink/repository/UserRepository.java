package ma.ensat.hirelink.repository;

import ma.ensat.hirelink.model.Role;
import ma.ensat.hirelink.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmailIgnoreCase(String email);
    Optional<User> findByGoogleSub(String googleSub);
    java.util.List<User> findAllByCompanyIce(String companyIce);
    long countByRole(Role role);
    boolean existsByRole(Role role);

    List<User> findTop10ByFullNameContainingIgnoreCaseOrEmailContainingIgnoreCase(String name, String email);

    /** Verrou de ligne : sérialise les réservations concurrentes d'un même quota (crédits IA, sièges). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM User u WHERE u.id = :id")
    Optional<User> findByIdForUpdate(@Param("id") Long id);
}
