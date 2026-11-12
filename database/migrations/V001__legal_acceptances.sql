-- HireLink — table des acceptations légales versionnées (CGU / confidentialité)
-- Idempotent. N'écrit aucune ligne métier. Ne touche pas aux données historiques.
-- Alternative contrôlée à la création implicite via spring.jpa.hibernate.ddl-auto=update.

CREATE TABLE IF NOT EXISTS legal_acceptances (
  id BIGINT NOT NULL AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  purpose VARCHAR(32) NOT NULL,
  document_version VARCHAR(64) NOT NULL,
  source VARCHAR(32) NOT NULL,
  granted BIT(1) NOT NULL,
  accepted_at DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  KEY idx_legal_user (user_id),
  KEY idx_legal_purpose (purpose)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
