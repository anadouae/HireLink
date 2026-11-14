# HireLink — Plan d’implémentation : agrégation mondiale d’offres

**Mode :** planification uniquement (read-only) — **aucune implémentation**  
**Date :** 2026-11-13  
**Entrées vérifiées :** `docs/GLOBAL_JOB_AGGREGATION_ARCHITECTURE.md`, `docs/HIRELINK_PRODUCT_REALITY_AUDIT.md`, code `ExternalJob*`, `application.properties`, frontend candidat, migrations `V000`–`V003`  
**Légende de confiance :** **FAIT** (code/docs vérifiés) · **DOC PUBLIQUE** (API/ToS éditeur, à revalider avant prod) · **HYPOTHÈSE** · **À CONFIRMER** (décision produit / juridique / terrain)

---

## 1. Résumé exécutif

HireLink dispose déjà d’un **socle d’offres externes** distinct de l’ATS (`external_job_offers`, sync admin, CTA hors plateforme). Ce socle est **trop étroit et trop fragile** pour un catalogue mondial : deux sources hardcodées, un facade en `if/else`, pas de pagination serveur, et un feed Maroc **échantillon** dont les URLs sont souvent des **homepages**.

**Stratégie recommandée (progressive, légale, réaliste) :**

1. **Qualité d’abord** — refuser les liens génériques ; séparer démo et données réelles.  
2. **Connecteurs ATS publics** — Greenhouse, Lever, Ashby via **allowlist d’entreprises** (APIs JSON documentées, sans scrape).  
3. **Maroc** — boards ATS d’employeurs ayant des postes MA + futur feed partenaire **contractuel** à deep links (remplacer le sample).  
4. **UX catalogue** — filtres / pagination serveur + provenance claire.  
5. **Extension** — Remote OK / Remotive (ToS), puis Adzuna **seulement avec licence**.

Les entreprises **n’ont pas besoin** d’un compte HireLink pour apparaître si leur job board public est sur l’allowlist. Les candidatures externes restent **sur le site d’origine** ; l’ATS interne (`job_offers` / `applications`) est **hors portée** des connecteurs.

**Première phase recommandée :** Phase 0 + Phase 1 (préparation + qualité des données), **avant** tout appel réseau réel à Greenhouse/Lever/Ashby.

---

## 2. Vérification des conclusions d’architecture contre le code

| Conclusion architecture | Vérification code | Statut |
|---|---|---|
| Tables séparées externe / interne | `ExternalJobOffer` ? `JobOffer` ; SQL `external_job_offers` | **FAIT** |
| Apply externe = URL, pas ATS | `candidate-home.js`, `offer-card.js` : `applyUrl \|\| originUrl` | **FAIT** |
| Sync admin only | `ExternalJobController.sync` `@PreAuthorize ADMINISTRATEUR` | **FAIT** |
| Flux vide conserve | `ExternalJobImportService` `keptPrevious` | **FAIT** |
| Absence 7 jours | `ABSENCE_BEFORE_HIDE = Duration.ofDays(7)` | **FAIT** |
| Catalogue 2 sources | `ExternalSourceCatalog` : `MAROC_EMPLOI`, `REMOTE_OK` | **FAIT** |
| Maroc on / Remote OK off | `application.properties` | **FAIT** |
| Facade non pluggable | `ExternalJobSyncFacade` branches `if` | **FAIT** |
| Pas de pagination repo | `findByAvailableTrueOrderByPublishedAtDesc()` seulement | **FAIT** |
| DTO minimal | `ParsedExternalJob` (pas de country/urlQuality) | **FAIT** |
| Sample Maroc homepages | `feeds/maroc-emploi-partenaires.v1.json` ? `rekrute.com/`, `emploi.ma/` | **FAIT** |
| Greenhouse/Lever/Ashby absents | Aucune classe connecteur | **FAIT** |
| `sources-offres-externes.md` obsolète (Remote OK only) | Doc du 10/11 vs code Maroc enabled | **FAIT** (doc drift) |
| Apply `ext-*` refusé | Controllers/tests d’intégration (id numérique JobOffer) | **FAIT** (parcours réel) |

Les conclusions du document d’architecture restent **valides**. Ce plan les transforme en phases exécutables.

---

## 3. Architecture cible (rappel opérationnel)

```
[Allowlist boards / flags]
        ?
        ?
 ExternalJobConnector (SPI) ?? Greenhouse | Lever | Ashby | RemoteOk | MarocFeed | …
        ?
        ?
 NormalizedExternalJob (+ urlQuality, country, workplace, boardKey, contentHash)
        ?
        ?
 UrlQualityGate ? rejette GENERIC_HOME / INVALID
        ?
        ?
 ExternalJobImportService (upsert source+externalId, absence, lock)
        ?
        ?
 external_job_offers (+ indexes)
        ?
        ?
 GET /external-offers?page&country&…   ?   job_offers / applications
        ?
        ?
 UI : « Voir l’annonce » / « Postuler chez l’émetteur » + attribution
```

### Classes / fichiers

| Action | Éléments |
|---|---|
| **Conserver** | `ExternalJobImportService` (cœur upsert), `ExternalJobOffer`, `ExternalSyncRun`, controllers admin hide, frontend CTA externe, séparation ATS |
| **Faire évoluer** | `ExternalJobSyncFacade` ? orchestrateur SPI ; `ExternalSourceCatalog` ? providers + boards ; `ParsedExternalJob` ? modèle enrichi (ou wrapper) ; `ExternalJobController` ? query paginée ; `ExternalJobOfferRepository` ; `admin.js` sources ; `candidate-home.js` / `landing-offers.js` / `offer-card.js` |
| **Proposer (nouveaux, non créés)** | `ExternalJobConnector`, `GreenhouseConnector`, `LeverConnector`, `AshbyConnector`, `UrlQualityService`, `ExternalBoardRegistry` / entité `ExternalJobBoard`, `ExternalOfferQueryService`, DTOs page/facets, tests WireMock |

---

## 4. Tableau comparatif des sources

| Source | Méthode | Géo réelle | Offres MA | Auth lecture | Attribution / restrictions | Coût / quotas | URLs directes | Offres disparues | Confiance | Preuves |
|---|---|---|---|---|---|---|---|---|---|---|
| **Greenhouse Job Board API** | `GET boards-api…/boards/{token}/jobs` | Boards allowlistés | **Oui si** l’employeur publie des postes MA | Aucune (GET) | Données board publiques ; **pas** = partenariat Greenhouse Inc. ; revue juridique pour agrégation multi-boards | Gratuit techniquement ; politesse + cache ; board entier / requête | `absolute_url` | Absentes du prochain full sync ? règle 7 j | **DOC PUBLIQUE** + **FAIT** absence code | [docs.greenhouse.io/job-board](https://docs.greenhouse.io/job-board) |
| **Lever Postings API** | `GET api.lever.co/v0/postings/{site}?mode=json` (+ EU) | Idem | **Oui** : ex. filtres Casablanca observés sur boards publics (Yassir, Theodo) — **pas un partenariat** | Aucune | Lien vers `hostedUrl`/`applyUrl` ; robots Crawl-delay ~1s (**DOC PUBLIQUE**) ; POST apply hors scope | Gratuit lecture ; pagination `skip/limit` | `hostedUrl` + `applyUrl` | Idem full sync | **DOC PUBLIQUE** | [github.com/lever/postings-api](https://github.com/lever/postings-api) ; pages jobs.lever.co publiques |
| **Ashby Job Posting API** | `GET api.ashbyhq.com/posting-api/job-board/{name}` | Idem | **Possible** (ex. postes localisés Morocco sur boards publics) — **pas un partenariat** | Aucune | Docs orientées « your org » ; prudence multi-board | Payloads lourds si descriptions ; `isListed` | `jobUrl` + `applyUrl` | Idem | **DOC PUBLIQUE** | [developers.ashbyhq.com](https://developers.ashbyhq.com/docs/public-job-posting-api) |
| **Remote OK** | Déjà dans le code | Remote mondial | Faible / non ciblé MA | Aucune | Attribution + lien **follow** ; logo interdit sans accord | Fenêtre ~100 ; cron existant | Oui (souvent) | Fenêtre glissante ? règle 7 j imparfaite | **FAIT** code + **DOC PUBLIQUE** ToS | `RemoteOkClient`, legal banner JSON |
| **Remotive** | `GET remotive.com/api/remote-jobs` | Remote | Faible | Aucune | Lien Remotive + source ; **pas** de republication Google/LinkedIn/Jooble ; ?~4 req/j | Gratuit public strict | URL Remotive (pas toujours employeur) | Idem | **DOC PUBLIQUE** | remotive.com/api |
| **Adzuna** | Search API + clés | Multi-pays (gb, us, fr…) | **Non listé** dans codes pays API courants | `app_id`/`app_key` | Logo Adzuna ; org commercial ? essai 14 j puis **licence** | Plans / quotas compte | Redirect Adzuna | Via fraîcheur search | **DOC PUBLIQUE** | developer.adzuna.com ToS |
| **MAROC_EMPLOI sample** | Classpath JSON | Maroc (fictionnel) | Sample 8 offres | N/A | **Pas** un partenariat Rekrute/Emploi.ma | N/A | **Souvent homepage** | N/A | **FAIT** | `maroc-emploi-partenaires.v1.json` |
| **Feed partenaire Maroc (cible)** | Même parser HTTPS | Maroc | **Oui** si contrat | Selon contrat | Contrat + deep links obligatoires | Selon partenaire | Exigées | Snapshot + 7 j | **À CONFIRMER** | Aucun contrat dans le dépôt |
| **Rekrute / Emploi.ma HTML** | — | Maroc | — | — | Pas d’API publique prouvée ici | — | — | — | **Hors scope** (pas de scrape) | — |
| **LinkedIn / Indeed** | — | — | — | — | Pas d’API agrégation autorisée pour ce cas | — | — | — | **Hors scope** | — |
| **France Travail** | API restreinte | FR | Non | Demande d’accès | Consentements partenaires | — | — | — | **Bloqué** (déjà noté) | `sources-offres-externes.md` |
| **Workday / SmartRecruiters / …** | Souvent JSON par tenant | Variable | Possible | Variable | Par éditeur | Variable | Variable | Variable | **À CONFIRMER** phase 5 | Headcount Labs etc. (tiers) |

**Important :** des pages publiques Lever/Ashby mentionnant Casablanca/Morocco prouvent seulement que **certains employeurs** publient des postes MA sur ces ATS — **pas** qu’HireLink est partenaire de ces employeurs ou de Lever/Ashby.

---

## 5. Stratégie spécifique Maroc

### 5.1 Réalité terrain (**FAIT** + **DOC PUBLIQUE**)

- Pas d’API nationale type « France Travail Maroc » dans le dépôt.  
- Les job boards locaux majeurs n’offrent pas, dans nos preuves actuelles, d’API JSON libre documentée ? **interdiction de scrape**.  
- Couverture MA réaliste à court terme = **intersection** :
  - employeurs internationaux / régionaux avec board Greenhouse/Lever/Ashby et localisation Morocco/Casablanca/…  
  - + offres HireLink natives (entreprises inscrites)  
  - + futur **feed partenaire** signé (format `MarocPartnerFeedParser` déjà prêt).

### 5.2 Plan Maroc (sans inventer de partenariats)

| Priorité | Action | Preuve requise avant activation |
|---|---|---|
| M1 | Allowlist boards ATS où des offres `country=MA` / location Morocco apparaissent **publiquement** | URL board + réponse API stubbée en test ; filtre geo côté import |
| M2 | Remplacer le JSON sample par un feed HTTPS **contractuel** (deep `url`/`applyUrl`) | Contrat + échantillon réel |
| M3 | Recenser PME marocaines sur Teamtailor / Welcome to the Jungle / SmartRecruiters (**DOC PUBLIQUE** sectorielle) | Doc API + test board réel |
| M4 | Ne **pas** activer Remote OK comme « solution Maroc » | Couverture inadaptée |

### 5.3 UX Maroc

- Filtre pays `MA` par défaut possible en locale produit (**HYPOTHÈSE** produit).  
- Chip « HireLink » vs « Site carrière (Lever/…) » vs « Partenaire » (uniquement si `partnership_proven`).  
- Sample actuel : libeller clairement **démo** ou le retirer de la vitrine dès Phase 1 (qualité URL).

---

## 6. Plan technique détaillé par phases

### Phase 0 — Préparation et sécurisation

| | |
|---|---|
| **Objectif** | Inventaire figé, filet de sécurité, baseline tests, décisions produit/juridique minimales. |
| **Fichiers concernés (lecture / docs)** | Architecture, audit, `application.properties`, `External*`, migrations, `candidate-home.js` |
| **Modifications envisagées** | **Aucune applicative** — éventuellement mise à jour doc (ce plan). Checklist ops. |
| **Dépendances** | Accès repo ; environnement test `hirelink_test` déjà séparé de la démo |
| **Risques** | Confusion démo 8081 / test ; activation accidentelle Remote OK |
| **Tests de référence à figer** | `RemoteOkParserTest`, `MarocPartnerFeedParserTest`, `ExternalJobAndCvIntegrationTest`, `SkillMatchingServiceTest`, smoke apply interne |
| **Critères d’acceptation** | Liste des tests verts documentée ; flags actuels connus ; backup/export schéma noté ; **aucune sync live** lancée |
| **Rollback** | N/A (pas de changement) |
| **Complexité** | **S** (0,5–1 j) |
| **Gate suivante** | OK produit sur allowlist initiale + politique « refuser homepages » |

**Checklist Phase 0 (ops, sans exécution ici) :**

1. Noter ports démo (8081/5500) intacts.  
2. Confirmer `ddl-auto=validate` prod/démo.  
3. Prochaine migration libre = **V004** (après V003).  
4. Décider User-Agent futur et contact abuse.  
5. Trancher : masquer sample Maroc `GENERIC_HOME` dès Phase 1 ?

---

### Phase 1 — Qualité des données

| | |
|---|---|
| **Objectif** | Empêcher qu’une homepage passe pour une annonce ; distinguer démo / réel ; base dédup + dates. |
| **Fichiers** | `ParsedExternalJob` / modèle ; `ExternalJobImportService` ; `ExternalJobOffer` + **V004** ; nouveau `UrlQualityService` (proposé) ; admin status ; copy `candidate.html` |
| **Modifications** | Champ `url_quality` ; rejet ou masquage `GENERIC_HOME`/`INVALID` en vitrine ; `content_hash` ; flag `is_demo_feed` ou source `MAROC_EMPLOI_DEMO` ; ne plus afficher sample sans deep link |
| **Dépendances** | Phase 0 ; migration V004 |
| **Risques** | Vitrine externe vide si seul le sample existe ? **attendu** et honnête |
| **Tests** | Unit URL (/, /jobs, deep path, https only) ; import rejette generic ; liste publique 0 generic ; ATS inchangé |
| **Acceptation** | 0 offre publique avec path générique ; sample retiré ou badge DÉMO ; tests integration verts |
| **Rollback** | Flag `hirelink.external-jobs.url-quality.enforce=false` ; down migration colonnes nullable |
| **Complexité** | **M** (2–3 j) |
| **Gate** | Vitrine peut être vide ; OK métier |

---

### Phase 2 — Premiers connecteurs réels (Greenhouse, Lever, Ashby)

| | |
|---|---|
| **Objectif** | Offres **réelles** via APIs documentées, allowlist courte (ex. 5–15 boards), deep links. |
| **Fichiers** | SPI connecteur ; 3 clients/parsers ; `ExternalJobSyncFacade` ; table/registry `external_job_boards` ; admin CRUD/sync-one ; properties flags off par défaut |
| **Modifications** | Sync par board ; `external_id = boardKey + ":" + jobId` ; mapping origin/apply ; timeouts ; budget HTTP ; **aucun POST apply** |
| **Dépendances** | Phase 1 ; allowlist validée ; WireMock fixtures |
| **Risques** | ToS multi-board ; rate limit ; payload Ashby volumineux |
| **Tests** | Parsers fixtures ; WireMock 200/404/500 ? keep previous ; disable board ? hide ; pas d’écriture `job_offers` |
| **Acceptation** | ?1 offre réelle affichée avec URL profonde vérifiable ; attribution provider visible ; flags default **false** sur démo jusqu’à activation manuelle |
| **Rollback** | Flags `greenhouse/lever/ashby.enabled=false` ; boards `enabled=0` |
| **Complexité** | **L** (4–6 j) |
| **Gate** | Juridique light OK (**À CONFIRMER**) ; canary 1 board |

**Allowlist initiale (exemples publics, non partenaires) — À CONFIRMER avant insert :**

- Boards Lever où des localisations Morocco/Casablanca apparaissent publiquement (ex. pages jobs.lever.co filtrables).  
- Boards Ashby/Greenhouse d’employeurs recrutant en MA **si** le token est vérifié manuellement.  
- Documenter pour chaque ligne : URL careers, date de vérif, nombre d’offres MA observées.

---

### Phase 3 — Couverture marocaine

| | |
|---|---|
| **Objectif** | Maximiser les offres **réellement** localisées MA sans scrape ni faux partenariats. |
| **Fichiers** | Filtres import `country=MA` / location fold ; allowlist MA-first ; contrat feed `MarocPartnerFeedParser` ; retrait sample |
| **Modifications** | Priorité sync boards MA ; UI défaut pays MA ; pipeline commercial pour partenaires (hors code) |
| **Dépendances** | Phase 2 ; éventuel contrat partenaire (**À CONFIRMER**) |
| **Risques** | Volume MA faible via seuls ATS internationaux ; pression pour scraper |
| **Tests** | Filtre MA ; rejet location vide si policy stricte ; feed partenaire deep links only |
| **Acceptation** | Liste MA non vide **ou** message honnête « peu d’annonces externes MA » + offres HireLink mises en avant ; zéro homepage |
| **Rollback** | Désactiver boards MA ; revenir sample **uniquement** en mode démo flaggé |
| **Complexité** | **M–L** (3–5 j + délai commercial) |
| **Gate** | Couverture acceptable produit |

---

### Phase 4 — Recherche et interface

| | |
|---|---|
| **Objectif** | Catalogue professionnel : filtres, pagination, provenance, navigation exacte. |
| **Fichiers** | `ExternalJobController`, repository/query, `candidate-home.js`, `landing-offers.js`, `offer-card.js`, CSS, `admin.js` |
| **Modifications** | Query params `q,country,city,workplace,contract,company,source,sinceDays,page,size` ; dual CTA ; facets optionnelles ; ne plus charger tout le catalogue en JS |
| **Dépendances** | Phases 1–2 (données propres) |
| **Risques** | Régression filtres existants ; perf |
| **Tests** | API pagination ; filtres combinés ; Playwright lecture CTA ; apply interne intact |
| **Acceptation** | pageSize respecté ; filtre pays/source OK ; bouton ouvre deep link ; copy sans « partenaire officiel » abusif |
| **Rollback** | Feature flag UI ; endpoint legacy sans params |
| **Complexité** | **M** (2–4 j) |
| **Gate** | UX validée |

---

### Phase 5 — Robustesse et extension internationale

| | |
|---|---|
| **Objectif** | Sync fiable, supervision, quotas, nouveaux pays / providers (Remotive, Remote OK, Adzuna licencié, autres ATS). |
| **Fichiers** | Cron budgets ; `external_sync_runs` enrichi ; métriques admin ; connecteurs additionnels |
| **Modifications** | Jitter, circuit breaker soft, canary nightly hors CI, soft-dedupe cross-source |
| **Dépendances** | Licence Adzuna si retenue ; ToS Remotive/Remote OK acceptés |
| **Risques** | Coût licence ; SEO constraints Remotive |
| **Tests** | Charge 10k rows fictives ; chaos 502 ; soft dedupe |
| **Acceptation** | Sync N boards < budget temps ; panne 1 provider ? panne app ; dashboards fraîcheur |
| **Rollback** | Disable providers un par un |
| **Complexité** | **L** (ongoing) |

---

## 7. Impacts base de données

### Migration proposée `V004__external_jobs_global.sql` (**non exécutée**)

**Ajouts `external_job_offers` :**  
`country_code`, `city`, `workplace_type`, `board_key`, `content_hash`, `url_quality`, `origin_host`, éventuellement `is_demo` ; élargir `external_id` (ex. 160).

**Nouvelle table `external_job_boards` :**  
provider, board_key, company_name, home_url, region, enabled, last_success_at, last_error, notes_legales.

**Index :** `(available, country_code, published_at)`, `(available, source, published_at)`, `(board_key, available)`.

### Risques migration

| Risque | Mitigation |
|---|---|
| `ddl-auto=validate` casse si JAR sans migration appliquée | Appliquer V004 **avant** déploiement JAR |
| Longueur `external_id` | Migration alter + re-sync |
| Données sample | Backfill `url_quality=GENERIC_HOME` |

### Rollback DB

1. Flags applicatifs off.  
2. `available=0` sur sources nouvelles.  
3. Down migration : drop colonnes/tables **seulement** si vide / acceptable perte externe (ATS intact).  
4. Ne jamais toucher `job_offers` / `applications`.

---

## 8. Matrice des risques

| ID | Risque | Prob. | Impact | Mitigation | Owner |
|---|---|---|---|---|---|
| R1 | Contestation ToS multi-boards | M | H | Allowlist courte ; pas de claim partenariat ; counsel | Produit/Juridique |
| R2 | Tentation scrape Maroc | M | H | Interdit dans plan + revues PR | Tech |
| R3 | Vitrine vide après Phase 1 | H | M | Message UX + prioriser Phase 2 vite | Produit |
| R4 | Rate limit / ban IP | M | M | Budget, délai, cache | Tech |
| R5 | Régression ATS | F | H | Tests non-régression Phase 0 | Tech |
| R6 | Confusion démo / prod data | M | M | `is_demo` ; flags | Tech |
| R7 | Adzuna sans licence | M | H | Ne pas brancher | Produit |
| R8 | Liens morts | M | M | url_quality + recheck échantillon | Tech |
| R9 | Perf catalogue | M | M | Pagination + indexes Phase 4 | Tech |
| R10 | Doc obsolète | H | L | Remplacer `sources-offres-externes.md` après go | Tech |

---

## 9. Stratégie de tests (scénarios obligatoires)

| # | Scénario | Type | Preuve attendue |
|---|---|---|---|
| T1 | Annonce externe ? page exacte | Integration + FE | `originUrl`/`applyUrl` path contient id/slug ; HTTP fixture |
| T2 | Homepage ? annonce valide | Unit UrlQuality | `/`, `/jobs` ? GENERIC_HOME exclus vitrine |
| T3 | Annonce absente du feed | Integration | Après full sync + 7 j (ou accéléré en test) ? `available=false` |
| T4 | Même offre multi-sources | Unit/Integration soft-dedupe | Pas de doublon agressif auto ; signal ou collapse documenté |
| T5 | API 502/timeout | Integration WireMock | App UP ; `keptPrevious` ; sync report erreur |
| T6 | Filtres + pagination volume | Integration + perf léger | `total` cohérent ; pageSize respecté sur N?1000 fixtures |
| T7 | Candidature interne OK | Integration existante | Apply + pipeline |
| T8 | Externe ? faux dossier | Integration | `ext-*` / id invalide ? pas d’`Application` |
| T9 | Démo intacte | Smoke manuel checklist | Comptes seed, offres internes, billing mock |
| T10 | Attribution visible | FE | Chip source + lien home provider |

CI : **stubs only** — pas d’appels réels Greenhouse/Lever/Ashby dans la pipeline.

---

## 10. Estimations de complexité

| Phase | Effort indicatif | Complexité |
|---|---|---|
| 0 Préparation | 0,5–1 j | S |
| 1 Qualité données | 2–3 j | M |
| 2 Connecteurs G/L/A | 4–6 j | L |
| 3 Maroc | 3–5 j (+ délai contrats) | M–L |
| 4 UI / recherche | 2–4 j | M |
| 5 Robustesse / extension | 5–10 j spread | L |
| **Total technique cœur (0–4)** | **~12–19 j** | — |

Hors : counsel, négociation partenaires Maroc, licence Adzuna.

---

## 11. Décisions nécessitant ton autorisation

Avant toute implémentation, merci de trancher :

1. **Autoriser le démarrage Phase 0–1 seulement**, ou un package plus large ?  
2. **Politique homepages :** masquer immédiatement le sample Maroc (vitrine externe peut devenir vide) ?  
3. **Allowlist initiale :** combien de boards ? qui les valide ?  
4. **Revue juridique** multi-boards Greenhouse/Lever/Ashby : go / wait ?  
5. **Remote OK / Remotive** : inclus dès Phase 5 ou jamais en prod démo ?  
6. **Adzuna** : engager une demande de licence ?  
7. **Défaut UX pays** : MA d’abord ?  
8. **Libellés** : interdiction stricte de « partenaire officiel » sauf champ prouvé ?

---

## 12. Première phase recommandée et justification

**Recommandation : enchaîner Phase 0 puis Phase 1 uniquement, puis stop & review.**

**Pourquoi :**

- Corrige le plus gros mensonge UX actuel (homepages présentées comme annonces) **sans** risque réseau tiers.  
- Pose la migration et les garde-fous avant d’attirer du volume.  
- Évite d’investir dans des connecteurs tant que la vitrine ne sait pas distinguer démo / réel / lien profond.  
- Préserve totalement l’ATS et la démo interne.

**Ensuite seulement :** Phase 2 (1–3 boards canary Lever/Ashby/Greenhouse), puis Phase 3 Maroc.

---

## 13. Protection de l’existant (checklist non-négociable)

| Élément | Mesure planifiée |
|---|---|
| `job_offers` / `applications` | Aucun connecteur n’écrit dedans |
| Candidature interne | Tests T7 à chaque phase |
| Matching | Inchangé ; externe reste skills-only indicatif |
| Rôles / pipeline / notifs / billing / IA | Hors scope connecteurs |
| Seed démo | Ne pas purge ; flags externes séparés |
| Secrets | Pas de nouvelles clés sauf Adzuna futur en env |
| Docker / 8081 / 5500 | Aucun redémarrage dans ce mode plan |

---

## 14. Faits / hypothèses / à confirmer (synthèse)

| Type | Points |
|---|---|
| **FAIT** | Socle externe existant ; sample Maroc homepage ; Remote OK off ; pas de pagination ; ATS séparé |
| **DOC PUBLIQUE** | APIs G/L/A lisibles sans clé par board ; ToS Adzuna/Remotive/Remote OK |
| **HYPOTHÈSE** | Allowlist de boards suffira à amorcer une vitrine utile MA+international |
| **À CONFIRMER** | Position juridique agrégation multi-boards ; partenaire feed Maroc ; volume MA réel ; go Phase 1 |

---

## 15. Conclusion

Le chemin sûr vers un catalogue mondial **crédible** pour HireLink n’est pas un scrape massif ni un faux partenariat boards locaux : c’est **qualité des liens ? connecteurs ATS publics allowlistés ? Maroc ciblé ? UI paginée ? extensions licenciées**.

**Aucune implémentation n’a été démarrée.**  
**En attente de ton autorisation explicite** pour ouvrir la Phase 0/1 (ou une autre séquence).
