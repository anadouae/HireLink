package ma.ensat.hirelink.service;

import ma.ensat.hirelink.model.BillingInvoice;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

/** Fournisseur fictif. Il ne reçoit aucune donnée bancaire et ne contacte aucun réseau de paiement. */
@Component
public class MockPaymentProvider implements PaymentProvider {

    @Override
    public String name() {
        return "MOCK";
    }

    @Override
    public Outcome charge(BillingInvoice invoice, String scenario) {
        String reference = "SIM-" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        if ("SUCCESS".equals(scenario)) {
            return new Outcome(true, reference, null);
        }
        if ("FAILURE".equals(scenario)) {
            return new Outcome(false, reference, "Paiement refusé par le fournisseur fictif.");
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Scénario de simulation inconnu.");
    }
}
