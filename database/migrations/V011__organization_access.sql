-- HireLink — accès des entreprises géré par l'administration (désactivation réversible, retrait).
-- Colonnes : statut d'accès, date du changement, comptes et offres mis en pause (rétablis à la réactivation).
-- Idempotent (MySQL 8+ / MariaDB). En démo locale, Hibernate (ddl-auto=update) crée les mêmes colonnes.

SET @db := DATABASE();

SET @sql := (
  SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.COLUMNS
           WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'organizations' AND COLUMN_NAME = 'access_status'),
    'SELECT 1',
    'ALTER TABLE organizations ADD COLUMN access_status VARCHAR(16) NULL'
  )
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := (
  SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.COLUMNS
           WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'organizations' AND COLUMN_NAME = 'access_changed_at'),
    'SELECT 1',
    'ALTER TABLE organizations ADD COLUMN access_changed_at DATETIME(6) NULL'
  )
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := (
  SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.COLUMNS
           WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'organizations' AND COLUMN_NAME = 'paused_member_ids'),
    'SELECT 1',
    'ALTER TABLE organizations ADD COLUMN paused_member_ids TEXT NULL'
  )
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := (
  SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.COLUMNS
           WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'organizations' AND COLUMN_NAME = 'paused_offer_ids'),
    'SELECT 1',
    'ALTER TABLE organizations ADD COLUMN paused_offer_ids TEXT NULL'
  )
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
