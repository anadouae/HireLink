-- Rollback V012 — à exécuter uniquement après vérification des données et sur la base ciblée.
SET FOREIGN_KEY_CHECKS = 0;
DROP TABLE IF EXISTS cv_import_items;
DROP TABLE IF EXISTS cv_import_drafts;
DROP TABLE IF EXISTS profile_versions;
SET FOREIGN_KEY_CHECKS = 1;
