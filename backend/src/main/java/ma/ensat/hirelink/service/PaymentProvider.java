package ma.ensat.hirelink.service;

import ma.ensat.hirelink.model.BillingInvoice;

/**
 * Fournisseur de paiement. L'implémentation actuelle est fictive.
 * Une intégration réelle pourra s'ajouter ici sans réécrire la facturation.
 */
public interface PaymentProvider {
    String name();

    Outcome charge(BillingInvoice invoice, String scenario);

    record Outcome(boolean success, String reference, String reason) {}
}
