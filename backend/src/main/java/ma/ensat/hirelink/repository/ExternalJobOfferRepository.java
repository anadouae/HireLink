package ma.ensat.hirelink.repository;

import ma.ensat.hirelink.model.ExternalJobOffer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ExternalJobOfferRepository extends JpaRepository<ExternalJobOffer, Long> {
    List<ExternalJobOffer> findBySource(String source);
    List<ExternalJobOffer> findByAvailableTrueOrderByPublishedAtDesc();
}
