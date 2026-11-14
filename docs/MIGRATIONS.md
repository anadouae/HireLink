# Stratégie migrations HireLink

## Constat
`spring.jpa.hibernate.ddl-auto=update` (démo locale) crée automatiquement les tables manquantes. C’est pratique en développement, mais **non auditable** et risqué en production.

`application-prod.properties` fixe déjà `ddl-auto=validate`.

## Cible
1. **Install propre** : `database/apply-migrations.ps1 -Database <nom> -Fresh` (V000 puis V001…**V006**) puis démarrer avec `validate`.
2. **Mise à jour** : même script **sans** `-Fresh` — réapplique V001…V006 de façon idempotente.
3. **Démo actuelle** : conserver `update` jusqu’à bascule explicite.

## Fichiers
`database/migrations/V000` … `V006` + runner `database/apply-migrations.ps1`.

| Version | Contenu |
|---|---|
| V000 | Baseline (uniquement avec `-Fresh`) |
| V001 | Consentements légaux |
| V002 | Offres externes |
| V003 | Factures candidat (`organization_id` nullable) |
| V004 | Qualité URL / quarantaine |
| V005 | Boards ATS |
| V006 | `catalog_listings` + Pour toi |

Client SQL : paramètre `-Mysql` (défaut XAMPP). Le runner retire un éventuel BOM UTF-8.

## Exemples
```powershell
.\database\apply-migrations.ps1 -Database hirelink_lab -Fresh
.\database\apply-migrations.ps1 -Database hirelink_lab
```

## Scripts de lancement
`LANCER-HIRELINK.bat` / `LANCER-HIRELINK-DEMO.bat` : config locale (`update`).  
Lab `validate` : properties additionnel hors démo + base migrée V000–V006.
