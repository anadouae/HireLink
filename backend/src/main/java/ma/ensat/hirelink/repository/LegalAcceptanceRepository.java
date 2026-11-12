package ma.ensat.hirelink.repository;

import ma.ensat.hirelink.model.LegalAcceptance;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LegalAcceptanceRepository extends JpaRepository<LegalAcceptance, Long> {
    List<LegalAcceptance> findByUserIdOrderByAcceptedAtDesc(Long userId);

    Optional<LegalAcceptance> findFirstByUserIdAndPurposeAndGrantedTrueOrderByAcceptedAtDesc(Long userId, String purpose);

    boolean existsByUserIdAndPurposeAndDocumentVersionAndGrantedTrue(Long userId, String purpose, String documentVersion);
}
