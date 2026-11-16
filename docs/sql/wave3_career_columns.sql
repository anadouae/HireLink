-- HireLink Vague 3 — colonnes profil structuré (ISO hirelink_iso uniquement)
-- Sauvegarde préalable requise. Hibernate ddl-auto=update les crée aussi au démarrage.
-- Ne pas exécuter sur la base démo `hirelink`.

ALTER TABLE users
  ADD COLUMN IF NOT EXISTS profile_experiences TEXT NULL,
  ADD COLUMN IF NOT EXISTS profile_educations TEXT NULL,
  ADD COLUMN IF NOT EXISTS country VARCHAR(80) NULL,
  ADD COLUMN IF NOT EXISTS pending_email VARCHAR(180) NULL;

-- MySQL < 8.0.12 n'a pas IF NOT EXISTS sur ADD COLUMN : utiliser la variante ci-dessous si besoin.
-- ALTER TABLE users ADD COLUMN profile_experiences TEXT NULL;
-- ALTER TABLE users ADD COLUMN profile_educations TEXT NULL;
-- ALTER TABLE users ADD COLUMN country VARCHAR(80) NULL;
-- ALTER TABLE users ADD COLUMN pending_email VARCHAR(180) NULL;
