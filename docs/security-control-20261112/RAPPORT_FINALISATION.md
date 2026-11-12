# HireLink — Finalisation avant publication (2026-11-12)

Aucun commit / push. Démo `8081` + base `hirelink` préservées (`ddl-auto=update` inchangé).

---

## 1. Offre #158 — analyse lecture seule (non restaurée)

### Références associées
| Source | Résultat |
|---|---|
| `applications` (`job_offer_id=158`) | **0** |
| `offer_reports` | **0** |
| `job_offer_tags` | **0** |
| `scorecard_entries` (via candidatures) | **0** |
| `notifications` | **1** : id 1519, kind `FOLLOW`, user 10 (`candidat@…`), link `offer:158`, créée à la publication (2026-11-10 09:42:28) — **orpheline** (lien cassé) |
| `audit_logs` (OFFER) | 3 événements (publish / unpublish / delete) |

### Entretiens
Aucun (pas de candidature donc pas d’`interview_at`).

### Cause probable de la suppression
| Indice | Observation |
|---|---|
| Acteur | `recruteur@hirelink.ma` (user 11), rôle `RECRUTEUR` |
| IP | `127.0.0.1` |
| Timing | Unpublish 10:20:22 ? Delete 10:20:30 (8 s) |
| Volume session | Seulement ces 2 actions OFFER pour cet acteur dans la fenêtre 10:15–10:35 |
| Maven / tests auto | Les suites Maven n’écrivent pas `OFFER_DELETED` sur `hirelink` via UI ; pas de trace d’outil batch |

**Conclusion :** action **interactive locale** (navigateur / session d’audit UI sur la démo), **pas** un test Maven automatisé. Aucune donnée restaurée.

---

## 2. Migrations SQL + `ddl-auto=validate`

### Stratégie livrée
| Élément | Chemin |
|---|---|
| Baseline schéma MariaDB | `database/migrations/V000__schema_baseline_mariadb.sql` |
| Incrémentales idempotentes | `V001__legal_acceptances.sql`, `V002__external_offers.sql` |
| Runner | `database/apply-migrations.ps1` |
| Doc | `database/migrations/README.md`, `docs/MIGRATIONS.md` |

### Preuve isolée
```
apply-migrations.ps1 -Database hirelink_validate_lab -Fresh  ? 31 tables
Backend :8094 + spring.jpa.hibernate.ddl-auto=validate ? HEALTH UP
```
Démo : `application.properties` reste `update` ; prod profile déjà `validate`.

MariaDB locale : `10.4.32-MariaDB`. Scripts de lancement (`LANCER-HIRELINK*.bat`) non altérés.

---

## 3. Checkout simulé — cause et correction

### Cause des tirets
1. Les montants n’étaient peints qu’**après** `POST /billing/subscribe` (soumission).
2. Sans `?plan=recruiter`, le libellé tombait sur « Candidat Performance » même pour un recruteur.

### Correction
- Nouveau `GET /api/v1/billing/quote?cycle=` : devis catalogue serveur (HT / TVA / TTC), **sans** inventer de tarifs.
- Front charge le devis au chargement et au changement de période.
- Moyens de paiement visuels (carte de test / virement simulé) **sans** saisie bancaire.
- Badge « Simulation » conservé, discret.

### Récapitulatif catalogue (serveur, TVA non configurée ? `vatConfigured=false`)

| Abonnement | Période | HT* | TVA | Total TTC |
|---|---|---:|---|---:|
| Premium (recruteur) | Mensuel | 299,00 MAD | non configurée (—) | **299,00 MAD** |
| Premium (recruteur) | Annuel | 2 990,00 MAD | non configurée (—) | **2 990,00 MAD** |
| Performance (candidat) | Mensuel | 39,00 MAD | non configurée (—) | **39,00 MAD** |
| Performance (candidat) | Annuel | 390,00 MAD | non configurée (—) | **390,00 MAD** |

\*Avec `hirelink.billing.vat-rate` vide, le serveur stocke HT = montant catalogue TTC et n’invente pas de taux. Si un taux est configuré plus tard, HT/TVA sont ventilés automatiquement (couvert par `BillingQuoteTest`).

Captures : `shots/19`–`22-checkout-*.png`.

---

## 4. Validation finale

| Contrôle | Résultat |
|---|---|
| Checkout API + UI (4 formules/périodes) | **10 PASS / 0 FAIL** (`checkout-verify.md`) |
| Smoke UX pipeline + assistant | PASS |
| Maven | **Tests run: 149, Failures: 0** (BUILD SUCCESS) — +2 `BillingQuoteTest` |
| Parcours UX précédents (29) | Non régressés (smoke + checkout OK) |
| Base `hirelink` | 126 users / 118 offres / 0 legal rows |
| Démo `8081` | UP |

---

## Fichiers modifiés / ajoutés

### Backend
- `BillingCatalogService.java` — `quote()`
- `PaymentService.java` — `quote()`
- `BillingController.java` — `GET /billing/quote`
- `BillingQuoteTest.java` (nouveau)

### Frontend
- `checkout.html`, `js/checkout.js`, `css/checkout.css`

### Migrations / docs
- `database/migrations/*`, `database/apply-migrations.ps1`
- `docs/MIGRATIONS.md`
- `docs/security-control-20261112/RAPPORT_FINALISATION.md` (+ shots / matrix checkout)

### Non modifié (volontairement)
- `application.properties` (`ddl-auto=update` démo)
- Données `hirelink` / offre 158
- Secrets, commit, push
