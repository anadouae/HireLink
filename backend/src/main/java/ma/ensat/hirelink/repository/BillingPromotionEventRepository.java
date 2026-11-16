package ma.ensat.hirelink.repository;

import ma.ensat.hirelink.model.BillingPromotionEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BillingPromotionEventRepository extends JpaRepository<BillingPromotionEvent, Long> {
    List<BillingPromotionEvent> findByPromotionIdOrderByCreatedAtDesc(Long promotionId);
    List<BillingPromotionEvent> findTop200ByOrderByCreatedAtDesc();
}
