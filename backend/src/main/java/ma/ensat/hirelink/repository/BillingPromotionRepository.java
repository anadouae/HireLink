package ma.ensat.hirelink.repository;

import ma.ensat.hirelink.model.BillingPromotion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BillingPromotionRepository extends JpaRepository<BillingPromotion, Long> {
    Optional<BillingPromotion> findByCodeIgnoreCase(String code);
    List<BillingPromotion> findAllByOrderByUpdatedAtDesc();
    List<BillingPromotion> findByPlanCodeAndActiveTrueOrderByUpdatedAtDesc(String planCode);
}
