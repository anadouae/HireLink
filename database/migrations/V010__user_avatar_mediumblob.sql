-- HireLink — photos de profil : user_avatars.data passe de TINYBLOB (255 octets) à MEDIUMBLOB (16 Mo).
-- Idempotent (MySQL 8+ / MariaDB). Le backend applique le même correctif au démarrage (AvatarColumnRepair).

SET @db := DATABASE();

SET @sql := (
  SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.COLUMNS
           WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'user_avatars' AND COLUMN_NAME = 'data'
             AND LOWER(DATA_TYPE) IN ('tinyblob', 'blob')),
    'ALTER TABLE user_avatars MODIFY COLUMN data MEDIUMBLOB NOT NULL',
    'SELECT 1'
  )
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
