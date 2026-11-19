package ma.ensat.hirelink.repository;

import ma.ensat.hirelink.model.SupportRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SupportRequestRepository extends JpaRepository<SupportRequest, Long> {
    Page<SupportRequest> findAllByOrderByCreatedAtDesc(Pageable pageable);
    Page<SupportRequest> findByStatusOrderByCreatedAtDesc(String status, Pageable pageable);
    long countByStatus(String status);
}
