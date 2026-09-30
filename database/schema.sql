-- HireLink V1 — schéma de référence (Hibernate ddl-auto=update peut aussi créer les tables)

CREATE DATABASE IF NOT EXISTS hirelink
  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE hirelink;

CREATE TABLE IF NOT EXISTS users (
  id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
  email              VARCHAR(160) NOT NULL UNIQUE,
  password_hash      VARCHAR(255) NULL,
  full_name          VARCHAR(160) NOT NULL,
  role               VARCHAR(32)  NOT NULL,
  active             TINYINT(1)   NOT NULL DEFAULT 1,
  must_set_password  TINYINT(1)   NOT NULL DEFAULT 0,
  email_verified     TINYINT(1)   NOT NULL DEFAULT 0,
  google_sub         VARCHAR(128) NULL UNIQUE,
  skills             VARCHAR(500) NULL,
  company            VARCHAR(120) NULL,
  created_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS email_otps (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  email       VARCHAR(160) NOT NULL,
  purpose     VARCHAR(32)  NOT NULL,
  code        VARCHAR(16)  NOT NULL,
  expires_at  TIMESTAMP    NOT NULL,
  consumed    TINYINT(1)   NOT NULL DEFAULT 0,
  created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_otp_email_purpose (email, purpose)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS job_offers (
  id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
  recruiter_id       BIGINT       NOT NULL,
  title              VARCHAR(200) NOT NULL,
  description        TEXT         NOT NULL,
  required_skills    VARCHAR(500),
  location           VARCHAR(120),
  contract_type      VARCHAR(60),
  response_sla_days  INT          NOT NULL DEFAULT 7,
  published          TINYINT(1)   NOT NULL DEFAULT 0,
  created_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_job_recruiter FOREIGN KEY (recruiter_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS applications (
  id                BIGINT AUTO_INCREMENT PRIMARY KEY,
  candidate_id      BIGINT      NOT NULL,
  job_offer_id      BIGINT      NOT NULL,
  status            VARCHAR(32) NOT NULL,
  match_score       DOUBLE,
  rejection_reason  VARCHAR(500),
  submitted_at      TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at        TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT uq_candidate_offer UNIQUE (candidate_id, job_offer_id),
  CONSTRAINT fk_app_candidate FOREIGN KEY (candidate_id) REFERENCES users(id),
  CONSTRAINT fk_app_offer FOREIGN KEY (job_offer_id) REFERENCES job_offers(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
