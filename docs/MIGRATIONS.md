# Stratégie migrations HireLink

## Constat
`spring.jpa.hibernate.ddl-auto=update` (démo locale) crée automatiquement les tables manquantes (ex. `legal_acceptances` le 2026-11-12). C’est pratique en développement, mais **non auditable** et risqué en production.

`application-prod.properties` fixe déjà `ddl-auto=validate`.

## Cible
1. **Install propre** : appliquer `database/migrations/V000…` (+ incrémentales) puis démarrer avec `validate`.
2. **Mise à jour** : exécuter uniquement les scripts `V00x` manquants (`IF NOT EXISTS`).
3. **Démo actuelle** : conserver `update` jusqu’à validation explicite de cette stratégie (ne pas basculer `application.properties` sans OK).

## MariaDB
Scripts versionnés dans le dépôt : `database/migrations/V000…V002` + `database/apply-migrations.ps1`.  
Aucune dépendance à un dump hors dépôt pour une install propre.

Client SQL : paramètre `-Mysql` (défaut XAMPP `C:\xampp\mysql\bin\mysql.exe`). Sur une machine sans XAMPP, passer le chemin du client MariaDB/MySQL.

Le runner retire un éventuel BOM UTF-8 avant `source` (compatibilité MariaDB).

## Scripts de lancement
- `LANCER-HIRELINK.bat` / `LANCER-HIRELINK-DEMO.bat` : inchangés ; pointent toujours vers la config locale (`update`).
- Pour un lab `validate` : fichier properties additionnel (hors démo), ex.  
  `--spring.config.additional-location=file:…/hl-validate-lab.properties`  
  avec `spring.jpa.hibernate.ddl-auto=validate` et une base migrée.

## Preuve de contrôle
Voir `docs/security-control-20261112/RAPPORT_FINALISATION.md` (section migrations + `hirelink_validate_lab`).
