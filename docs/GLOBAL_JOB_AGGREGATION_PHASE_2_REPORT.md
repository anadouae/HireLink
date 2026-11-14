# HireLink — Rapport Phase 2 (connecteurs ATS internationaux)

**Date :** 2026-11-13  
**Autorisation :** Phase 2 uniquement — environnement isole  
**Schema isole :** `hirelink_ext_phase02`  
**Base `hirelink` :** non modifiee (106 offres externes, pas de `board_key` / `url_quality`)  
**Demo 8081 / 5500 :** non redemarrees, intactes  
**Commit / push / deploiement :** non effectues

---

## Resume fonctionnel (non technique)

HireLink peut maintenant parler a trois systemes de recrutement publics (Greenhouse, Lever, Ashby) pour **lire** des annonces publiees par des entreprises, sans que ces entreprises aient un compte HireLink.

En pratique :

1. On configure une liste courte d'entreprises autorisees (allowlist).
2. HireLink recupere leurs annonces via les APIs documentees.
3. Chaque annonce garde son titre, entreprise, lieu, description et liens d'origine.
4. Le candidat voit la provenance et ouvre la page exacte chez l'emetteur pour postuler.
5. Aucun dossier de candidature interne n'est cree automatiquement.

**Important :** les connecteurs sont **desactives par defaut**. Aucune synchronisation ATS ne part au demarrage de la demo. L'agregation multi-boards n'est pas un partenariat officiel : elle reste sous reserve juridique.

---

## Statuts (exigence de transparence)

| Element | Statut |
|---|---|
| SPI `ExternalJobConnector` + 3 connecteurs | **IMPLEMENTED** / **TESTED** |
| Allowlist boards (JSON) | **IMPLEMENTED** (tous `enabled=false` hors tests) |
| Parsing fixtures Greenhouse / Lever / Ashby | **TESTED** |
| Client HTTP + SSRF / redirect / 429 / timeout | **TESTED** |
| Import + quarantaine Phase 1 + API publique | **TESTED** (schema isole) |
| Non-regression candidatures internes | **TESTED** |
| Endpoints publics reels (GET) | **LIVE VERIFIED** (lecture seule) |
| Import live Lever `leverdemo` → `hirelink_ext_phase02` | **LIVE VERIFIED** (5 offres) |
| Activation sur demo / `hirelink` | **NOT IMPLEMENTED** (interdit) |
| Clarification ToS multi-board Lever (#86 ouverte) | **BLOCKED** (juridique) — flags off |
| Phase 3 (couverture Maroc / pagination) | **NOT IMPLEMENTED** |

---

## 1. Fichiers crees ou modifies

### Crees

| Fichier |
|---|
| `backend/.../service/external/ExternalJobConnector.java` |
| `backend/.../service/external/ExternalBoardDefinition.java` |
| `backend/.../service/external/ExternalBoardRegistry.java` |
| `backend/.../service/external/AtsHttpClient.java` |
| `backend/.../service/external/AbstractAtsJobConnector.java` |
| `backend/.../service/external/GreenhouseJobConnector.java` |
| `backend/.../service/external/LeverJobConnector.java` |
| `backend/.../service/external/AshbyJobConnector.java` |
| `backend/.../service/external/GreenhouseJobParser.java` |
| `backend/.../service/external/LeverJobParser.java` |
| `backend/.../service/external/AshbyJobParser.java` |
| `backend/.../service/external/ExternalHtmlText.java` |
| `backend/.../model/ExternalJobBoard.java` |
| `backend/.../repository/ExternalJobBoardRepository.java` |
| `backend/src/main/resources/feeds/ats-boards-allowlist.v1.json` |
| `database/migrations/V005__external_job_boards_ats.sql` |
| `backend/src/test/resources/feeds/ats-boards-allowlist-test.v1.json` |
| `backend/src/test/resources/fixtures/ats/*.json` |
| Tests : `GreenhouseJobParserTest`, `LeverJobParserTest`, `AshbyJobParserTest`, `AtsHttpClientTest`, `AtsConnectorFetchTest`, `AtsConnectorsPhase2IsolationTest`, `Phase2LiveAtsImportSmokeTest` |
| `docs/GLOBAL_JOB_AGGREGATION_PHASE_2_REPORT.md` (ce fichier) |

### Modifies

| Fichier | Nature |
|---|---|
| `ParsedExternalJob.java` | `boardKey`, `countryCode` (+ constructeur legacy) |
| `ExternalJobOffer.java` | `external_id` 160, `boardKey`, `countryCode` |
| `ExternalJobImportService.java` | persistance board/pays |
| `ExternalJobSyncFacade.java` | delegation SPI + `syncBoard` |
| `ExternalSourceCatalog.java` | sources GREENHOUSE / LEVER / ASHBY |
| `ExternalJobController.java` | expose `boardKey`, `countryCode` |
| `ExternalJobAdminController.java` | boards + sync board |
| `application.properties` (+ test) | flags ATS `false` + reglages HTTP |

---

## 2. Architecture implementee

```
Allowlist JSON (enabled=false)
        |
        v
ExternalBoardRegistry
        |
        +-- GreenhouseJobConnector -- AtsHttpClient --> boards-api.greenhouse.io
        +-- LeverJobConnector      -- AtsHttpClient --> api.lever.co
        +-- AshbyJobConnector      -- AtsHttpClient --> api.ashbyhq.com
        |
        v
   ParsedExternalJob  -->  ExternalJobImportService (Phase 1 UrlQuality)
        |
        v
   external_job_offers (schema isole / futur)
        |
        v
   GET /api/v1/external-offers  (CTA hors plateforme)
```

- Pas de nouveau `if/else` metier dans le facade pour chaque ATS : map de `ExternalJobConnector`.
- Remote OK / Maroc conserves en chemins legacy.
- Aucun POST apply vers les ATS.
- Redirections HTTP **non suivies** ; hotes allowlistes ; throttle ; taille max.

---

## 3. Sources et etat

| Source | Flag defaut | Docs utilisees | Etat |
|---|---|---|---|
| Greenhouse Job Board API | `greenhouse.enabled=false` | https://docs.greenhouse.io/job-board.html | Pret, off |
| Lever Postings API | `lever.enabled=false` | https://github.com/lever/postings-api | Pret, off — **ToS multi-board non tranche** (issue #86 ouverte) |
| Ashby Job Postings API | `ashby.enabled=false` | https://developers.ashbyhq.com/docs/public-job-posting-api | Pret, off — API orientee « votre organisation » |
| Remote OK / Maroc | inchanges | — | Inchanges |

---

## 4. Boards allowlistes (seed)

Tous `enabled=false` en configuration principale :

| Provider | boardKey | Reference | Notes de verification |
|---|---|---|---|
| GREENHOUSE | `gitlab` | https://boards.greenhouse.io/gitlab | **LIVE VERIFIED** 200 (liste jobs) |
| GREENHOUSE | `stripe` | https://boards.greenhouse.io/stripe | Documente publiquement ; volume eleve — canary seulement |
| LEVER | `leverdemo` | https://jobs.lever.co/leverdemo | **LIVE VERIFIED** + import isole |
| LEVER | `yassir` | https://jobs.lever.co/yassir | URL publique citee au plan ; non activee |
| ASHBY | `Ashby` | https://jobs.ashbyhq.com/Ashby | **LIVE VERIFIED** 200 (payload volumineux) |

Ce ne sont **pas** des partenariats HireLink.

---

## 5. Exemples d'annonces reelles (live, schema isole)

Import smoke Lever `leverdemo` → `hirelink_ext_phase02` :

| Titre | Qualite | Listable | originUrl (exacte) |
|---|---|---|---|
| Customer Success Manager | DEEP | oui | https://jobs.lever.co/leverdemo/ad208490-4052-4f91-a57e-433f2d1e484b |
| Dentist | DEEP | oui | https://jobs.lever.co/leverdemo/120b5976-ee4e-4dc5-b31b-2b1ca124460c |
| Sales Engineer AH | DEEP | oui | https://jobs.lever.co/leverdemo/58db3f8e-b108-47d1-9e6e-87a1712497cd |
| Director of Diversity... | DEEP | non (quarantaine EXPIRED >180j) | https://jobs.lever.co/leverdemo/1dbf02a6-d434-4e47-8d66-3aba2c17bed1 |
| Sales Engineer | DEEP | non (quarantaine EXPIRED) | https://jobs.lever.co/leverdemo/7e10ea46-98be-4bd1-a3ca-57d153b25c61 |

Compteurs `hirelink_ext_phase02` apres smoke : **total 5**, **listables 3**, **quarantaine 2**.

---

## 6. Tests simules vs appels reels

| Type | Contenu | Internet |
|---|---|---|
| Fixtures + HttpServer local | Parsing, fetch connecteur, 404/429/302/timeout, import, hide, candidature interne | Non |
| Maven surefire (campagne locale) | 180 tests, 0 failure / 0 error (dont suites hors phase 2 deja presentes dans target) | Non (sauf smoke) |
| GET live documentes | leverdemo, Ashby, gitlab | Oui (lecture) |
| Import live | leverdemo limit 5 → `hirelink_ext_phase02` uniquement | Oui |

**Ne pas confondre :** les tests CI/automatises passent **sans** Internet. Le smoke live est optionnel (`-Dhirelink.phase2.live=true`).

### Resultats tests Phase 2 (nouveaux)

| Suite | Tests | Failures |
|---|---|---|
| GreenhouseJobParserTest | 3 | 0 |
| LeverJobParserTest | 2 | 0 |
| AshbyJobParserTest | 1 | 0 |
| AtsHttpClientTest | 2 | 0 |
| AtsConnectorFetchTest | 2 | 0 |
| AtsConnectorsPhase2IsolationTest | 5 | 0 |
| Phase2LiveAtsImportSmokeTest | 1 | 0 (avec flag live) |
| **Sous-total Phase 2** | **16** | **0** |

Regression Phase 0/1 relancee dans la meme campagne : ExternalJob*, UrlQuality*, parsers, SkillMatching — OK.

---

## 7. Parcours fonctionnel (isole)

1. Entreprise publie sur Lever demo — **LIVE VERIFIED**.
2. Connecteur / parser recupere JSON — **TESTED** + smoke live.
3. Normalisation + UrlQuality + stockage `hirelink_ext_phase02` — **LIVE VERIFIED**.
4. API expose provenance / boardKey / URLs — **TESTED**.
5. CTA FE Phase 1 (`linkReliable` / DEEP) — conserve (pas de redeploiement demo).
6. Bouton → page exacte `jobs.lever.co/...` — URLs reelles ci-dessus.
7. Candidature interne non creee pour `ext-*` — **TESTED**.

Etape non validee sur la **demo publique 8081/5500** (volontairement non deployee).

---

## 8. Restrictions juridiques / techniques

1. Greenhouse / Ashby documentent l'API surtout pour **le site carriere de l'employeur**.
2. Lever : question usage tiers ouverte ([postings-api#86](https://github.com/lever/postings-api/issues/86)) — **pas d'activation**.
3. Pas de republication pretendue « partenaire officiel ».
4. Attribution + lien sortant obligatoires.
5. Ashby : payloads lourds ; `isListed=false` ignore.
6. Rate limit / politesse : `min-interval-ms`, pas de scrape HTML.

---

## 9. Etat des bases et services

| Cible | Etat |
|---|---|
| `hirelink` | 106 offres ; pas de colonnes Phase 1/2 |
| `hirelink_ext_phase02` | V002+V004+V005 ; 5 offres LEVER live smoke |
| `hirelink_test` | intact pour tests create-drop classiques |
| Demo `:8081` | UP (401 health) — non redemarree |
| Demo `:5500` | 200 — non redemarree |
| Secrets | inchanges |

---

## 10. Risques de migration / rollback

**Risques**

- Redemarrer le JAR demo avec `ddl-auto=update` sur `hirelink` appliquerait des colonnes JPA Phase 1/2 — **a eviter**.
- Activer des flags ATS sans allowlist legale expose un risque ToS.

**Rollback**

1. Garder / remettre `greenhouse/lever/ashby.enabled=false`.
2. Boards seed `enabled=false`.
3. Sur schema isole seulement : `DROP TABLE external_job_boards` ; colonnes `board_key`/`country_code` optionnelles.
4. Ne jamais toucher `job_offers` / `applications`.

---

## 11. Travaux restants / recommandation

1. **Conseil juridique** sur aggregation multi-boards avant toute activation.
2. Canary 1 board (ex. `leverdemo` ou board MA verifie) sur process **non-demo** + schema dedie.
3. Phase 3 : filtre geo MA, retrait sample homepages, pagination catalogue.
4. Sonde HTTP admin optionnelle (domaines allowlistes uniquement).
5. Ne pas deployer sur 8081 tant que V004/V005 non planifiees sur `hirelink`.

**Recommandation :** valider le rapport, trancher le juridique Lever/Ashby/Greenhouse, puis autoriser un canary isole — **pas** la demo actuelle.

---

## Gate

Phase **2 implementee et testee en isolation**.  
**Stop** — pas de phase 3, pas de commit, pas de push, pas de redeploiement demo.