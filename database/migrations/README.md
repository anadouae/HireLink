# Migrations SQL HireLink (MariaDB / MySQL)

## Objectif
Rendre le schéma **explicite et reproductible** sans dépendre de `spring.jpa.hibernate.ddl-auto=update` en production.

| Environnement | `ddl-auto` recommandé | Migrations |
|---|---|---|
| Install propre | `validate` (après scripts) | `V000` (`-Fresh`) puis `V001`…`V014` |
| Mise à jour | `validate` | `V001`…`V014` (idempotents) |
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
| `V008__catalog_salary_experience_sector.sql` | Filtres salaire / expérience / secteur du catalogue |
| `V009__support_requests.sql` | Messages de la page Support |
| `V010__user_avatar_mediumblob.sql` | Photos de profil : `user_avatars.data` en `MEDIUMBLOB` |
| `V011__organization_access.sql` | Accès des entreprises (désactivation réversible, retrait) |
| `V012__cv_import_drafts.sql` | Brouillons d’import CV et versions de profil restaurables |
| `V013__billing_promotions.sql` | Tables des promotions de facturation et leur journal d’événements |
| `R013__billing_promotions.sql` | Rollback V013 (supprime les deux tables et leurs données) |
| `V014__align_profile_schema.sql` | Colonnes de profil absentes du baseline et présentes sur HireLink |
| `R014__align_profile_schema.sql` | Rollback V014 (supprime ces cinq colonnes et leurs données) |

Il n’y a pas de `V007` (numéro non utilisé). Les tables `billing_promotions` et
`billing_promotion_events`, auparavant créées uniquement par Hibernate, sont désormais
gérées explicitement par V013 afin de fonctionner avec `ddl-auto=validate`.

`V001`–`V012` sont conçus pour être **ré-exécutables** (`IF NOT EXISTS` / colonnes testées dans `information_schema`). `-Fresh` est le seul mode destructeur (DROP DATABASE).

## Application (PowerShell)

```powershell
# Install propre isolée (recommandé pour tests)
.\database\apply-migrations.ps1 -Database hirelink_lab -Fresh

# Mise à jour d'une base existante (applique V001 à V012, sans effet sur ce qui existe déjà)
.\database\apply-migrations.ps1 -Database hirelink_lab

# Machine neuve — après sauvegarde si données existantes
.\database\apply-migrations.ps1 -Database hirelink -Fresh
```

Le runner **n’applique pas** V000 sans `-Fresh` (évite d’écraser une base peuplée) et refuse
`-Fresh` sur `hirelink`. V000 contient des `DROP TABLE` : ne l’exécutez jamais sur une base
qui contient des données; vérifiez `SELECT DATABASE()` avant toute exécution manuelle.

Pour annuler V013, exécutez `R013__billing_promotions.sql`; pour annuler V014, exécutez
`R014__align_profile_schema.sql`. Exécutez les rollbacks uniquement sur la base de test ciblée.

## Validation Hibernate

```
spring.jpa.hibernate.ddl-auto=validate
```

Voir aussi `docs/MIGRATIONS.md` et `docs/HIRELINK_INSTALL_WINDOWS.md`.
