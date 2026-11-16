-- HireLink — filtres serveur salaire / expérience / secteur sur catalog_listings.
-- Idempotent (MySQL 8+ / MariaDB).

SET @db := DATABASE();

SET @sql := (
  SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.COLUMNS
           WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'catalog_listings' AND COLUMN_NAME = 'salary_min'),
    'SELECT 1',
    'ALTER TABLE catalog_listings ADD COLUMN salary_min INT NULL'
  )
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := (
  SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.COLUMNS
           WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'catalog_listings' AND COLUMN_NAME = 'salary_max'),
    'SELECT 1',
    'ALTER TABLE catalog_listings ADD COLUMN salary_max INT NULL'
  )
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := (
  SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.COLUMNS
           WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'catalog_listings' AND COLUMN_NAME = 'experience_level'),
    'SELECT 1',
    'ALTER TABLE catalog_listings ADD COLUMN experience_level VARCHAR(16) NULL'
  )
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := (
  SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.COLUMNS
           WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'catalog_listings' AND COLUMN_NAME = 'sector'),
    'SELECT 1',
    'ALTER TABLE catalog_listings ADD COLUMN sector VARCHAR(120) NULL'
  )
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := (
  SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.STATISTICS
           WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'catalog_listings' AND INDEX_NAME = 'idx_catalog_salary'),
    'SELECT 1',
    'ALTER TABLE catalog_listings ADD KEY idx_catalog_salary (salary_max, salary_min)'
  )
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := (
  SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.STATISTICS
           WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'catalog_listings' AND INDEX_NAME = 'idx_catalog_experience'),
    'SELECT 1',
    'ALTER TABLE catalog_listings ADD KEY idx_catalog_experience (experience_level)'
  )
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
