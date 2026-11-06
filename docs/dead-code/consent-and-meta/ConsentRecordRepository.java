package ma.ensat.hirelink.repository;

import ma.ensat.hirelink.model.ConsentRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ConsentRecordRepository extends JpaRepository<ConsentRecord, Long> {
    List<ConsentRecord> findByUserId(Long userId);
}
