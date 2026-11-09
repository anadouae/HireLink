package ma.ensat.hirelink.repository;

import ma.ensat.hirelink.model.BillingInvoice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface BillingInvoiceRepository extends JpaRepository<BillingInvoice, Long> {
    List<BillingInvoice> findAllByOrderByIssuedAtDesc();
    List<BillingInvoice> findByOrganizationIdOrderByIssuedAtDesc(Long organizationId);
    List<BillingInvoice> findByDebtorUserIdOrderByIssuedAtDesc(Long debtorUserId);
    List<BillingInvoice> findByOrganizationIdAndStatusInOrderByIssuedAtDesc(Long organizationId, Collection<String> statuses);
    List<BillingInvoice> findByDebtorUserIdAndOrganizationIsNullAndStatusInOrderByIssuedAtDesc(Long debtorUserId, Collection<String> statuses);

    /** Sequenceurs de numérotation : une facture par entreprise et par mois, sans trou ni doublon. */
    long countByOrganizationIdAndNumberStartingWith(Long organizationId, String prefix);
    long countByDebtorUserIdAndNumberStartingWith(Long debtorUserId, String prefix);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from BillingInvoice i where i.id = :id")
    Optional<BillingInvoice> findByIdForUpdate(@Param("id") Long id);
}
