# HireLink — Rapport d'implementation Phase 3

**Date :** 2026-11-13  
**Autorisation :** implementation controlee en environnement isole uniquement  
**Schema de tests :** `hirelink_ext_phase03` (`ddl-auto=create-drop` dans les tests)  
**Migration versionnee :** `database/migrations/V006__catalog_listings.sql` (non appliquee sur `hirelink`)  
**Commit / push :** aucun  
**Demo :** non redemarree ; sync externe non activee sur la demo

---

## 1. Fichiers modifies / ajoutes (Phase 3)

### Backend (nouveaux)
- `controller/CatalogOfferController.java` — `GET /api/v1/catalog/offers`, `POST /api/v1/catalog/reindex` (admin)
- `service/CatalogOfferService.java` — recherche paginee + mode Pour toi
- `service/CatalogListingIndexer.java` — projection depuis `job_offers` + `external_job_offers`
- `model/CatalogListing.java`, `model/CatalogForYouRun.java`
- `repository/CatalogListingRepository.java`, `repository/CatalogForYouRunRepository.java`
- `service/external/JoobleJobParser.java`, `JoobleHttpClient.java`, `JoobleJobConnector.java`
- `database/migrations/V006__catalog_listings.sql`
- `src/test/java/.../CatalogPhase3IsolationTest.java`
- `src/test/resources/fixtures/jooble-ma-200.json`

### Backend (modifies)
- `config/SecurityConfig.java` — `GET /api/v1/catalog/offers` en `permitAll`
- `service/ExternalSourceCatalog.java` — source `JOOBLE` (desactivee)
- `application.properties` (+ test) — props catalogue + Jooble OFF

### Frontend
- `frontend/js/candidate-home.js` — catalogue paginé serveur + repli legacy si endpoint absent
- `frontend/js/landing-offers.js` — idem
- `frontend/app/candidate.html` — filtre pays (MA par defaut / tous)
- `frontend/index.html` — filtre pays landing

---

## 2. Architecture reellement implementee

```
job_offers ──┐
             ├─► CatalogListingIndexer.rebuild() ──► catalog_listings
external_* ──┘         (exclut MAROC_EMPLOI, quarantaine, GENERIC_HOME/INVALID)

GET /api/v1/catalog/offers
  ├─ filtres SQL (q, country, city, contract, workMode, source, origin, sinceDays)
  ├─ ORDER BY sort_at DESC, id DESC
  ├─ COUNT(*) puis LIMIT/OFFSET  (pas de chargement massif en memoire)
  └─ forYou=true :
        1) SELECT id, skills_text (candidats skill-bearing, plafonnes)
        2) SkillMatchingService.computeMatchScore (existant uniquement)
        3) insert eligibles (>= seuil) dans catalog_for_you_run(run_id)
        4) COUNT + page SQL JOIN/IDs
        5) delete run_id
```

DTO unifie : `external`, `id`, `origin`, `source`, `sourceLabel`, `title`, `companyName`, `location`, `city`, `countryCode`, `contractType`, `workMode`, `requiredSkills`, `description`, URLs/qualite si externe, `publishedAt` seulement si `publishedAtTrusted`, `matchScore`/`matchAvailable`/`matchLabel` (indicatif pour externe).

Legacy conserve : `GET /offers/public`, `GET /external-offers`.

---

## 3. Strategie de pagination et performances

| Aspect | Choix |
|---|---|
| Stockage | Projection denormalisee `catalog_listings` |
| Tri | `sort_at DESC, id DESC` (deterministe) |
| Filtrage | Dans le `WHERE` SQL **avant** `LIMIT/OFFSET` |
| Comptage | `COUNT(*)` avec les memes predicats |
| Pour toi | Filtre d'eligibilite **avant** pagination via table temporaire de run |
| Plafond Pour toi | `hirelink.catalog.for-you-max-scan=5000` (sinon HTTP 400 a affiner) |
| size max | 50 |

**Mesures :** suite `CatalogPhase3IsolationTest` — 5 tests, ~60 s (boot Spring inclus), **Failures: 0**.  
La pagination SQL ne charge qu'une page d'entites (`LIMIT`), pas le catalogue entier.  
Aucune mesure de charge multi-milliers hors suite (schema isole recreate a chaque run create-drop).

---

## 4. Filtres operationnels

| Param | Defaut / notes |
|---|---|
| `q` | LIKE sur `search_blob` |
| `country` | Defaut produit UI `MA` ; `ALL`/`*` = sans filtre |
| `city` | egalite case-insensitive |
| `contract` | label exact |
| `workMode` | normalise REMOTE/HYBRID/ONSITE |
| `source` | code source |
| `origin` | `internal` / `external` / `all` |
| `sinceDays` | uniquement si `published_trusted=1` et `published_at` non null |
| `forYou` | boolean |
| `page`, `size` | pagination |

---

## 5. Fonctionnement exact de « Pour toi »

1. Sans utilisateur ou sans competences confirmees (`SkillMatchingService.hasConfirmedSkills`) → `totalElements=0`, aucun score fabrique.  
2. Avec competences : score = `computeMatchScore(profil, skills_text de l'offre)` uniquement.  
3. Inclusion si `score >= hirelink.catalog.for-you-threshold` (defaut **50**).  
4. Offres sans `skills_text` exclues du scan Pour toi.  
5. Externe : `matchLabel` indique que le score est **indicatif** (competences declarees par la source).  
6. Pagination et totaux portes sur le sous-ensemble eligible (pas de pages incompletes artificielles).

---

## 6. Etat de l'integration Jooble

| Element | Etat |
|---|---|
| Connecteur | Present (`JoobleJobConnector`) |
| Flag | `hirelink.external-jobs.jooble.enabled=false` |
| Cle | vide — **aucune cle inventee** |
| Appels reseau | aucun tant que disabled / sans cle |
| Catalogue sources | `JOOBLE` liste, `enabled=false` |
| Attribution | liens Jooble conserves (`originUrl`/`applyUrl` = `link`) |
| Fixtures | `fixtures/jooble-ma-200.json` |
| Tests HTTP | 200 / 403 / 429 / timeout via `HttpServer` local |

Activation future : cle officielle + ToS + quotas (doc publique ~100 req/j) + autorisation explicite.

ATS Greenhouse / Lever / Ashby : restent `enabled=false` (aucun canary live ajoute en Phase 3).

---

## 7. Resultats des tests

### Automatises (schema `hirelink_ext_phase03`)

Commande :
`mvn -Dtest=CatalogPhase3IsolationTest test`  
**Result : Tests run: 5, Failures: 0, Errors: 0 — BUILD SUCCESS**

Couverture verifiee :
- Pagination + comptage exact, tri stable, absence de doublons inter-pages  
- Recherche sans resultats  
- Filtres ville / contrat / origin  
- Quarantaine hors catalogue ; `MAROC_EMPLOI` exclu  
- Pour toi sans competences → vide  
- Pour toi avec score reel ≥ 50  
- Pas de faux scores sur externes sans matchAvailable  
- Liens externes fiables (DEEP)  
- Candidature interne OK ; candidature via id externe refusee  
- Jooble desactive sans cle ; fixtures 200/403/429/timeout  

### Navigateur

**Non execute** (demo non redemarree, backend 8081 sans endpoint catalogue).  
Le FE inclut un **repli legacy** vers `/offers/public` + `/external-offers` si `/catalog/offers` echoue, pour ne pas casser la vitrine 5500 actuelle.

---

## 8. Captures / preuves UI

Pas de captures navigateur Phase 3 (pas d'integration demo).  
Preuves : surefire `CatalogPhase3IsolationTest` (BUILD SUCCESS) ; health demo ci-dessous.

---

## 9. Integrite des bases et de la demo

| Controle | Resultat |
|---|---|
| `GET http://127.0.0.1:8081/api/v1/health` | **UP** |
| Frontend `http://127.0.0.1:5500/` | **200** |
| `hirelink.external_job_offers` | **106** (inchange) |
| Tables `catalog_%` sur `hirelink` | **absentes** (aucune migration appliquee) |
| Redemarrage demo | **non** |
| Commit / push | **non** |
| Secrets | **non modifies** |

---

## 10. Risques et blocages restants

1. **Index catalogue** : la projection doit etre reconstruite (`POST /catalog/reindex` admin ou appel service) apres imports ; `rebuild-on-startup=false` pour proteger la demo.  
2. **Geo** : `country_code` derive du catalogue de lieux ; offres hors catalogue de villes peuvent manquer le filtre `MA`.  
3. **Pour toi a tres grand volume** : scan Java plafonne a 5000 lignes skill-bearing ; au-dela, affiner les filtres.  
4. **Jooble** : droits, cle, quotas, attribution — non actives.  
5. **ATS multi-boards** : blocage juridique Phase 2 inchange.  
6. **FE facets** (entreprise / competence / reactivite / candidature) : partiellement client sur la page courante.  
7. Integration demo : le backend 8081 n'expose pas encore `/catalog/offers` tant qu'il n'est pas reconstruit/redéploye avec cet autorisation.

---

## 11. Procedure de retour arriere

1. Ne pas appliquer `V006` sur `hirelink` (deja le cas).  
2. Revenir FE : restaurer les fetch legacy seuls dans `candidate-home.js` / `landing-offers.js` (ou garder le repli actuel).  
3. Desactiver/ignorer beans catalogue : props deja non invasives sur demo non redemarree.  
4. Jooble : deja `enabled=false` + cle vide.  
5. Schema isole : `DROP DATABASE hirelink_ext_phase03;` si nettoyage souhaite.

---

## 12. Recommandations avant tout deploiement demo

1. Accord explicite pour rebuild/redemarrage backend 8081 avec le code Phase 3.  
2. Appliquer `V006` **uniquement** apres backup, ou laisser Hibernate `update` sur un schema de staging — **pas** en aveugle sur `hirelink` sans go.  
3. Premier demarrage : `POST /api/v1/catalog/reindex` (admin) puis verifier `GET /catalog/offers?country=MA&page=0&size=20`.  
4. Garder Jooble et ATS a `false` jusqu'a cle + avis juridique.  
5. Verifier manuellement parcours candidat (Pour toi, pagination, candidature interne) et recruteur (pipeline inchange).  
6. Confirmer exclusion sample `MAROC_EMPLOI` du catalogue public.  
7. Mesurer latence COUNT/page sur le volume reel apres reindex.

**En attente de votre accord explicite avant toute integration dans la demo principale.**