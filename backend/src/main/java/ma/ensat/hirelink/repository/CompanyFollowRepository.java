package ma.ensat.hirelink.repository;

import ma.ensat.hirelink.model.CompanyFollow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CompanyFollowRepository extends JpaRepository<CompanyFollow, Long> {

    List<CompanyFollow> findByCandidateUserIdOrderByCreatedAtDesc(Long candidateUserId);

    Optional<CompanyFollow> findByCandidateUserIdAndOrganizationId(Long candidateUserId, Long organizationId);

    long countByOrganizationId(Long organizationId);

    /** Seuls les identifiants sont lus : l'entreprise ne doit jamais voir qui la suit. */
    @Query("select f.candidateUserId from CompanyFollow f where f.organizationId = :organizationId")
    List<Long> findCandidateIdsByOrganization(@Param("organizationId") Long organizationId);
}
