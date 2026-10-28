package ma.ensat.hirelink.repository;

import ma.ensat.hirelink.model.Role;
import ma.ensat.hirelink.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmailIgnoreCase(String email);
    Optional<User> findByGoogleSub(String googleSub);
    java.util.List<User> findAllByCompanyIce(String companyIce);
    long countByRole(Role role);
    boolean existsByRole(Role role);
}
