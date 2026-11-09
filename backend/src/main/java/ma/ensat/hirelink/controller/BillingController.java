package ma.ensat.hirelink.controller;

import ma.ensat.hirelink.model.CommercialPlan;
import ma.ensat.hirelink.model.Role;
import ma.ensat.hirelink.model.User;
import ma.ensat.hirelink.service.BillingCatalogService;
import ma.ensat.hirelink.service.PaymentService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Abonnement et factures. Le paiement est simulé : le serveur fixe le montant et n'active
 * une formule payante qu'après une transaction réussie.
 */
@org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('CANDIDAT','RECRUTEUR','ADMINISTRATEUR')")
@RestController
@RequestMapping("/api/v1/billing")
public class BillingController {

    private final BillingCatalogService billingCatalogService;
    private final PaymentService payments;

    public BillingController(BillingCatalogService billingCatalogService, PaymentService payments) {
        this.billingCatalogService = billingCatalogService;
        this.payments = payments;
    }

    @GetMapping("/plans")
    @Transactional(readOnly = true)
    public List<Map<String, Object>> plans(@AuthenticationPrincipal User user) {
        String audience = user.getRole() == Role.CANDIDAT ? "CANDIDAT" : "RECRUTEUR";
        List<Map<String, Object>> out = new ArrayList<>();
        for (CommercialPlan plan : billingCatalogService.plans()) {
            if (user.getRole() == Role.ADMINISTRATEUR || audience.equals(plan.getAudience())) {
                out.add(billingCatalogService.toPlan(plan));
            }
        }
        return out;
    }

    @PostMapping("/subscribe")
    public Map<String, Object> subscribe(@AuthenticationPrincipal User user, @RequestBody(required = false) Map<String, Object> body) {
        String cycle = body != null && "year".equals(String.valueOf(body.get("cycle"))) ? "year" : "month";
        return payments.prepare(user, cycle);
    }

    @DeleteMapping("/subscription")
    public Map<String, Object> cancel(@AuthenticationPrincipal User user) {
        return payments.cancel(user);
    }

    @GetMapping("/invoices")
    public List<Map<String, Object>> invoices(@AuthenticationPrincipal User user, @RequestParam(defaultValue = "mine") String scope) {
        return payments.list(user, scope);
    }

    @GetMapping("/invoices/{id}")
    public Map<String, Object> invoice(@AuthenticationPrincipal User user, @PathVariable Long id) {
        return payments.one(user, id);
    }

    @GetMapping("/invoices/{id}/pdf")
    public ResponseEntity<byte[]> pdf(@AuthenticationPrincipal User user, @PathVariable Long id) {
        byte[] body = payments.pdf(user, id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"facture-" + id + ".pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(body);
    }

    @PostMapping("/payments")
    public Map<String, Object> pay(@AuthenticationPrincipal User user, @RequestBody Map<String, Object> body) {
        return payments.pay(user, body);
    }

    @GetMapping("/payments/{id}")
    public Map<String, Object> payment(@AuthenticationPrincipal User user, @PathVariable Long id) {
        return payments.payment(user, id);
    }

    @GetMapping("/payments")
    public Map<String, Object> paymentByKey(@AuthenticationPrincipal User user, @RequestParam String idempotencyKey) {
        return payments.paymentByKey(user, idempotencyKey);
    }
}
