package ma.ensat.hirelink.repository;

import ma.ensat.hirelink.model.BillingInvoice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BillingInvoiceRepository extends JpaRepository<BillingInvoice, Long> {
    List<BillingInvoice> findAllByOrderByIssuedAtDesc();
}
