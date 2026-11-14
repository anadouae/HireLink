# Migrations SQL HireLink (MariaDB / MySQL)

## Objectif
Rendre le schéma **explicite et reproductible** sans dépendre de `spring.jpa.hibernate.ddl-auto=update` en production.

| Environnement | `ddl-auto` recommandé | Migrations |
|---|---|---|
| Install propre | `validate` (après scripts) | `V000` (`-Fresh`) puis `V001`…`V006` |
| Mise à jour | `validate` | `V001`…`V006` (idempotents) |
| Démo locale | `update` possible | optionnel si schéma déjà aligné |

## Fichiers

| Fichier | Rôle |
|---|---|
| `V000__schema_baseline_mariadb.sql` | Schéma baseline (`CREATE TABLE`) — **uniquement** avec `-Fresh` |
| `V001__legal_acceptances.sql` | Consentements légaux |
| `V002__external_offers.sql` | Offres externes + sync |
| `V003__billing_invoice_org_nullable.sql` | Factures candidat (`organization_id` NULL) |
| `V004__external_offer_url_quality.sql` | Qualité URL / quarantaine |
| `V005__external_job_boards_ats.sql` | Boards ATS + colonnes |
| `V006__catalog_listings.sql` | Projection catalogue + Pour toi |

`V001`–`V006` sont conçus pour être **ré-exécutables** (`IF NOT EXISTS` / `MODIFY` idempotent). `-Fresh` est le seul mode destructeur (DROP DATABASE).

## Application (PowerShell)

```powershell
# Install propre isolée (recommandé pour tests)
.\database\apply-migrations.ps1 -Database hirelink_lab -Fresh

# Mise à jour d'une base déjà migrée jusqu'à V002 (ajoute V003–V006)
.\database\apply-migrations.ps1 -Database hirelink_lab

# Clone professeur (machine neuve) — après sauvegarde si données existantes
.\database\apply-migrations.ps1 -Database hirelink -Fresh
```

Le runner **n’applique pas** V000 sans `-Fresh` (évite d’écraser une base peuplée).

## Validation Hibernate

```
spring.jpa.hibernate.ddl-auto=validate
```

Voir aussi `docs/MIGRATIONS.md` et `docs/HIRELINK_INSTALL_WINDOWS.md`.
