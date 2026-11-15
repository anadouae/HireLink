# HireLink — Validation finale du clone GitHub

**Date :** 2026-11-14
**Dépôt testé :** https://github.com/anadouae/HireLink.git
**Commit attendu / obtenu :** `62339277081301bca5c72778478370d7ceb9e616`
**Dossier isolé :** `C:\Users\daurinia\Desktop\HireLink_GitHub_Clone_Test`
**Guide suivi :** `docs/HIRELINK_INSTALL_WINDOWS.md`
**Contraintes :** pas de modification du dépôt publié, pas de commit/push, pas de touche à la base `hirelink`, démo 8081/5500 non redémarrée, pas d’import de sauvegarde personnelle, connecteurs externes non activés.

---

## Verdict

# PASS WITH CONDITIONS

Le clone GitHub au commit `6233927` est **autonome, installable et fonctionnel** : migrations V000–V006, seed, catalogue, authentification multi-rôles, recherche / filtres / « Pour toi », pipeline, facturation simulée et connecteurs désactivés — **sans clé API personnelle ni fichier local de l’auteur**.

La condition porte uniquement sur l’**isolement forcé** (ports 18082 / 5505) : plusieurs pages `frontend/app/*.html` limitent le CSP `connect-src` à `8081`. Sur les ports documentés du guide (**8081 / 5500**), ce frein n’existe pas. Les parcours bloqués par CSP en ports alternatifs ont été prouvés fonctionnels via API et, en session navigateur uniquement, avec contournement CSP (aucune modification des fichiers du clone).

---

## 1. Clone isolé

| Contrôle | Résultat |
|---|---|
| `git clone https://github.com/anadouae/HireLink.git` → dossier neuf | OK |
| `git rev-parse HEAD` | `62339277081301bca5c72778478370d7ceb9e616` |
| Fichiers non versionnés de l’auteur réutilisés | Non |
| `backend/application-local.properties` présent dans le clone | **Absent** (attendu) |
| JWT | Auto-généré dans `backend/data/jwt.key` au démarrage |

Fichiers **uniquement locaux au test** (hors Git publié) :
`backend/application-clone-test.properties` (ports/DB isolés) et `tmp-clone-test/` (logs + captures).

---

## 2. Installation (guide Windows, base distincte)

### Base de données

| Élément | Valeur |
|---|---|
| Base de test | `hirelink_github_clone_test` |
| Base principale `hirelink` | **Non modifiée** (toujours **123** offres, démo UP) |
| Script | `database\apply-migrations.ps1 -Database hirelink_github_clone_test -Fresh` |

Migrations appliquées :

1. V000 baseline
2. V001__legal_acceptances.sql
3. V002__external_offers.sql
4. V003__billing_invoice_org_nullable.sql
5. V004__external_offer_url_quality.sql
6. V005__external_job_boards_ats.sql
7. V006__catalog_listings.sql

→ **OK** (`catalog_listings`, `external_job_boards`, colonne qualité URL présents).

### Services isolés (sans toucher 8081 / 5500)

| Service | Port | Statut |
|---|---|---|
| Backend clone | **18082** | UP (`/api/v1/health`) |
| Frontend clone | **5505** | 200 |
| Démo existante | 8081 / 5500 | **UP / 200** pendant tout le test |

Config de test (non publiée) : DB `hirelink_github_clone_test`, CORS 5505, tous les `hirelink.external-jobs.*.enabled=false`, `startup-sync=false`.

### Seed & catalogue

Logs backend clone :

- `Seed OK — 34 users, 53 offres, 11 candidatures, 6 motifs`
- `Catalogue déjà peuplé (52 lignes)` (bootstrap automatique déjà effectué sur cette base fraîche au 1er boot post-migrations)
- `Allowlist ATS chargee : 5 board(s), 0 actif(s)`

DB clone : `users=34`, `job_offers=53`, `catalog_listings=52`, origines **INTERNAL**, boards enabled = **0**.

---

## 3. Tests API (clone 18082)

| Parcours | Résultat |
|---|---|
| Offres publiques | **52** (`/offers/public`) |
| Catalogue anonyme | **52** (`/catalog/offers`) |
| Login candidat | OK — Sara El Amrani / `CANDIDAT` |
| « Pour toi » (`forYou=true`) | **6** / 52 scannées |
| Recherche `q=java` | **5** |
| Filtre `country=MA` | **52** |
| Login recruteur | OK — Salma Berrada / `RECRUTEUR` |
| Pipeline | **200** `/applications/pipeline` |
| Quote recruteur non-owner | **403** (attendu) |
| Login OWNER | OK — Nadia Bennani |
| Quote OWNER | OK — `amountTtc=299`, clés plan/cycle/TVA présentes |
| Plans billing | **200** |
| Login admin | OK — Youssef Cherkaoui / `ADMINISTRATEUR` |
| Clé API personnelle requise | **false** |
| Démo `hirelink` | UP, **123** offres |

---

## 4. Tests UI (frontend clone 5505 → API 18082)

### Fonctionnels sans contournement (pages CSP déjà ouvertes à 18082)

| Écran | Résultat | Capture |
|---|---|---|
| Landing / grille catalogue | **9** cartes « Offre HireLink » visibles | `docs/evidence/github-clone-final/shot-landing-grid.png` |
| Login candidat | OK | — |
| Espace candidat | **20** cartes | `shot-candidate.png` |
| « Pour toi » (toggle profil) | **6** offres + scores | `shot-pour-toi.png` |

### Fonctionnels après contournement CSP session-only (pages CSP = 8081 seul)

Cause : meta CSP `connect-src` de nombreuses pages `app/*.html` et `checkout.html` n’autorise que `8081`, pas `18082`.
Preuve sans modifier le clone : `Page.setBypassCSP` Playwright.

| Écran | Résultat | Capture |
|---|---|---|
| Pipeline recruteur | Kanban peuplé (dossiers seed) | `shot-pipeline-nocsp.png` |
| Admin vue d’ensemble | Stats 34 comptes / 52 offres / 11 candidatures | `shot-admin-nocsp.png` |
| Admin Sources externes | Toutes **Désactivée**, 0 import, sync jamais | `shot-admin-sources-nocsp.png` |
| Checkout OWNER | Formules Accès / Premium visibles | `shot-checkout-nocsp.png` |
| Paramètres → Formules | Bouton Premium visible (créateur) | `shot-owner-formules-nocsp.png` |

Sur une installation professeur **stricte** (`8081` + `5500` selon le guide), ces pages fonctionnent **sans** contournement CSP.

---

## 5. Autonomie & secrets

| Contrôle | Résultat |
|---|---|
| Besoin d’un `.env` / `application-local.properties` de l’auteur | **Non** |
| Clés Gemini / Groq / Mistral / Google OAuth | **Non requises** pour la démo |
| JWT | Généré localement au boot |
| Import dump SQL personnel | **Non** |
| Fichiers de l’espace `Desktop\HireLink` utilisés pour faire tourner le clone | **Non** (Playwright du poste utilisé seulement pour captures automatisées ; non requis par le guide d’install) |

---

## 6. Connecteurs externes

| Contrôle | Résultat |
|---|---|
| Defaults publiés (`application.properties`) | `maroc/remoteok/greenhouse/lever/ashby.enabled=false`, `startup-sync=false` |
| Config test clone | Tous false |
| Log boot | `0 actif(s)` |
| UI Admin › Sources externes | Emploi Maroc, Remote OK, Greenhouse, Lever, Ashby, Jooble → **Désactivée** ; offres importées **0** |
| Catalogue | Labels **Offre HireLink** uniquement |

---

## 7. Non-régression démo locale

| Contrôle | Pendant / après test |
|---|---|
| `http://127.0.0.1:8081/api/v1/health` | UP |
| `http://127.0.0.1:5500` | 200 |
| `hirelink.job_offers` | **123** (inchangé) |
| Services clone 18082 / 5505 | Arrêtés en fin de campagne |

---

## 8. Problèmes rencontrés (documentés avant toute correction produit)

### P1 — CSP incomplet pour ports API non standards (CONDITION du verdict)

- **Symptôme :** pages recruteur / admin / checkout affichent « Réseau indisponible » si l’API est sur **18082** alors que le CSP n’autorise que **8081**.
- **Impact livraison professeur :** **nul** si le guide est suivi (8081/5500). `index.html`, `auth.html`, `candidate.html` autorisent déjà 18081/18082.
- **Correction produit :** hors périmètre de ce test (interdit de modifier le dépôt publié ici). Recommandation ultérieure : homogénéiser `connect-src` / `img-src` sur toutes les pages HTML comme sur `candidate.html`.

### P2 — Non bloquant

- Dialecte Hibernate WARN MySQL 5.5.5 (XAMPP) : démarrage OK.
- Playwright absent du dépôt publié : normal ; le professeur valide au navigateur.

Aucun problème bloquant d’installation, de seed, de catalogue ou d’API n’a été trouvé. Aucune correction n’a été appliquée au code publié.

---

## 9. Matrice objectifs

| # | Objectif | Statut |
|---|---|---|
| 1 | Clone isolé sans fichiers locaux non versionnés | PASS |
| 2 | Commit `6233927…` | PASS |
| 3 | Suivre `HIRELINK_INSTALL_WINDOWS.md` | PASS |
| 4 | DB de test ≠ `hirelink` | PASS |
| 5 | Ports alternatifs sans perturber 8081/5500 | PASS |
| 6 | Migrations V000–V006 + seed + catalogue auto | PASS |
| 7 | Connexions candidat / recruteur / OWNER / admin | PASS |
| 8 | Recherche, filtres, Pour toi, pipeline, facturation simulée | PASS (UI ports alt. : voir condition CSP) |
| 9 | Aucune clé API / fichier perso requis | PASS |
| 10 | Connecteurs externes désactivés | PASS |

---

## 10. Preuves

- Logs / smoke : `HireLink_GitHub_Clone_Test\tmp-clone-test\` (`mig.log`, `be-18082.log`, `api-smoke.txt`, `browser*.log`)
- Captures : `docs/evidence/github-clone-final/`

---

## Verdict final

**PASS WITH CONDITIONS**

Le dépôt GitHub publié au commit `62339277081301bca5c72778478370d7ceb9e616` se clone, s’installe et se démontre de façon autonome pour un professeur suivant `docs/HIRELINK_INSTALL_WINDOWS.md`. La seule condition est l’écart CSP sur ports API non documentés ; la livraison standard 8081/5500 n’y est pas exposée.
