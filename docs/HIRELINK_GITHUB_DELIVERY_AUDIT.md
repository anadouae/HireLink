# HireLink — Audit final avant livraison GitHub

**Date :** 2026-11-14  
**Périmètre :** audit + préparation uniquement  
**Contraintes respectées :** aucune écriture sur `hirelink`, aucun redémarrage `8081`/`5500`, aucun commit/push, aucun connecteur activé, secrets non modifiés. Tests d’installation = schéma isolé `hirelink_delivery_audit` uniquement.

**Cahier des charges de référence :** le dépôt contient `HireLink_Cahier_des_charges_v6_3_finale` (pas de fichier nommé « v6.0 »). La matrice §11 s’appuie sur v6.3 + `HIRELINK_PRODUCT_REALITY_AUDIT.md` + README.

---

## Verdict

# READY TO COMMIT

*(Mis à jour 2026-11-14 après P0 — voir `HIRELINK_P0_DELIVERY_FIX_REPORT.md`.)*

Les corrections P0-1/2/3 sont appliquées et validées sur install isolée. **Commit/push non effectués** — en attente d’autorisation.

| Critère | Statut |
|---|---|
| Code Phases 1–3 + `landing-offers.js` L127 | Working tree prêt |
| Secrets / `.gitignore` | `docs/deploy-prep/` + screenshots exclus |
| Migrations V000→V006 | Runner OK (fresh + partial) |
| Seed + catalogue Phase 3 | Bootstrap auto si catalogue vide |
| Connecteurs externes | OFF |
| Clone & run isolé | **PASS** (`hirelink_p0_clone` / `:18081`) |

---

## 1. Code à versionner

### À inclure (indispensable)

| Zone | Contenu |
|---|---|
| Backend Phase 1–3 | `Catalog*`, `ExternalJobBoard*`, `UrlQuality*`, `external/*` ATS, sync facade, parsers, tests isolation |
| Migrations | `database/migrations/V003` … `V006` |
| Config | `application.properties` (external-jobs OFF, startup-sync false) |
| Frontend | `landing-offers.js` (fix `}));`), `candidate-home.js` catalogue, pages CSP, checkout, etc. |
| Docs produit | guides existants + rapports Phase 0–3 (sans dumps SQL) |
| `.gitignore` | version working **en premier** |

### À exclure absolument

| Chemin | Raison |
|---|---|
| `docs/deploy-prep/**` (surtout `*.sql`) | Hashs BCrypt + e-mails réels (ex. Gmail / etu.uae) |
| `docs/audit-screenshots/` | Captures runtime / PII démo |
| `backend/application-local.properties` | SMTP + clés LLM **réelles** |
| `backend/data/**`, `jwt.key` | Secret JWT + uploads |
| `backend/application-*-runtime.properties` / audit-iso / deploy-prep | Config machine |
| `backup-db/`, `*.jar`, `target*/` | Artéfacts |

**État Git actuel :** ~94 entrées porcelain ; `main...origin/main` avec nombreuses modifications + untracked Phase 3. Remote : `https://github.com/anadouae/HireLink.git`.

---

## 2. Migrations Flyway-style

| Fait | Détail |
|---|---|
| Emplacement | `database/migrations/V000` … `V006` (pas sous `src/main/resources/db/migration`) |
| Spring Flyway | **Absent** — pas de dépendance Flyway |
| Demo locale | `ddl-auto=update` masque l’écart (Hibernate crée/aligne) |
| Prod | `ddl-auto=validate` → **exige** schéma complet à la main |

**Test isolé :** `hirelink_delivery_audit` — apply manuel V000→V006 = **SUCCESS** (34 tables, dont `catalog_listings`, `external_job_boards`).

**P0-2 :** `apply-migrations.ps1` applique V001–**V006** (V000 avec `-Fresh`).

---

## 3. Configuration par défaut

| Paramètre | Valeur livrable |
|---|---|
| DB | `jdbc:mysql://127.0.0.1:3306/hirelink`, user `root`, password **vide** |
| Port API | `8081` |
| JWT | `HIRELINK_JWT_SECRET` ou auto-fichier `backend/data/jwt.key` |
| Seed | `hirelink.seed.enabled=true` (désactivé en `prod`) |
| Externes | Jooble / ATS / RemoteOK / Maroc / startup-sync = **false** |
| Overlay secrets | `application-local.properties` (gitignored) ou env `HIRELINK_*` |

Profil `prod` : seed off, validate, secrets env obligatoires.

---

## 4. Scripts Windows

| Script | Rôle | Prérequis |
|---|---|---|
| `LANCER-HIRELINK.bat` | `mvn spring-boot:run` :8081 | Java **21+**, Maven, MySQL 3306 |
| `LANCER-HIRELINK-DEMO.bat` | Idem + SMTP coupé / OTP console off | Idem |
| `LANCER-FRONTEND.bat` | `python -m http.server 5500` | Python 3 |

Chemins portables (`%~dp0`). Fallbacks optionnels JDK/Maven sous `C:\Program Files\...` / `C:\apache-maven-3.10.0`.

---

## 5. Dépendances

| Composant | Version / note |
|---|---|
| JDK | **21** (`pom.xml` `<java.version>21</java.version>`) — 23 OK en pratique |
| Maven | 3.9+ |
| MariaDB/MySQL | XAMPP 10.4+ / 8.x, port 3306 |
| Python | 3.x (frontend statique) |
| Node | **non requis** pour démo professeur (Playwright = audit local seulement) |

---

## 6. Secrets & données personnelles

| Élément | Suivi Git ? | Risque |
|---|---|---|
| `backend/data/jwt.key` | Non | OK |
| `application-local.properties` | Non (ignore) | **BLOCKER** si forcé |
| Dumps `docs/deploy-prep/*.sql` | Ignorés working ; **pas sur HEAD ignore** | **BLOCKER** si add avant commit ignore |
| Google Client ID dans `application.properties` | Oui | MED (public mais projet-spécifique) |
| Mots de passe démo dans GUIDE/README | Oui | LOW (intentionnel scolaire) |

---

## 7. Cohérence `.gitignore`

Working tree couvre cutover/audit props + SQL deploy-prep + jwt.  
**HEAD** ne contient pas encore ces lignes deploy-prep → **commit `.gitignore` en premier**.

Recommandation (correction P1, autorisation) : ignorer entièrement `docs/deploy-prep/` et `docs/audit-screenshots/` (pas seulement `*.sql`).

---

## 8. Comptes de démonstration

Sur **base vide**, `DataSeeder` crée les comptes si `hirelink.seed.enabled=true` et `users.count()==0`.

| Compte | Mot de passe | Usage professeur |
|---|---|---|
| `admin@hirelink.ma` | `Admin123!` | Admin |
| `n.bennani@atlasdigital.ma` | `Recruteur123!` | Owner (billing) |
| `recruteur@hirelink.ma` | `Recruteur123!` | Recruteur **non-OWNER** |
| `candidat@hirelink.ma` | `Candidat123!` | Candidat |

Méthode sûre install neuve : MySQL vide → lancer backend → seed automatique → **ne pas** importer de dump SQL personnel.

---

## 9. Catalogue sur base propre

Avec flags actuels :

- **Aucun** import fixture MAROC / RemoteOK / ATS au boot (`startup-sync=false`, sources OFF).
- Seed crée des **offres internes** HireLink (démo), pas des « vraies » annonces externes.
- **P0-3 :** en fin de seed, si `catalog_listings` est vide et qu’il existe des offres, `CatalogListingIndexer.rebuild()` est appelé une fois. Deuxième boot : skip si déjà peuplé. MAROC_EMPLOI reste exclu du rebuild.

---

## 10. Cause exacte du `403` `/billing/quote?cycle=month`

**Pas un bug SecurityConfig / CSRF.**

1. Endpoint autorisé pour `CANDIDAT|RECRUTEUR|ADMINISTRATEUR`.
2. `PaymentService.targetPaidTier` : un `RECRUTEUR` doit être **`OrgRole.OWNER`**, sinon :

   > `403` — « Seul le créateur de l'espace règle la formule. »

3. `recruteur@hirelink.ma` = rôle org **RECRUTEUR** (Atlas Digital). Owner = `n.bennani@atlasdigital.ma`.
4. Checkout FE gère le soft-fallback vers `/billing/plans`.

**Action livraison :** documenter dans le plan professeur (utiliser Nadia pour le devis payant). **Aucune correction code requise** pour ce 403.

---

## 11. Cahier des charges (v6.3) — synthèse

Légende : **I** = implémenté · **T** = testé (auto et/ou smoke démo) · **R** = restant / partiel.

| Domaine | I | T | R |
|---|---|---|---|
| Auth (password, OTP, refresh) | oui | oui | Google dépend config ; MFA admin optionnelle |
| Profil / CV / compétences | oui | oui | OCR image/scan limité |
| Offres internes + pipeline ATS + SLA + refus catalogue | oui | oui | — |
| Matching local déterministe | oui | oui | — |
| Entretiens + notifications SSE | oui | smoke | — |
| Admin ICE / plans / modération | oui | partiel | — |
| Billing simulé (quote/invoice/PDF) | oui | oui | Pas de PSP réel (attendu) |
| IA crédits + providers | oui | partiel | Clés absentes → repli local |
| Catalogue unifié Phase 3 | oui | oui (démo) | Reindex post-seed manquant |
| Agrégation externe autorisée | code prêt | isolé | **OFF** ; Jooble MA non activé ; feed Maroc = fixture si ON |
| Scorecards multi-évaluateurs | UI retired / 409 writes | — | Assumé retiré |

Détail : `docs/HIRELINK_PRODUCT_REALITY_AUDIT.md`.

---

## 12. Tests automatisés & install isolée

| Élément | Note |
|---|---|
| ~37 `*Test.java` | Isolation catalogue / ATS / URL quality |
| `Phase2LiveAtsImportSmokeTest` | Opt-in `-Dhirelink.phase2.live=true` seulement |
| `mvn test` | Nécessite MariaDB `hirelink_test` (pas 100 % offline) |
| Install isolée | `hirelink_delivery_audit` V000–V006 OK (manuel) |

---

## Corrections indispensables (priorisées)

### P0 — **FAIT** (2026-11-14)

1. `.gitignore` élargi (`docs/deploy-prep/`, `docs/audit-screenshots/`, …).  
2. `apply-migrations.ps1` → V001–V006 (+ V000 avec `-Fresh`).  
3. Bootstrap catalogue post-seed idempotent (`DataSeeder.ensureCatalogBootstrap`).

### P1 — recommandé avant/après push

4. Neutraliser ou documenter le Google Client ID par défaut.  
5. Commit selon `HIRELINK_GIT_SAFE_INCLUDE_LIST.md` (pas `git add .`).

### P2 — non bloquant

6. Message UI explicite si quote 403 non-OWNER.  
7. Brancher ou retirer la propriété morte `catalog.rebuild-on-startup`.

---

## Livrables associés

| Document | Fichier |
|---|---|
| Rapport P0 | `docs/HIRELINK_P0_DELIVERY_FIX_REPORT.md` |
| Liste inclusion Git | `docs/HIRELINK_GIT_SAFE_INCLUDE_LIST.md` |
| Guide installation Windows | `docs/HIRELINK_INSTALL_WINDOWS.md` |
| Checklist Git pré-commit | `docs/HIRELINK_GIT_PRECOMMIT_CHECKLIST.md` |
| Plan de test professeur | `docs/HIRELINK_PROFESSOR_TEST_PLAN.md` |

---

## Prochaine étape recommandée

**Autoriser le commit** (puis push) selon la checklist — rien n’a été poussé.
