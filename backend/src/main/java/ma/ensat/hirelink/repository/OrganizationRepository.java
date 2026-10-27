package ma.ensat.hirelink.repository;

import ma.ensat.hirelink.model.Organization;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OrganizationRepository extends JpaRepository<Organization, Long> {
    @org.springframework.data.jpa.repository.Query("SELECT o FROM Organization o LEFT JOIN FETCH o.owner")
    java.util.List<Organization> findAllWithOwner();
    Optional<Organization> findByOwnerId(Long ownerId);
    List<Organization> findByVerifiedTrue();
}
