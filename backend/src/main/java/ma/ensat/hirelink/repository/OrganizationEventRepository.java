package ma.ensat.hirelink.repository;

import ma.ensat.hirelink.model.OrganizationEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface OrganizationEventRepository extends JpaRepository<OrganizationEvent, Long> {
    @Query("select e from OrganizationEvent e join fetch e.organization join fetch e.createdBy where e.organization.id = :orgId order by e.startsAt asc")
    List<OrganizationEvent> findAllByOrganizationIdOrderByStartsAt(@Param("orgId") Long orgId);

    @Query("select e from OrganizationEvent e join fetch e.organization join fetch e.createdBy where e.id = :id and e.organization.id = :orgId")
    Optional<OrganizationEvent> findByIdAndOrganizationId(@Param("id") Long id, @Param("orgId") Long orgId);
}
