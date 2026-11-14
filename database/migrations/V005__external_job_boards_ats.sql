-- HireLink Phase 2 — boards ATS + elargissement external_id.
-- Idempotent autant que possible. A appliquer UNIQUEMENT sur un schema isole
-- (ex. hirelink_ext_phase02). NE PAS executer sur hirelink sans autorisation.

CREATE TABLE IF NOT EXISTS external_job_boards (
  id                 BIGINT(20)    NOT NULL AUTO_INCREMENT,
  provider           VARCHAR(40)   NOT NULL,
  board_key          VARCHAR(80)   NOT NULL,
  company_name       VARCHAR(200)  NOT NULL,
  reference_url      VARCHAR(500)  DEFAULT NULL,
  region_hint        VARCHAR(80)   DEFAULT NULL,
  enabled            BIT(1)        NOT NULL DEFAULT 0,
  attribution_notes  VARCHAR(500)  DEFAULT NULL,
  verification_notes VARCHAR(500)  DEFAULT NULL,
  last_success_at    DATETIME(6)   DEFAULT NULL,
  last_error         VARCHAR(300)  DEFAULT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_external_board (provider, board_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Colonnes offres (ignorer l'erreur si deja presentes selon moteur)
ALTER TABLE external_job_offers
  MODIFY COLUMN external_id VARCHAR(160) NOT NULL;

ALTER TABLE external_job_offers
  ADD COLUMN IF NOT EXISTS board_key VARCHAR(80) DEFAULT NULL AFTER external_id,
  ADD COLUMN IF NOT EXISTS country_code VARCHAR(8) DEFAULT NULL AFTER board_key;

CREATE INDEX IF NOT EXISTS idx_external_offer_board
  ON external_job_offers (board_key, available);