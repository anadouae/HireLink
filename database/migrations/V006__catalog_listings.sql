-- HireLink Phase 3 — projection catalogue unifie (pagination SQL).
-- A appliquer UNIQUEMENT sur un schema isole (ex. hirelink_ext_phase03).
-- Ne pas executer sur hirelink sans autorisation.

CREATE TABLE IF NOT EXISTS catalog_listings (
  id                BIGINT(20)     NOT NULL AUTO_INCREMENT,
  origin            VARCHAR(16)    NOT NULL,
  ref_id            BIGINT(20)     NOT NULL,
  public_id         VARCHAR(40)    NOT NULL,
  source_code       VARCHAR(40)    NOT NULL,
  board_key         VARCHAR(80)    DEFAULT NULL,
  title             VARCHAR(200)   NOT NULL,
  company_name      VARCHAR(200)   NOT NULL,
  location_label    VARCHAR(200)   DEFAULT NULL,
  city              VARCHAR(120)   DEFAULT NULL,
  city_fold         VARCHAR(120)   DEFAULT NULL,
  country_code      VARCHAR(8)     DEFAULT NULL,
  contract_label    VARCHAR(60)    DEFAULT NULL,
  work_mode         VARCHAR(24)    DEFAULT NULL,
  skills_text       VARCHAR(500)   DEFAULT NULL,
  description       TEXT           DEFAULT NULL,
  origin_url        VARCHAR(500)   DEFAULT NULL,
  apply_url         VARCHAR(500)   DEFAULT NULL,
  url_quality       VARCHAR(20)    DEFAULT NULL,
  link_reliable     BIT(1)         NOT NULL DEFAULT 0,
  published_at      DATETIME(6)    DEFAULT NULL,
  published_trusted BIT(1)         NOT NULL DEFAULT 0,
  sort_at           DATETIME(6)    NOT NULL,
  dedupe_key        VARCHAR(64)    DEFAULT NULL,
  search_blob       TEXT           NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_catalog_origin_ref (origin, ref_id),
  UNIQUE KEY uk_catalog_public_id (public_id),
  KEY idx_catalog_sort (sort_at, id),
  KEY idx_catalog_country_city (country_code, city),
  KEY idx_catalog_source (source_code),
  KEY idx_catalog_dedupe (dedupe_key),
  KEY idx_catalog_contract (contract_label),
  KEY idx_catalog_work_mode (work_mode)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS catalog_for_you_run (
  run_id       VARCHAR(36)  NOT NULL,
  listing_id   BIGINT(20)   NOT NULL,
  match_score  DOUBLE       NOT NULL,
  created_at   DATETIME(6)  NOT NULL,
  PRIMARY KEY (run_id, listing_id),
  KEY idx_foryou_run_score (run_id, match_score, listing_id),
  KEY idx_foryou_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;