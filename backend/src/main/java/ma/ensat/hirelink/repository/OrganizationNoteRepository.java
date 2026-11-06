package ma.ensat.hirelink.repository;

import ma.ensat.hirelink.model.OrganizationNote;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface OrganizationNoteRepository extends JpaRepository<OrganizationNote, Long> {
    List<OrganizationNote> findAllByOrganizationIdAndOwnerIdOrderByUpdatedAtDesc(Long organizationId, Long ownerId);
    Optional<OrganizationNote> findByIdAndOrganizationIdAndOwnerId(Long id, Long organizationId, Long ownerId);
}
