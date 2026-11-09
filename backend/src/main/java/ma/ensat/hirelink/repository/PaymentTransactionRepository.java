package ma.ensat.hirelink.repository;

import ma.ensat.hirelink.model.PaymentTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, Long> {
    Optional<PaymentTransaction> findByIdempotencyKey(String idempotencyKey);
    boolean existsByInvoiceIdAndStatus(Long invoiceId, PaymentTransaction.Status status);
}
