-- Factures candidat : pas d'organisation (Performance).
-- Sans NULL autorisé, INSERT échoue avec « Une valeur est trop longue ou déjà utilisée ».
ALTER TABLE billing_invoices
  MODIFY COLUMN organization_id BIGINT NULL;
