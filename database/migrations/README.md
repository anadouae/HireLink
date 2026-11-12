# Migrations SQL HireLink (MariaDB / MySQL)

## Objectif
Rendre le schéma **explicite et reproductible** sans dépendre de `spring.jpa.hibernate.ddl-auto=update` en production.

| Environnement | `ddl-auto` recommandé | Migrations |
|---|---|---|
| Install propre | `validate` (après scripts) | `V000` puis `V001`… |
| Mise à jour | `validate` | uniquement les `V00x` manquants |
| Démo locale actuelle | `update` (inchangé tant que non validé) | optionnel |

La démo `8081` / base `hirelink` **n’est pas modifiée** par ces scripts tant que vous ne les exécutez pas explicitement dessus.

## Fichiers
| Fichier | Rôle |
|---|---|
| `V000__schema_baseline_mariadb.sql` | Schéma complet exporté (CREATE TABLE) compatible MariaDB |
| `V001__legal_acceptances.sql` | Table consentements (idempotent `IF NOT EXISTS`) |
| `V002__external_offers.sql` | Offres externes + sync (idempotent) |

`V000` inclut déjà les tables de `V001`/`V002` lorsqu’il est pris sur une base à jour. Les scripts `V001`/`V002` restent utiles pour une base plus ancienne.

## Application (PowerShell)
```powershell
# Install propre isolée
.\database\apply-migrations.ps1 -Database hirelink_validate_lab -Fresh

# Mise à jour d'une base existante (n'applique que V001+V002 idempotents)
.\database\apply-migrations.ps1 -Database hirelink_ancienne
```

## Validation Hibernate
Après migrations sur une base isolée :
```
spring.jpa.hibernate.ddl-auto=validate
```
Le démarrage Spring doit réussir sans altérer le schéma. Voir `docs/MIGRATIONS.md`.
