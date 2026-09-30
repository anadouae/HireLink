package ma.ensat.hirelink.repository;

import ma.ensat.hirelink.model.RefusalReason;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RefusalReasonRepository extends JpaRepository<RefusalReason, Long> {
    List<RefusalReason> findByActiveTrue();
    Optional<RefusalReason> findByCode(String code);
}
