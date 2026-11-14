# HireLink — Architecture d’agrégation mondiale d’offres (étude technique)

**Statut :** étude en lecture seule — **aucune implémentation autorisée** sans validation produit  
**Date :** 2026-11-13  
**Bases :** code actuel (`ExternalJob*`, parsers, frontend), `docs/HIRELINK_PRODUCT_REALITY_AUDIT.md`, `docs/sources-offres-externes.md` (partiellement obsolète), documentation publique Greenhouse / Lever / Ashby / Adzuna / Remotive / Remote OK  

**Règle d’intégrité :** ne jamais présenter une source comme « partenaire officiel » sans contrat ou preuve écrite dans le dépôt. Ne jamais proposer de scraping HTML, de contournement d’auth, ni de republier des offres sans respect des conditions d’attribution.

---

## 1. Objectif produit et non-objectifs

### Objectif

Permettre aux candidats de **consulter dans HireLink de véritables offres publiées dans le monde**, puis d’**ouvrir l’annonce ou le formulaire de candidature d’origine** (hors ATS HireLink pour ces lignes).

### Non-objectifs (explicites)

- Remplacer `job_offers` / `applications` (ATS interne).
- Créer une candidature HireLink pour une offre externe.
- Scraper LinkedIn, Indeed, Rekrute, Emploi.ma, etc.
- Soumettre des candidatures via Harvest / API employeur sans mandat (Greenhouse POST application, Lever POST apply, etc.).
- Affirmer un partenariat Greenhouse Inc., Lever, Ashby, Adzuna, Remotive ou Remote OK sans accord.

### Invariants déjà prouvés dans le code (à conserver)

| Règle | Preuve |
|---|---|
| Tables séparées | `external_job_offers` ? `job_offers` |
| Pas d’apply ATS sur externe | `candidate-home.js` / `offer-card.js` ouvrent `applyUrl \|\| originUrl` ; API applications refuse `ext-*` |
| Attribution visible | `sourceLabel` / `sourceHome` |
| Sync admin | `POST /api/v1/external-offers/sync` (`ADMINISTRATEUR`) |
| Flux vide = conserver | `ExternalJobImportService` `keptPrevious` |
| Absence 7 j | `ABSENCE_BEFORE_HIDE` |

---

## 2. Audit de l’existant

### 2.1 Composants

| Composant | Rôle actuel | Limite pour un catalogue mondial |
|---|---|---|
| `ExternalSourceCatalog` | 2 sources hardcodées (`MAROC_EMPLOI`, `REMOTE_OK`) + flags | Pas de registre multi-boards ; pas de métadonnées légales/attribution |
| `ExternalJobSyncFacade` | `if/else` par code source | Non pluggable ; un connecteur = modification du facade |
| `RemoteOkClient` + `RemoteOkParser` | GET public JSON Remote OK | Fenêtre récente ~100 offres ; remote-only ; ToS attribution dofollow |
| `MarocPartnerFeedParser` + JSON classpath | Feed partenaire **échantillon** | URLs souvent **homepage** ; pas un vrai board mondial |
| `ExternalJobImportService` | Upsert `(source, external_id)`, lock, absence 7 j, admin hide | Snapshot complet par source ; pas d’incrémental `updated_at` ; pas de validation profondeur d’URL ; pas de pays/ville/mode structurés |
| `ParsedExternalJob` | DTO normalisé minimal | Manque `countryCode`, `city`, `workplaceType`, `boardToken`, `urlQuality`, `contentHash` |
| `ExternalJobOffer` / SQL | Colonnes titre, company, location libre, origin/apply | Index insuffisants pour filtres pays/source/date à l’échelle |
| `ExternalJobOfferRepository` | `findByAvailableTrueOrderByPublishedAtDesc()` | **Pas de pagination serveur**, pas de critères |
| Controllers | Liste publique + status admin | Pas de query API riche |
| Frontend | Merge client `/offers/public` + `/external-offers` | Filtres locaux ; pas de filtre source/pays dédiés ; charge tout en mémoire |
| Cron / startup | `MarocPartnerSyncJob`, `RemoteOkSyncJob`, `ExternalJobsStartupSync` | OK pour 1–2 sources ; fragile pour N boards |

### 2.2 Flux actuel (rappel)

```
Source (JSON fichier | HTTP Remote OK)
  ? Parser ? ParsedExternalJob
  ? ExternalJobImportService.importSnapshot(source, jobs)
  ? external_job_offers
  ? GET /external-offers (sources enabled seulement)
  ? candidate-home / landing / offer-card
  ? CTA https externe (pas d’Application HireLink)
```

### 2.3 Écarts vs objectif « catalogue international »

1. **Couverture** : échantillon Maroc + Remote OK optionnel ? monde.  
2. **Qualité des liens** : sample Maroc pointe vers racines de sites.  
3. **Extensibilité** : connecteur = patch Java du facade.  
4. **Découverte UX** : pas de pagination / filtres géo serveur.  
5. **Gouvernance légale** : catalogue figé ; doc `sources-offres-externes.md` encore Remote-OK-centric.  
6. **Greenhouse / Lever / Ashby** absents du code (malgré APIs Job Board publiques documentées).

---

## 3. Étude des sources candidates

Légende d’accès : **Technique** = endpoint documenté utilisable sans contournement.  
**Réutilisation** = ce que les conditions publiques permettent d’affirmer aujourd’hui (à revalider avant prod).  
**Partenariat** = **NON** sauf preuve écrite (aucune dans le dépôt).

### 3.1 Greenhouse — Job Board API

| Critère | Constat |
|---|---|
| Accès technique | **Oui** — `GET https://boards-api.greenhouse.io/v1/boards/{board_token}/jobs` (+ `?content=true`) |
| Auth lecture | Aucune (docs : données Job Board publiques). POST candidature = autre endpoint + clé employeur ? **hors scope HireLink** |
| Lien annonce | `absolute_url` (page job board Greenhouse / job-boards.greenhouse.io) |
| Lien apply | Souvent la même page (formulaire embarqué) ; pas toujours un `applyUrl` distinct |
| Géographie | Celle des entreprises dont on connaît le `board_token` |
| Limites | 1 requête ? board entier (pas de pagination jobs list) ; politesse + cache obligatoires |
| Découverte mondiale | **Pas d’annuaire global** : il faut une **liste d’employeurs / tokens** |
| Réutilisation | API conçue pour exposer les postes publics d’un board. Agréger *beaucoup* de boards tiers : **revue juridique recommandée** ; ce n’est pas un partenariat Greenhouse Inc. |
| Verdict HireLink | **Connecteur prioritaire (phase boards)** — deep links réels, zéro scrape |

Docs : [docs.greenhouse.io/job-board](https://docs.greenhouse.io/job-board)

### 3.2 Lever — Postings API (v0 public)

| Critère | Constat |
|---|---|
| Accès technique | **Oui** — `GET https://api.lever.co/v0/postings/{site}?mode=json` (EU : `api.eu.lever.co`) |
| Auth lecture | Aucune |
| Lien annonce | `hostedUrl` |
| Lien apply | `applyUrl` (**distinction native** — idéal pour HireLink) |
| Champs utiles | `country` (ISO-2), `workplaceType`, `categories.location/commitment`, descriptions plain/HTML, salaires optionnels |
| Pagination | `skip` / `limit` (défaut 100) — grands boards à paginer |
| Découverte | Slug = segment de `jobs.lever.co/{site}` ; **pas d’index mondial** |
| Réutilisation | API publique destinée aux sites carrière / intégrations ; attribution vers Lever hosted URLs recommandée ; POST apply = clé + rate limit ? **ne pas implémenter** |
| Verdict | **Connecteur prioritaire** — meilleure séparation annonce / candidature |

Docs : [github.com/lever/postings-api](https://github.com/lever/postings-api)

### 3.3 Ashby — Public Job Posting API

| Critère | Constat |
|---|---|
| Accès technique | **Oui** — `GET https://api.ashbyhq.com/posting-api/job-board/{JOB_BOARD_NAME}?includeCompensation=true` |
| Auth lecture | Aucune |
| Lien annonce | `jobUrl` |
| Lien apply | `applyUrl` |
| Champs utiles | `workplaceType`, `employmentType`, adresse structurée, `isListed`, descriptions |
| Portée docs | Documentée pour « your organization » / careers page — usage multi-entreprises = **même prudence juridique** que Greenhouse |
| Découverte | Slug `jobs.ashbyhq.com/{name}` ; pas d’API « list all companies » |
| Verdict | **Connecteur prioritaire** aux côtés Lever |

Docs : [developers.ashbyhq.com/docs/public-job-posting-api](https://developers.ashbyhq.com/docs/public-job-posting-api)

### 3.4 Remote OK (déjà branché, off par défaut)

| Critère | Constat |
|---|---|
| Accès | `GET https://remoteok.com/api` |
| Liens | `url` / `apply_url` — souvent deep |
| ToS | Attribution + lien **follow** vers Remote OK ; logo interdit sans permission |
| Couverture | Remote international, fenêtre courte (~100) |
| Verdict | **Optionnel** en complément remote ; déjà dans le code |

### 3.5 Remotive

| Critère | Constat |
|---|---|
| Accès | `GET https://remotive.com/api/remote-jobs` |
| ToS | Lier l’URL Remotive + citer la source ; **interdiction** de republier vers Google Jobs / LinkedIn / Jooble… ; pas de mur email ; ~4 fetch/j |
| Verdict | Possible **si** HireLink accepte ces contraintes UX/SEO ; sinon écarter |

### 3.6 Adzuna API

| Critère | Constat |
|---|---|
| Accès | Clés `app_id` / `app_key` ; `GET .../jobs/{country}/search/{page}` |
| Pays | gb, us, fr, de, … (**pas de code MA** dans les listes publiques courantes) |
| ToS | Usage perso/académique / publication listings avec **logo Adzuna** ; usage org commercial ? essai 14 j puis **licence écrite** |
| Liens | Redirectors Adzuna vers annonces sources |
| Verdict | **Phase licence** seulement — excellent pour couverture multi-pays *après* accord ; pas pour la démo « sans contrat » |

### 3.7 France Travail / LinkedIn / Indeed / sites HTML RH Maroc

| Source | Verdict |
|---|---|
| France Travail | Accès restreint / demande ; déjà noté bloqué dans `sources-offres-externes.md` |
| LinkedIn / Indeed | Pas d’API publique d’agrégation autorisée pour ce cas ? **hors scope** |
| Rekrute / Emploi.ma HTML | Pas d’API publique prouvée dans le dépôt ? **pas de scrape** ; seul un **flux partenaire contractuel** (format `MarocPartnerFeedParser`) |

### 3.8 Autres ATS board APIs (mention)

Workable, SmartRecruiters, Recruitee, Teamtailor exposent souvent des endpoints JSON publics **par entreprise**. Même modèle architectural : **connecteur + allowlist de boards**. À évaluer un par un (docs + ToS) avant ajout — **pas de partenariat implicite**.

### 3.9 Synthèse stratégique

| Stratégie | Mécanisme | Couverture | Qualité liens | Faisabilité |
|---|---|---|---|---|
| **A. Boards ATS publics** | Greenhouse + Lever + Ashby sur allowlist | Mondiale mais **biaisée** vers tech / scale-ups | Excellente | Haute |
| **B. Agrégateur licencié** | Adzuna (après contrat) | Large multi-pays | Variable (redirect) | Moyenne (juridique) |
| **C. Remote niche** | Remote OK / Remotive | Remote only | Bonne | Haute (ToS stricts) |
| **D. Partenaire Maroc** | JSON HireLink v1 signé | Locale | Exige deep links contractuels | Haute si partenaire réel |

**Recommandation :** A ? C ? D ? B (licence), jamais scrape.

---

## 4. Architecture cible extensible

### 4.1 Principes

1. **Connecteurs indépendants** implémentant une interface commune.  
2. **Normalisation** vers un modèle enrichi (évolution de `ParsedExternalJob`).  
3. **Import** reste source-agnostique (`ExternalJobImportService` ou successeur).  
4. **Gouvernance** : chaque board / source a un enregistrement admin (enabled, token, attribution, ToS notes).  
5. **Séparation ATS** : zéro écriture dans `job_offers` / `applications` pour l’externe.  
6. **Outbound only** : HireLink n’appelle jamais les POST apply employeur.

### 4.2 Interfaces proposées (conception — non codées)

```text
ExternalJobConnector
  id(): String                     // GREENHOUSE | LEVER | ASHBY | REMOTE_OK | …
  supportsBoards(): boolean        // true pour ATS multi-entreprises
  fetch(context: SyncContext): ConnectorFetchResult

SyncContext
  sourceCode, boardKey?, since?, cursor?, httpBudget, userAgent

ConnectorFetchResult
  jobs: List<NormalizedExternalJob>
  nextCursor?, truncated: boolean, rawStats

NormalizedExternalJob  (évolution ParsedExternalJob)
  provider, boardKey, externalId
  title, company
  originUrl, applyUrl
  countryCode?, city?, locationRaw?
  workplaceType?, contractLabel?
  descriptionPlain?, skills?
  salaryMin?, salaryMax?, currency?
  publishedAt?, updatedAt?
  contentHash
  urlQuality: DEEP | HOSTED_APPLY | GENERIC_HOME | INVALID
```

`ExternalJobSyncFacade` devient un **orchestrateur** qui découvre les connecteurs Spring (`List<ExternalJobConnector>`) + les boards actifs en base, au lieu d’un `if/else`.

### 4.3 Registre de boards (nouvelle table proposée)

`external_job_boards` (migration future, **pas exécutée maintenant**) :

| Colonne | Rôle |
|---|---|
| `id` | PK |
| `provider` | GREENHOUSE / LEVER / ASHBY / … |
| `board_key` | token / slug |
| `company_name` | libellé affichage |
| `home_url` | URL carrière / board |
| `region` | GLOBAL / EU (Lever) |
| `enabled` | sync on/off |
| `attribution_ compulsory` | texte / mode lien |
| `last_success_at`, `last_error` | supervision |
| `sync_cron_override` | optionnel |

Clé logique sync : `source = provider` **ou** `source = provider + ":" + board_key`  
**Recommandation :** `source = GREENHOUSE` et `external_id = boardKey + ":" + jobId` (évite explosion de codes source) **ou** source composite `GREENHOUSE:gitlab`. Préférer **`external_id` préfixé board** + `source=GREENHOUSE` pour garder `ExternalSourceCatalog` simple.

### 4.4 Synchronisation incrémentale

| Mode | Quand | Comment |
|---|---|---|
| Full snapshot | Premier sync / force admin | Comportement actuel `importSnapshot` |
| Incrémental soft | Greenhouse `updated_at`, Lever champs date, Ashby `publishedAt` | Ne retirer que les IDs absents d’un **full** réussi |
| Cursor | Lever `skip/limit` | Boucler jusqu’à épuisement sous budget HTTP |
| Absence | Inchangé 7 j après full | Pour feeds fenêtre (Remote OK) : documenter que l’absence ? clôture certaine |

**Règle anti-casse :** un connecteur en erreur HTTP/parse ? `keptPrevious=true`, **aucune** purge.

### 4.5 Déduplication

1. **Primaire :** `(source, external_id)` (existant).  
2. **Secondaire (soft) :** hash normalisé `(company_fold, title_fold, origin_host+path)` pour signaler doublons cross-provider (même poste Greenhouse + Lever) ? **ne pas fusionner automatiquement** ; flag `duplicate_of` / admin review.  
3. **Intra-snapshot :** déjà géré (`seen` set).

### 4.6 Validation des URL

Pipeline `UrlQualityService` (proposé) :

| Check | Action |
|---|---|
| Schéma `https` uniquement | rejeter |
| Host blocklist (javascript:, data:) | rejeter |
| Path = `/` ou trop générique (liste : `/`, `/jobs`, `/careers` sans id) | `urlQuality=GENERIC_HOME` ? **exclure de la vitrine publique** (ou badge « lien imprécis ») |
| HEAD/GET léger optionnel (échantillon, pas à chaque sync massif) | `link_status` OK / REDIRECT / DEAD |
| Redirection vers domaine login / captcha | marquer `apply_degraded` |

**Politique produit recommandée :** n’afficher que `DEEP` ou `HOSTED_APPLY`. Les `GENERIC_HOME` du feed Maroc actuel seraient **filtrés** — ce qui masque l’échantillon actuel tant qu’il n’a pas de deep links (comportement voulu pour la qualité).

### 4.7 Expiration et masquage

- Conserver `available` / `hidden_by_admin` / absence 7 j.  
- Ajouter `withdrawn_reason` : `ABSENT_FROM_FEED` | `LINK_DEAD` | `ADMIN` | `BOARD_DISABLED`.  
- Désactiver un board ? cacher ses offres (comme source disabled aujourd’hui).

### 4.8 Supervision

Étendre `external_sync_runs` :

- `board_key`, `http_calls`, `duration_ms`, `error_code`  
- Métriques admin : taux `GENERIC_HOME`, taux liens morts, volume par pays/provider  
- Alertes : 3 échecs consécutifs ? disable temporaire board

### 4.9 Schéma d’évolution `external_job_offers` (proposé)

Sans exécution :

- `country_code` CHAR(2) NULL  
- `city` VARCHAR(120) NULL  
- `workplace_type` VARCHAR(16) NULL  
- `board_key` VARCHAR(80) NULL  
- `content_hash` CHAR(64) NULL  
- `url_quality` VARCHAR(20) NOT NULL DEFAULT 'UNKNOWN'  
- `origin_host` VARCHAR(120) NULL (index)  
- Index : `(available, country_code, published_at)`, `(available, source, published_at)`, `(board_key, available)`

Allonger `external_id` à 160 si `board:jobId`.

---

## 5. Distinction URL annonce vs URL candidature

| Concept | Champ | UX |
|---|---|---|
| **Annonce** | `originUrl` | « Voir l’offre » |
| **Candidature d’origine** | `applyUrl` | « Postuler chez l’émetteur » |
| Repli | si `applyUrl` vide ? `originUrl` | comportement actuel |

### Mapping connecteurs

| Provider | originUrl | applyUrl |
|---|---|---|
| Greenhouse | `absolute_url` | `absolute_url` (même page) + note UI |
| Lever | `hostedUrl` | `applyUrl` |
| Ashby | `jobUrl` | `applyUrl` |
| Remote OK | `url` | `apply_url` else `url` |
| Maroc partner | `url` | `applyUrl` — **exiger deep links au contrat** |

### Gestion des cas dégradés

| Cas | Comportement |
|---|---|
| Lien mort | masquer apply ; badge « Lien indisponible » ; ne pas créer de faux bouton |
| Redirect chaîne | stocker URL finale si sûre ; sinon garder canonique source |
| Page générique | exclure vitrine ou CTA désactivé |
| Apply = login wall | toujours OK (c’est le site d’origine) — HireLink n’automatise pas |

**Frontend :** deux boutons distincts quand `applyUrl ? originUrl` ; un seul sinon. Ne jamais mélanger avec « Postuler sur HireLink ».

---

## 6. Interface professionnelle (catalogue)

### 6.1 API proposée

`GET /api/v1/external-offers` enrichi (rétrocompatible) :

| Param | Effet |
|---|---|
| `q` | titre / entreprise / texte |
| `country` | ISO-2 |
| `city` | libellé / fold |
| `workplace` | REMOTE / HYBRID / ONSITE |
| `contract` | label normalisé |
| `company` | |
| `source` / `provider` | GREENHOUSE, LEVER, … |
| `board` | board_key |
| `sinceDays` | |
| `page`, `size` | pagination obligatoire |
| `sort` | `published_at,desc` (défaut) |

Réponse : `{ items, page, size, total, facets? }`.

Conserver `GET /offers/public` **inchangé** pour l’ATS interne.

Endpoint catalogue unifié **optionnel** plus tard : `GET /api/v1/catalog/jobs?origin=hirelink|external|all` — **attention** à ne pas fusionner les modèles apply.

### 6.2 Frontend

- Page / section « Offres dans le monde » (landing + espace candidat).  
- Filtres : pays (drapeaux via `PlaceCatalogService`), ville, métier (q), contrat, télétravail, entreprise, **source**, date.  
- Chip provenance : `Source : Lever · Acme` + lien `home_url` / attribution.  
- Pagination serveur (ne plus tout charger).  
- Séparateur clair vs « Offres HireLink » (candidature suivie).  
- Filtres actuels `f-origin` restent valides.

### 6.3 Matching

- Garder score **indicatif skills-only** sur externe (audit réalité).  
- Ne pas prétendre au score pondéré 6 critères sans données structurées offre HireLink.

---

## 7. Préservation ATS HireLink

| Élément | Mesure |
|---|---|
| `job_offers` | Aucun connecteur n’écrit dedans |
| `applications` | Refus explicite des ids `ext-*` (déjà testé) |
| Pipeline / entretiens / score | Uniquement interne |
| Vérif ICE / sièges / billing | Inchangés |
| UI recruteur | Pas de mélange des externes dans « Mes offres » |
| Feature flag | `hirelink.external-jobs.global.enabled` pour activer le catalogue mondial sans toucher l’ATS |

Tests de non-régression obligatoires : apply interne, pipeline, isolation org.

---

## 8. Plan d’implémentation par étapes

> **Gel :** attendre autorisation avant toute modification de code / DB.

### Étape 0 — Gouvernance (0,5–1 j)

- Mettre à jour la matrice légale (ce document + revue counsel).  
- Décider allowlist initiale (ex. 20–50 boards publics tech connus **sans** les présenter comme partenaires).  
- Définir User-Agent : `HireLinkJobSync/1.0 (+https://hirelink.ma; contact@…)`.

### Étape 1 — Socle connecteurs (2–4 j)

- Interface `ExternalJobConnector` + registre Spring.  
- Refactor `ExternalJobSyncFacade` sans changer le comportement Remote OK / Maroc.  
- Tests de non-régression parsers existants.

### Étape 2 — Modèle données (1–2 j)

- Migration V00x : colonnes + `external_job_boards` + indexes.  
- Allonger `external_id` ; `UrlQuality`.  
- Rollback SQL documenté.

### Étape 3 — Connecteurs Lever + Ashby + Greenhouse (3–5 j)

- Clients HTTP (timeouts, retry backoff, budget).  
- Parsers ? `NormalizedExternalJob`.  
- Sync par board allowlist.  
- Admin CRUD boards (enable/disable/sync one).

### Étape 4 — Qualité URL + filtres API (2–3 j)

- Filtre `GENERIC_HOME` hors vitrine.  
- Pagination / facets.  
- Supervision runs enrichis.

### Étape 5 — Frontend catalogue (2–4 j)

- Filtres pays/source/remote + pagination.  
- Double CTA Voir / Postuler.  
- Copy honnête : « Annonces publiques de sites carrière » ? « partenaires ».

### Étape 6 — Remote OK / Remotive (optionnel, 1–2 j)

- Réactiver Remote OK seulement si produit OK avec remote international + ToS.  
- Remotive si contraintes SEO acceptées.

### Étape 7 — Adzuna (plus tard)

- Uniquement après **licence écrite** + attribution logo.

### Étape 8 — Partenaire Maroc réel

- Remplacer le JSON sample par un feed HTTPS signé avec **deep links** obligatoires.

---

## 9. Tests automatisés

| Niveau | Cas |
|---|---|
| Unit | Parsers Greenhouse/Lever/Ashby (fixtures JSON) ; `UrlQuality` ; mapping apply/origin |
| Unit | Dedupe `board:jobId` ; hash content |
| Integration | Sync board mock WireMock ; empty/error ? keep previous ; disable board ? hide |
| Integration | `POST /applications` avec `ext-*` ? 4xx (existant) |
| Integration | Pagination `GET /external-offers?page=` |
| Contract | Fixtures « absolute_url / hostedUrl / applyUrl » |
| FE (Playwright lecture) | CTA externe n’ouvre pas wizard HireLink ; provenance visible |
| Régression ATS | Publish offer, apply, pipeline status |

**Ne pas** appeler les vrais boards dans la CI (stubs). Un job nightly optionnel « canary » 1 board public avec quota.

---

## 10. Migrations éventuelles

1. `V004__external_jobs_global.sql` (nom indicatif) : alter `external_job_offers` + create `external_job_boards` + indexes.  
2. Data backfill : `url_quality` pour lignes existantes (Maroc sample ? probablement `GENERIC_HOME`).  
3. **Pas de drop** de `job_offers` / `applications`.  
4. Feature flags properties :  
   - `hirelink.external-jobs.greenhouse.enabled=false`  
   - `hirelink.external-jobs.lever.enabled=false`  
   - `hirelink.external-jobs.ashby.enabled=false`  
   - `hirelink.external-jobs.boards-allowlist-file=`  

---

## 11. Risques

| Risque | Impact | Mitigation |
|---|---|---|
| Interprétation ToS multi-boards | Juridique | Allowlist courte ; pas de revendication partenariat ; counsel |
| Rate limiting / blocage IP | Sync down | Budget, cache, jitter, 1 board à la fois |
| Explosion volume | Perf DB / RAM front | Pagination serveur ; indexes ; caps par board |
| Liens génériques | UX trompeuse | Filtre `url_quality` |
| Doublons cross-ATS | Bruit | Soft dedupe + tri |
| Doc obsolète | Mauvaise décision produit | Remplacer `sources-offres-externes.md` après go |
| Confusion candidat ATS vs externe | Support | Copy + CTA séparés (déjà amorcé) |
| Adzuna sans licence | Non-conformité | Ne pas brancher |
| Scraping « tentant » | Illégal / ToS | **Interdit** dans l’archi |

---

## 12. Critères d’acceptation

1. Une offre Greenhouse/Lever/Ashby affichée a un **lien https profond** (pas homepage générique).  
2. Le bouton externe n’appelle **jamais** `POST /applications`.  
3. Les offres HireLink et le pipeline restent fonctionnels à l’identique (tests verts).  
4. Admin peut activer/désactiver un board sans redéployer le code connecteur.  
5. Sync en échec ne vide pas le catalogue.  
6. UI montre **provider + entreprise** et ouvre `applyUrl` ou `originUrl`.  
7. API externe paginée ; pas de chargement monolithique > N (ex. 50) sans page.  
8. Aucune source n’est libellée « partenaire officiel » sans champ `partnership_proven=true` (défaut false).  
9. Remote OK / Remotive : attribution conforme si activés.  
10. Rollback possible en < 15 min (flags off).

---

## 13. Procédure de retour arrière

1. **Flags** : désactiver `greenhouse/lever/ashby/global.enabled` ? sync stop + filtre liste enabled.  
2. **Boards** : `enabled=0` en masse.  
3. **Code** : revert PR connecteurs ; facade redevient Remote OK / Maroc.  
4. **DB** : migration down optionnelle (garder colonnes nullable inoffensives de préférence).  
5. **Front** : feature flag cache catalogue mondial.  
6. **Données** : ne pas DELETE massif ; `available=0` suffit.  
7. **Comm** : retirer mentions UI « monde » si catalogue vide.

---

## 14. Clarification importante vs doc historique

`docs/sources-offres-externes.md` affirme que Greenhouse/Lever « ne sont pas une source autorisée » et assimilait leur lecture à du scraping.  
**Correction technique (2026-11) :** Greenhouse, Lever et Ashby exposent des **Job Board / Postings APIs JSON documentées**, sans clé pour la lecture des postes **publics d’un board connu**. Ce n’est pas du scraping HTML.  
En revanche, ce n’est **pas** une autorisation commerciale implicite d’agréger tout le web, ni un partenariat, ni un annuaire mondial magique : **allowlist + conformité + deep links**.

Le feed `MAROC_EMPLOI` classpath reste un **échantillon HireLink**, pas une preuve de partenariat Rekrute/Emploi.ma.

---

## 15. Décision demandée avant implémentation

Merci de trancher :

1. **Allowlist boards** initiale (combien, quels secteurs) ?  
2. Activer Remote OK / Remotive en plus des ATS boards ?  
3. Budget juridique pour Adzuna ou autre agrégateur licencié ?  
4. Filtrer strictement les `GENERIC_HOME` (cassera la vitrine sample Maroc actuelle) ?  
5. Priorité UX : catalogue séparé vs onglet « Provenance » enrichi ?

**Aucun code, secret, migration ou redémarrage n’a été effectué dans le cadre de cette étude.**  
Implémentation uniquement après autorisation explicite.
