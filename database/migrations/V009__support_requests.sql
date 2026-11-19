-- Messages de la page Support (signalements, questions, précisions). Idempotent.
CREATE TABLE IF NOT EXISTS support_requests (
  id BIGINT NOT NULL AUTO_INCREMENT,
  topic VARCHAR(20) NOT NULL,
  name VARCHAR(120) NOT NULL,
  email VARCHAR(160) NOT NULL,
  subject VARCHAR(160) NOT NULL,
  message VARCHAR(4000) NOT NULL,
  reference VARCHAR(300) NULL,
  user_id BIGINT NULL,
  user_role VARCHAR(20) NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'NOUVEAU',
  created_at DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  KEY idx_support_created (created_at),
  KEY idx_support_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
