package ma.ensat.hirelink.repository;

import ma.ensat.hirelink.model.ExternalJobBoard;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ExternalJobBoardRepository extends JpaRepository<ExternalJobBoard, Long> {
    List<ExternalJobBoard> findByProvider(String provider);
    Optional<ExternalJobBoard> findByProviderAndBoardKey(String provider, String boardKey);
}