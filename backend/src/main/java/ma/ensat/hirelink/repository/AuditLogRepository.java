package ma.ensat.hirelink.repository;

import ma.ensat.hirelink.model.AuditLog;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
    List<AuditLog> findAllByOrderByIdDesc(Pageable pageable);
    List<AuditLog> findByActionOrderByIdDesc(String action, Pageable pageable);
}
