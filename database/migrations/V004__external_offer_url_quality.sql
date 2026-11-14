-- HireLink — qualité URL + quarantaine des offres externes (Phase 1).
-- Idempotent. N'efface aucune ligne. Ne touche pas aux offres internes (job_offers / applications).
-- À appliquer UNIQUEMENT sur une base de test / après autorisation explicite sur la démo.
-- Ne pas lancer en aveugle sur la base principale hirelink sans validation.

ALTER TABLE external_job_offers
  ADD COLUMN IF NOT EXISTS url_quality VARCHAR(20) NOT NULL DEFAULT 'UNKNOWN' AFTER apply_url,
  ADD COLUMN IF NOT EXISTS url_quality_reason VARCHAR(240) DEFAULT NULL AFTER url_quality,
  ADD COLUMN IF NOT EXISTS quarantined BIT(1) NOT NULL DEFAULT 0 AFTER hidden_by_admin,
  ADD COLUMN IF NOT EXISTS quarantine_reason VARCHAR(240) DEFAULT NULL AFTER quarantined,
  ADD COLUMN IF NOT EXISTS content_hash VARCHAR(64) DEFAULT NULL AFTER quarantine_reason;

-- MariaDB < 10.3.3 / MySQL sans IF NOT EXISTS sur ADD COLUMN : variante manuelle ci-dessous si besoin.
-- Vérifier information_schema avant rejeu sur moteurs anciens.

CREATE INDEX IF NOT EXISTS idx_external_offer_quarantine
  ON external_job_offers (source, quarantined, available);
