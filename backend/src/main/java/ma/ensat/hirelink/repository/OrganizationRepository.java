package ma.ensat.hirelink.repository;

import ma.ensat.hirelink.model.Organization;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;

public interface OrganizationRepository extends JpaRepository<Organization, Long> {
    @Query("SELECT o FROM Organization o LEFT JOIN FETCH o.owner")
    java.util.List<Organization> findAllWithOwner();

    java.util.List<Organization> findTop10ByNameContainingIgnoreCaseOrIceContainingIgnoreCase(String name, String ice);

    /** Verrou de ligne : deux facturasions simultanées de la même entreprise ne reçoivent pas le même numéro. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM Organization o WHERE o.id = :id")
    Optional<Organization> findByIdForUpdate(@Param("id") Long id);
}
