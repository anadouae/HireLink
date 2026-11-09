# Données de recette du lot L1 — inventaire et identification

Date de la recette : **2026-11-09**, backend en service (PID 21428).
Statut : **aucune ligne de cette liste n'a été supprimée.** Purge soumise à l'autorisation écrite du propriétaire, sauvegarde complète préalable obligatoire.

Sauvegarde de référence : `backup-db/hirelink-avant-L1-20261109.sql` (1 494 723 octets, 26 tables, restaurée et vérifiée avant l'intervention).

## 1. Racines de la recette

| Objet | Identifiant | Comment le reconnaître |
|---|---|---|
| Offre | `job_offers.id = 157` | `title = 'Offre de test L1 — convocation entretien'`, `created_at = 2026-11-09 10:14:22`, `published = 0` (dépubliée en fin de recette) |
| Dossier candidat | `applications.id = 259` | `candidate_id = 10` (Sara El Amrani), `cover_letter = 'Lettre de recette L1 — dossier de test dedie.'`, `status = INTERVIEW`, `interview_status = CANCELLED` |
| Dossier candidat | `applications.id = 260` | `candidate_id = 6` (Youssef Idrissi), `cover_letter = 'Second dossier de recette (rappel J-1).'`, `status = INTERVIEW`, `interview_status = SCHEDULED`, `interview_reminder_sent = 1` |

Les deux dossiers portent des champs d'entretien remplis par la recette (`interview_at`, `interview_location`, `interview_type`, `interview_kind`, `interview_time_zone`, `interview_duration_minutes`, `interview_cancel_reason` pour 259). Les colonnes inertes `interview_confirmation` et `interview_candidate_note` sont restées à `NULL` sur ces deux lignes.

## 2. Lignes filles créées par la recette

| Table | Identifiants | Nombre | Règle d'identification indépendante des identifiants |
|---|---|---|---|
| `application_status_history` | 592 → 599 | 8 | `application_id IN (259,260)` |
| `notifications` | 1484 → 1511 | 28 | `link IN ('application:259','application:260','interview:259','interview:260','offer:157')` |
| `sent_emails` | 294 → 319 | 26 | `subject LIKE '%Offre de test L1%' OR subject LIKE '%Sara El Amrani%' OR subject LIKE '%Youssef Idrissi%'`, toutes avec `created_at > '2026-11-09 10:00:00'` |
| `audit_logs` | 1480 → 1503 | 24 | `created_at > '2026-11-09 10:00:00'` ; cibles uniquement `OFFER 157`, `APPLICATION 259/260`, `ENDPOINT …/259|260/interview`, `USER 2/4/6/10/13` |
| `job_offer_tags` | `job_offer_id = 157`, `tag_id IN (1,2)` | 2 | `job_offer_id = 157` |
| `user_sessions` | 6 sessions (acteurs 2, 4, 6, 10 ×13) | 6 | `created_at > '2026-11-09 10:00:00'` ; **révoquées** le 2026-11-09 avec `revoked_reason = 'RECIPE_L1'` |

**Ne jamais supprimer les lignes `user_sessions`** : la table est chaînée par `previous_hash`, une suppression casse la chaîne des lignes postérieures. La révocation suffit et a été faite.

Aucune ligne dans `offer_reports`, `scorecard_criteria`, `scorecard_entries`, `company_follows`, `ai_usage_logs`, `email_otps`, `memberships`, `users`, `organizations` n'appartient à la recette (vérifié par requêtes).

## 3. Preuves déjà acquises sur ces données

- **Aucun SMTP réel** : les 26 lignes de la recette sont `status = 'LOGGED'`, `error = NULL`. Les seules lignes `SENT` de la table sont des données de démonstration antérieures (2026-11-01). 26 lignes « [MAIL non envoyé : SMTP absent] » dans le journal, aucune autre.
- **Aucun doublon de rappel J-1** : exactement 3 lignes de rappel à 10:25:52 (dossier 260), un destinataire différent chacune (`y.idrissi@outlook.com`, `n.bennani@atlasdigital.ma`, `m.alaoui@atlasdigital.ma`). Entre 10:25:53 et 10:45:08 — soit plus d'un cycle de sondes de 15 minutes — 0 nouveau `sent_emails` et 0 nouvelle notification.
- **Aucun dossier métier touché** : `SELECT COUNT(*) FROM applications WHERE id < 259 AND updated_at > '2026-11-09 10:00:00'` = 0.

## 4. Compteurs de contrôle (à comparer après purge)

```
applications                219   (max id avant recette : 217)
job_offers                  118   (max id avant recette : 127)
notifications              1211   (dont 28 de recette)
sent_emails                  85   (dont 26 de recette)
audit_logs                 1089   (dont 24 de recette)
application_status_history  509   (dont 8 de recette)
```

## 5. Requêtes de localisation (lecture seule)

```sql
SELECT id, title, published FROM job_offers WHERE id = 157;
SELECT id, candidate_id, job_offer_id, status, interview_status, cover_letter
  FROM applications WHERE job_offer_id = 157;
SELECT id, application_id, from_status, to_status
  FROM application_status_history WHERE application_id IN (259,260);
SELECT id, user_id, kind, link, created_at
  FROM notifications
 WHERE link IN ('application:259','application:260','interview:259','interview:260','offer:157');
SELECT id, recipient, subject, status FROM sent_emails
 WHERE created_at > '2026-11-09 10:00:00';
SELECT id, action, target_type, target_id, created_at FROM audit_logs
 WHERE created_at > '2026-11-09 10:00:00';
SELECT tag_id FROM job_offer_tags WHERE job_offer_id = 157;
```

## 6. Ce qui reste visible côté démonstration

L'offre 157 est dépubliée : elle n'apparaît ni sur le marché public, ni dans les listes recruteur filtrées sur les offres publiées. Elle demeure visible dans l'espace du recruteur `n.bennani@atlasdigital.ma` tant qu'elle n'est pas supprimée. Les deux dossiers de recette apparaissent dans le pipeline de cette offre et dans la liste « Mes candidatures » des comptes candidats 10 et 6 jusqu'à purge.
