-- HireLink — tables des offres externes.
-- Idempotent : un second passage ne modifie pas les lignes déjà présentes.
-- N'efface aucune table existante. À lancer sur la base choisie, jamais en aveugle sur une autre.
-- Hibernate (ddl-auto=update) crée les mêmes tables au démarrage si elles manquent.
-- Ce script reste la forme explicite, vérifiable avant un démarrage.

CREATE TABLE IF NOT EXISTS external_job_offers (
  id              BIGINT(20)    NOT NULL AUTO_INCREMENT,
  source          VARCHAR(40)   NOT NULL,
  external_id     VARCHAR(80)   NOT NULL,
  origin_url      VARCHAR(500)  NOT NULL,
  apply_url       VARCHAR(500)  DEFAULT NULL,
  company         VARCHAR(200)  NOT NULL,
  title           VARCHAR(200)  NOT NULL,
  location        VARCHAR(160)  DEFAULT NULL,
  contract_label  VARCHAR(60)   DEFAULT NULL,
  description     TEXT          DEFAULT NULL,
  skills          VARCHAR(500)  DEFAULT NULL,
  salary_min      INT(11)       DEFAULT NULL,
  salary_max      INT(11)       DEFAULT NULL,
  published_at    DATETIME(6)   DEFAULT NULL,
  last_synced_at  DATETIME(6)   NOT NULL,
  last_seen_at    DATETIME(6)   DEFAULT NULL,
  available       BIT(1)        NOT NULL,
  hidden_by_admin BIT(1)        NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_external_offer_source (source, external_id),
  KEY idx_external_offer_available (source, available)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS external_sync_runs (
  id              BIGINT(20)   NOT NULL AUTO_INCREMENT,
  source          VARCHAR(40)  NOT NULL,
  created_count   INT(11)      NOT NULL,
  updated_count   INT(11)      NOT NULL,
  withdrawn_count INT(11)      NOT NULL,
  rejected_count  INT(11)      NOT NULL,
  duplicate_count INT(11)      NOT NULL,
  kept_previous   BIT(1)       NOT NULL,
  message         VARCHAR(300) NOT NULL,
  finished_at     DATETIME(6)  NOT NULL,
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
