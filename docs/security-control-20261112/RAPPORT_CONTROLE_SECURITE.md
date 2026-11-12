# HireLink — Contrôle de sécurité avant publication (refonte UX)

**Date :** 2026-11-12  
**Branche / arbre :** working tree local (aucun commit, aucun push)  
**Démo conservée :** API `8081` + front `5500` + base `hirelink`  
**Environnement de test UI :** API `8093` + front `5513` + base `hirelink_ux_check`

---

## 1. Écart offres 119 ? 118

### Verdict
Une offre a été **dépubliée puis hard-deleted** sur la base principale `hirelink` le 2026-11-12 matin. Ce n’est **pas** un effet du nouveau comptage UX ni du DDL `legal_acceptances`.

### Offre concernée
| Champ | Valeur |
|---|---|
| `id` | **158** |
| Titre (audit `detail`) | développeur de base de données/développeuse de base de données |
| Publiée | 2026-11-10 09:42:28 par `n.bennani@atlasdigital.ma` (user 2) |
| Dépublication | 2026-11-12 10:20:22 par `recruteur@hirelink.ma` (user 11) |
| Suppression | 2026-11-12 10:20:30 par `recruteur@hirelink.ma` (user 11) |
| Présence live | **absente** (`SELECT id FROM job_offers WHERE id=158` ? vide) |

### Preuves SQL (lecture seule)

```sql
-- Comptage live
SELECT COUNT(*) FROM hirelink.job_offers;                 -- 118
SELECT id FROM hirelink.job_offers WHERE id = 158;        -- 0 ligne

-- Journal d’audit
SELECT id, action, actor_email, target_id, detail, created_at
FROM hirelink.audit_logs
WHERE target_id = '158' AND action LIKE 'OFFER%'
ORDER BY created_at;
-- 1541 OFFER_PUBLISHED   n.bennani@...  2026-11-10 09:42:28
-- 1571 OFFER_UNPUBLISHED recruteur@...  2026-11-12 10:20:22
-- 1572 OFFER_DELETED     recruteur@...  2026-11-12 10:20:30
```

### Comparaison sauvegarde de référence
- Dump `backup-db/hirelink-avant-L1-20261109.sql` : max id offres **127**, **pas** d’id 158 (offre postérieure au dump).
- Dump soir `HireLink-sauvegardes/hirelink-20261109-avant-integration.sql` : ensemble d’ids aligné avec le live actuel (**118**), **sans** 158.
- Chronologie cohérente avec un pic temporaire à **119** (après publication du 10/11) puis retour à **118** après suppression du 12/11 ~10:20 (fenêtre d’audit UI sur la démo).

### Écritures effectuées dans ce contrôle
**Aucune** restauration, **aucune** réinsertion de l’offre 158, **aucune** modification de `job_offers` sur `hirelink`.

---

## 2. Table `legal_acceptances` sur la base principale

### Pourquoi elle est apparue dans `hirelink`
1. L’entité JPA `LegalAcceptance` a été ajoutée avec la refonte (consentements versionnés).
2. `spring.jpa.hibernate.ddl-auto=update` (`application.properties` L19) crée / aligne le **schéma** au démarrage.
3. Redémarrage démo observé : `CREATE_TIME = 2026-11-12 13:06:33` sur `hirelink.legal_acceptances`.
4. **0 ligne métier** sur `hirelink` après contrôle (`COUNT(*) = 0`) : Hibernate n’a fait que le DDL, pas d’acceptations historiques réécrites.

### Preuves

```sql
SELECT table_schema, table_name, create_time, table_rows
FROM information_schema.tables
WHERE table_name = 'legal_acceptances';
-- hirelink            2026-11-12 13:06:33   ~0
-- hirelink_ux_check   2026-11-12 13:50:56   (puis 2 lignes de test isolé uniquement)

SELECT COUNT(*) FROM hirelink.legal_acceptances;          -- 0
SELECT COUNT(*) FROM hirelink_ux_check.legal_acceptances; -- 2 (tests consentement isolés de ce contrôle)
```

### Autres écarts de schéma vs dump du 09/11
Tables présentes en live et absentes du dump `hirelink-20261109-avant-integration.sql` :
- `legal_acceptances` ? **aujourd’hui** (refonte consentements + `ddl-auto=update`)
- `external_job_offers`, `external_sync_runs` ? déjà hors dump du 09/11 (évolution produit antérieure, pas ce contrôle)

Aucune table historique vidée / restaurée. Comptages métier principaux inchangés pendant le contrôle :

| Métrique `hirelink` | Valeur |
|---|---|
| users | 126 |
| job_offers | 118 |
| applications | 220 |
| entretiens (`interview_at IS NOT NULL`) | 37 |
| legal_acceptances (lignes) | 0 |

### Comportement `ddl-auto=update`
- Crée les tables / colonnes manquantes au boot.
- **Ne versionne pas** les migrations, **n’est pas** reproductible de façon auditée sur install propre autant qu’un script SQL idempotent.
- Risque : divergence silencieuse entre environnements.

### Contrôle des futures migrations
Script ajouté (non exécuté de force sur la prod dans ce contrôle) :

- `database/migrate_legal_acceptances.sql` — `CREATE TABLE IF NOT EXISTS` idempotent, **aucune** ligne métier.

**Recommandation (sans exécution ici) :** pour la publication, basculer la prod vers `ddl-auto=validate` (ou `none`) + scripts SQL versionnés ; conserver `update` uniquement en local/dev.

### Écritures effectuées
- **Lecture** sur `hirelink` uniquement pour ce volet.
- **Écritures isolées** sur `hirelink_ux_check` : 1 utilisateur de test + 2 `legal_acceptances` (validation consentement).
- **Aucune** suppression de table, **aucune** restauration de sauvegarde.

---

## 3. Validation fonctionnelle de la refonte (base isolée)

### Dispositif
- Clone `hirelink_ui_audit` ? `hirelink_ux_check` (34 users / 54 offres).
- Backend `8093`, frontend `5513`.
- Playwright + interception CSP `8081?8093` (la CSP front n’autorise que `:8081` en `connect-src`).
- Session injectée via login API isolé (pas d’écriture sur `hirelink`).

### Matrice
Voir `ux-matrix.md` : **29 PASS / 0 FAIL / 0 BLOCKED**.

Synthèse par surface :

| Surface | Résultat |
|---|---|
| Candidatures Kanban / Liste | PASS (captures `02`, `03`) |
| Fusion ex-Candidats ? Liste | PASS (redirect pipeline) |
| Assistant IA synthèse CV | PASS (`05`) |
| Profil / CV | PASS (`08`, `09`) |
| Entretiens / Agenda | PASS (`06`, `07`) |
| Entreprises suivies / notifications | PASS (`10`, `11`) |
| Admin offres / signalements / sources | PASS (`12`–`14`) |
| Inscription CGU / confidentialité UI | PASS (`15`–`17`) |
| Validation serveur consentements | PASS (400 sans versions ; 200 avec ; unit tests 3/3) |
| Checkout simulé | PASS affichage + absence débit réel (`18`) |

### Preuve serveur consentement (isolé)

```
verify-register-otp sans versions ? HTTP 400
  "Acceptez les Conditions générales et la Politique de confidentialité…"
verify-register-otp avec cgu:2026-11-12 + privacy:2026-11-12 ? HTTP 200
? 2 lignes TERMS/PRIVACY dans hirelink_ux_check seulement
LegalConsentServiceTest ? Tests run: 3, Failures: 0
```

---

## 4. Sécurité / non-régression démo

| Contrôle | Statut |
|---|---|
| Démo `8081` UP | OK |
| Front démo `5500` | OK (HTTP 200) |
| Données historiques `hirelink` | Conservées (hors offre 158 déjà absente avant ce contrôle) |
| Commit / push | **Non effectués** |
| Drop table / restore dump | **Non effectués** |

---

## 5. Écarts, causes, corrections éventuellement nécessaires

| # | Écart | Cause | Action proposée (attendre votre OK) |
|---|---|---|---|
| E1 | Offres 119?118 | Hard-delete offre **#158** par `recruteur@hirelink.ma` le 12/11 10:20 | Décider : laisser ainsi, ou restaurer manuellement depuis audit/backup si besoin métier |
| E2 | Table `legal_acceptances` vide créée sur `hirelink` | `ddl-auto=update` au boot 13:06 | Garder la table ; pour la suite : migration SQL + `validate`/`none` en prod |
| E3 | Checkout montants « — » | Plan/montant non peuplé sur le scénario capturé | Vérifier binding plan?totaux si requis avant publication |
| E4 | CSP front figée sur `:8081` | `connect-src` ne liste pas les ports isolés | Documenté ; proxy de test OK ; optionnel d’étendre CSP pour labs |

---

## 6. Autorisation requise

**Aucun commit, push, restore d’offre, ni DROP de table n’a été fait.**  
En attente de votre autorisation explicite avant toute publication.
