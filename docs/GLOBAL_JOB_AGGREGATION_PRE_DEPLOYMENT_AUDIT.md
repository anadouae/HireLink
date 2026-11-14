# HireLink — Audit pre-deploiement Phases 1–3

**Date :** 2026-11-13 (recette finale completee)  
**Perimetre :** corrections + tests en schemas isoles `hirelink_ext_phase03` / `hirelink_ext_perf` / `hirelink_mig_audit_p13` ; ports `18081` + `5501`  
**Interdit respecte :** aucune migration/ecriture sur `hirelink`, pas de redemarrage demo `8081`/`5500`, pas de commit/push, Jooble/ATS non actives sur la demo.

Legende des statuts : **IMPLEMENTED** · **TESTED** · **VERIFIED IN BROWSER** · **NOT TESTED** · **BLOCKED**

---

## 1. Resume executif

L'audit final a confronte les rapports Phases 0–3 au code, mesure la perf multi-volumes, applique les migrations sur une copie structurelle, elargi la non-regression et complete la recette navigateur sur instance isolee.

Defauts majeurs deja corriges en isolation (et reconfirmes) :

1. **Catalogue stale** → sync incrementale `syncInternal` / `syncExternal` / `removeByOriginRef` branchee sur les mutations.
2. **Matching trompeur « Pour toi »** → internes = `evaluateIfAvailable` multicritere ; externes = score competences **indicatif** ; truncation douce (plus de HTTP 400).
3. **Filtre ville accents** → `city_fold`.

Preuves nouvelles de cette passe :

| Domaine | Preuve |
|---|---|
| Perf 100 → 25 000 | `CatalogPerfMultiVolumeIsolationTest` sur `hirelink_ext_perf` |
| Migrations V004–V006 | appliquees sur `hirelink_mig_audit_p13` (structure clonee) ; `hirelink` inchange |
| Catalogue apres publication | Playwright + API : offre recruteur visible dans `/catalog/offers` sans rebuild manuel |
| Non-regression | Suites catalogue/ATS/matching/URL/interviews/billing reports Surefire OK |
| Demo | 118 internes / 106 externes ; pas de `catalog_%` ; `8081`/`5500` toujours UP |

**Verdict : READY WITH CONDITIONS** — voir section 19. Aucune integration demo sans votre autorisation.

---

## 2. Etat initial verifie

| Controle | Resultat | Statut |
|---|---|---|
| Health / API `8081` | repond (public offers 200) | TESTED |
| Frontend `5500` | 200 | TESTED |
| `hirelink.job_offers` | **118** | TESTED |
| `hirelink.external_job_offers` | **106** | TESTED |
| Tables `catalog_%` sur `hirelink` | **absentes** | TESTED |
| Isolation | `hirelink_ext_phase03`, `hirelink_ext_perf`, `hirelink_mig_audit_p13` | TESTED |
| Config defaut | `application.properties` pointe encore `hirelink` — override obligatoire avant tout lancement test | TESTED |
| Git commit/push | aucun effectue | TESTED |

---

## 3. Problemes detectes

| # | Severite | Probleme | Preuve | Etat |
|---|---|---|---|---|
| P1 | Critique | Aucune sync catalogue apres create/publish/update/close/import/hide | code avant correction | **Corrige** |
| P2 | Haute | Pour toi / DTO internes skills-only vs `evaluate` | `CatalogOfferService` | **Corrige** |
| P3 | Haute | HTTP 400 si > `for-you-max-scan` | throw BAD_REQUEST | **Corrige** (truncation + flags) |
| P4 | Moyenne | Filtre ville sensible aux accents | echecs Tetouan | **Corrige** (`city_fold`) |
| P5 | Moyenne | FE CSP `connect-src` limite a 8081 | Playwright `bypassCSP` requis sur 18081 | Documente |
| P6 | Basse | Rapports Phase 3 « BUILD SUCCESS » sans sync evenementielle | confrontation docs/code | Documente |
| P7 | Info | Rebuild startup/reconcile periodique non actives (protege demo) | properties | Intentionnel |
| P8 | Basse | Formulaire login headless : champ email souvent non visible (onglets UI) | Playwright | Contourne via login API + injection session |
| P9 | Info | Apres seed, catalogue a 0 jusqu'a reindex admin | observe 18081 | Documente (runbook) |
| P10 | Info | `LIMIT/OFFSET` : pages profondes degradent | mesures + litterature | Documente |

---

## 4. Corrections realisees

| Correction | Justification |
|---|---|
| `CatalogListingIndexer.syncInternal/syncExternal/removeByOriginRef` | Actualisation transactionnelle idempotente |
| Branchement `JobOfferService` (cycle de vie interne) | Catalogue reflechir create/publish/update/close/… |
| Branchement `ExternalJobImportService` + hide admin | Import / quarantaine / masquage |
| `CatalogOfferService` : `evaluateIfAvailable` + label indicatif externe + truncation douce | Alignement matching metier + scalabilite |
| `city_fold` + fold query | Filtres villes accent-insensibles |
| Tests sync/perf/multi-volume | Preuves isolees |
| FE badge via `matchLabel` | Pas de presentation multicritere sur score indicatif |
| `application-audit-iso.properties` + script Playwright | Recette navigateur sans toucher la demo |

---

## 5. Fichiers modifies

**Produit (Phases 1–3 + audit) :**

- `service/CatalogListingIndexer.java`, `CatalogOfferService.java`, `JobOfferService.java`, `ExternalJobImportService.java`, `PlaceCatalogService.java`
- `controller/CatalogOfferController.java`, `ExternalJobAdminController.java`
- `model/CatalogListing.java`, `repository/CatalogListingRepository.java`
- `database/migrations/V004__…`, `V005__…`, `V006__catalog_listings.sql` (`city_fold`)
- FE : `candidate-home.js`, landing/candidate pages (pagination catalogue + badge)

**Recette / preuves (cette passe) :**

- `backend/src/test/.../CatalogPerfMultiVolumeIsolationTest.java` (schema `hirelink_ext_perf`)
- `backend/application-audit-iso.properties` (profil isole local, non destine a la demo)
- `docs/audit-browser-recette.mjs`, `docs/audit-screenshots/*`, logs `docs/audit-perf-multi.log`, `docs/audit-nonreg-catalog.log`

Aucun commit. Secrets demo inchanges.

---

## 6. Architecture finale

```
job_offers / external_job_offers
        │  sync incrementale (mutations listables)
        ▼
 catalog_listings  (+ city_fold, indexes sort/country/source)
        │
 GET /api/v1/catalog/offers  → filtres SQL → COUNT + LIMIT/OFFSET
        │
 forYou=true → scan N recentes (cap) → score metier → catalog_for_you_run → page SQL
```

Legacy conserve : `/offers/public`, `/external-offers`.  
Jooble / Greenhouse / Lever / Ashby : **flags OFF** dans `application.properties`.

---

## 7. Strategie d'actualisation du catalogue

| Declencheur | Action | Statut |
|---|---|---|
| Publish / update / unpublish / close / reopen / archive / suspend / restore | `syncInternal` | IMPLEMENTED + TESTED |
| Delete offre sans candidatures | `removeByOriginRef` | IMPLEMENTED + TESTED (chemin code) |
| Import snapshot externe | `syncExternal` | IMPLEMENTED + TESTED |
| Hide admin | `syncExternal` | IMPLEMENTED + TESTED (isolation) |
| Rebuild admin | `POST /catalog/reindex` | IMPLEMENTED + TESTED |
| Creation offre recruteur → catalogue | sync sans rebuild | TESTED + VERIFIED IN BROWSER (API+UI) |
| Reconcile periodique | non active | NOT TESTED / autorisation requise |
| Startup rebuild | OFF | IMPLEMENTED (protege demo) |

Idempotence : cle unique `(origin, ref_id)`. Pas de reconstruction integrale a chaque mutation.

---

## 8. Validation du matching

| Aspect | Attendu HireLink | Catalogue | Statut |
|---|---|---|---|
| Interne | `evaluateIfAvailable` multicritere | Oui | IMPLEMENTED + TESTED |
| Externe | competences source, label indicatif | Oui | IMPLEMENTED + TESTED |
| Sans competences | Pas de score / Pour toi vide | Oui | TESTED |
| Seuil 50 % | Configurable | Oui | TESTED |
| Scores candidatures historiques | Inchanges | Non touches | TESTED (unit + interview suites) |
| Cap scan | Truncation douce + `forYouTruncated` | Oui | TESTED |
| FE presentation | `matchLabel` | Oui | IMPLEMENTED |

Difference documentee : matching externe catalogue = **indicatif competences**, distinct du multicritere interne.

Strategie evolutive Pour toi (sans inventer de score) :

1. Garder le matching metier existant.
2. Scanner les N plus recentes (cap configurable) avec truncation explicite.
3. Evolution future possible (autorisation) : indexation partielle, file asynchrone, keyset — **pas** un score factice.

---

## 9. Resultats des tests fonctionnels

| Suite | Resultat | Statut |
|---|---|---|
| `CatalogPhase3IsolationTest` (5) | OK | TESTED |
| `CatalogSyncAndPerfIsolationTest` (3) | OK | TESTED |
| `AtsConnectorsPhase2IsolationTest` (5) | OK | TESTED |
| Sync publish→update→close→import/quarantine | OK | TESTED |
| Pour toi sans 400 + multicritere | OK | TESTED |
| Filtres city fold / pays / contrat / source | OK | TESTED |
| Exclusion publique `MAROC_EMPLOI` | OK (Phase 3) | TESTED |
| Jooble sans cle = OFF + fixtures | OK | TESTED |

---

## 10. Resultats des tests de performance

**Conditions :** Windows 10, MariaDB XAMPP local, Surefire JVM, schema **`hirelink_ext_perf`** (distinct de la demo), `for-you-max-scan=2000`, connectors OFF, seed OFF.  
**Source :** `CatalogPerfMultiVolumeIsolationTest` — lignes `PERF_ROW` dans `docs/audit-perf-multi.log`.

Format : `n,rebuildMs,pageMs,countMs,filterMs,forYouMs,heapMb,concurrentP95Ms`

| Volume | rebuild | page (20) | count | filtre city+CDI | Pour toi | heap Mo | concurrent p95 (8 thr) | Statut |
|---|---:|---:|---:|---:|---:|---:|---:|---|
| 100 | 1039 | 1089* | 48 | 49 | 1698 | 78 | 97 | TESTED |
| 1 000 | 3967 | 66 | 32 | 36 | 6329 | 73 | 135 | TESTED |
| 5 000 | 11457 | 62 | 41 | 62 | 9355 | 74 | 330 | TESTED |
| 10 000 | 21092 | 97 | 54 | 81 | 8770 | 138 | 569 | TESTED |
| 25 000 | 52488 | 224 | — | — | — | — | — | TESTED (page+rebuild) |

\* Premiere page a 100 : cout a froid / JIT ; les volumes suivants montrent ~60–100 ms.

**Lecture :** pagination SQL des premieres pages reste acceptable jusqu'a 25k ; Pour toi reste borne par le scan (secondes). Limite `LIMIT/OFFSET` : pages profondes non mesurees ici — evolution recommandee = keyset. Rebuild complet 25k ~52 s : a reserver a l'admin / migration, pas au chemin hot.

---

## 11. Resultats de la recette navigateur

Instance isolee : backend `18081` (`application-audit-iso.properties` → `hirelink_ext_phase03`) + FE `5501` + `?api=` + Playwright `bypassCSP`.

Script : `docs/audit-browser-recette.mjs` — resultat `docs/audit-screenshots/browser-recette.json`.

| Parcours | Statut | Preuve |
|---|---|---|
| Landing | VERIFIED IN BROWSER | `01-landing.png` |
| Recherche landing | VERIFIED IN BROWSER | `02-search-java.png` |
| Catalogue candidat (session injectee) | VERIFIED IN BROWSER | `09-candidate-authed.png` |
| Pour toi toggle | VERIFIED IN BROWSER | `10-pour-toi-authed.png` |
| Login formulaire headless | NOT TESTED | email non visible (onglets) — login API OK |
| Recruteur page offres | VERIFIED IN BROWSER | `12-recruiter-authed.png` |
| Publication → catalogue | TESTED (+ browser session) | offre id 55 visible `total=2` recherche |
| Resilience API morte | VERIFIED IN BROWSER | `08-api-down.png` |
| Selecteur pays UI | PARTIAL | pas de select country trouve sur la page candidate |
| Pipeline ATS detail UI | NOT TESTED navigateur | — |

Services isoles arretes apres recette ; demo `8081`/`5500` conserves.

---

## 12. Resultats de non-regression

| Domaine | Resultat | Statut |
|---|---|---|
| Catalogue Phase 3 + sync | 8 tests OK | TESTED |
| ATS Phase 2 isolation | 5 OK | TESTED |
| Matching / URL quality / parsers GH-Lever-Ashby | OK | TESTED |
| Interviews / ApplicationServiceInterview | OK (Surefire) | TESTED |
| BillingPayment | OK | TESTED |
| OfferIsolation / Notifications | OK | TESTED |
| ExternalJob / UrlQuality integration | OK | TESTED |
| Auth E2E complete UI | PARTIEL (API login OK) | PARTIAL |
| Demo 8081/5500 apres audit | intacte | TESTED |

Note : une execution parallele anterieure de deux suites `create-drop` sur le **meme** schema a provoque des erreurs transitoires ; rejoue **sequentiellement** / schemas separes → OK.

---

## 13. Audit des migrations

| Migration | Role | Test isole | Statut |
|---|---|---|---|
| V004 | `url_quality` / quarantaine | Appliquee sur `hirelink_mig_audit_p13` | TESTED (copie) |
| V005 | boards ATS + `board_key` | Idem | TESTED (copie) |
| V006 | `catalog_listings` + `catalog_for_you_run` + `city_fold` | Colonnes/tables presentes apres apply | TESTED (copie) |

Methode : `mysqldump --no-data hirelink` → import `hirelink_mig_audit_p13` → apply SQL V004→V005→V006.  
**`hirelink` :** toujours 118 offres, **aucune** table `catalog_%`.

Compat JPA : entites alignees ; **ne pas** demarrer le nouveau JAR sur `hirelink` sans migrations.  
Reversibilite : DROP tables catalogue ; DROP colonnes V004/V005 (manuel).  
Runbook futur : backup → V004→V005→V006 sur copie → reindex → smoke → bascule (autorisation requise).

---

## 14. Integrite demo / base principale (fin d'audit)

| Controle | Resultat |
|---|---|
| 8081 | LISTEN / API public 200 |
| 5500 | 200 |
| Offres internes | **118** |
| Offres externes | **106** |
| `catalog_%` sur hirelink | **absentes** |
| Ports isoles 18081/5501 | arretes (FREE) |
| Commit / push | aucun |
| Jooble / ATS demo | OFF |
| Ecriture hirelink | aucune (lecture seule verifiee) |

---

## 15. Risques residuels

1. Integration demo sans migrations → echec demarrage JPA.  
2. FE CSP limite a 8081.  
3. Apres seed/import massif, reindex ponctuel encore utile.  
4. Pour toi tronque aux N plus recentes.  
5. Sync catalogue logue `warn` si projection HS (transaction offre preservee → derive possible).  
6. Jooble/ATS **BLOCKED** juridiquement / sans cle.  
7. Login UI headless fragile (parcours reel navigateur manuel conseille avant go-live).  
8. Perf pages OFFSET profondes non mesurees.

---

## 16. Procedure de rollback

1. Ne pas deployer le JAR Phase 3 sur 8081.  
2. FE : repli legacy si `/catalog/offers` absent.  
3. Schemas isoles : `DROP DATABASE hirelink_ext_phase03;` / `hirelink_ext_perf;` / `hirelink_mig_audit_p13;` si nettoyage.  
4. Si un jour V006 sur hirelink : `DROP TABLE catalog_for_you_run, catalog_listings;`.  
5. Restaurer backup pre-migration si besoin.

---

## 17. Procedure recommandee pour un deploiement futur

1. Backup complet `hirelink`.  
2. Appliquer V004→V005→V006 sur **copie** puis valider (deja fait sur structure).  
3. Autorisation explicite pour redemarrer 8081 avec nouveau JAR.  
4. `POST /api/v1/catalog/reindex` (admin).  
5. Smoke : `/catalog/offers?country=MA`, Pour toi, candidature interne, lien externe, publication recruteur.  
6. Garder Jooble/ATS OFF jusqu'a cle + ToS.  
7. Surveiller latence COUNT/page 24 h.  
8. Elargir CSP FE si host API distinct.

---

## 18. Decisions necessitant votre autorisation

1. Integrer le code Phase 1–3 + corrections audit dans la **demo principale** (redemarrage 8081).  
2. Appliquer migrations V004–V006 sur `hirelink`.  
3. Activer un job de reconciliation periodique catalogue.  
4. Activer Jooble (cle officielle) ou canary ATS.  
5. Elargir CSP frontend.  
6. Commit / push Git.  
7. Nettoyage des schemas d'audit (`hirelink_ext_*`, `hirelink_mig_audit_p13`).

---

## 19. Verdict final

# READY WITH CONDITIONS

**Justification :** les criteres critiques techniques (sync catalogue, matching non trompeur, pagination SQL, exclusion sample/quarantaine, migrations sur copie, perf jusqu'a 25k, non-regression pertinente, recette navigateur isolee avec preuve de publication→catalogue) sont satisfaits avec preuves.  
La demo principale n'a **pas** ete integree ; le login formulaire headless reste non valide ; Jooble/ATS restent volontairement OFF ; le go-live exige encore backup + migrations + redemarrage autorises.

**Prochaines actions :**
1. Votre accord explicite pour integration demo + migrations.  
2. Ensuite : backup → migrate → restart 8081 → reindex → smoke manuel (login UI inclus).  
3. Garder Jooble/ATS desactives jusqu'a validation ToS/cle.

**En attente de votre autorisation explicite avant toute integration dans la demo principale.**
