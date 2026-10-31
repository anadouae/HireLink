package ma.ensat.hirelink.repository;

import ma.ensat.hirelink.model.SentEmail;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface SentEmailRepository extends JpaRepository<SentEmail, Long> {

    List<SentEmail> findAllByOrderByCreatedAtDescIdDesc(Pageable page);

    @Modifying
    @Query("DELETE FROM SentEmail e WHERE e.createdAt < :before")
    int deleteOlderThan(@Param("before") Instant before);
}
