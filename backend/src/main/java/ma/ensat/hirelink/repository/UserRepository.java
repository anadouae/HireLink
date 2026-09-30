package ma.ensat.hirelink.repository;

import ma.ensat.hirelink.model.Role;
import ma.ensat.hirelink.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmailIgnoreCase(String email);
    boolean existsByEmailIgnoreCase(String email);
    Optional<User> findByGoogleSub(String googleSub);
    Optional<User> findByCompanyIce(String companyIce);
    List<User> findByRole(Role role);
    long countByRole(Role role);
}
