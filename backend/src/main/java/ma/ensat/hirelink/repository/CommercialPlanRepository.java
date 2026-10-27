package ma.ensat.hirelink.repository;

import ma.ensat.hirelink.model.CommercialPlan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CommercialPlanRepository extends JpaRepository<CommercialPlan, Long> {
    Optional<CommercialPlan> findByCode(String code);
}
