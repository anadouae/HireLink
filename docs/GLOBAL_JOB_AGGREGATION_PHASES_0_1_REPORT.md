# HireLink — Rapport Phases 0 et 1 (agrégation mondiale)

**Date :** 2026-11-13  
**Autorisation :** phases 0 et 1 uniquement — **aucun** connecteur Greenhouse / Lever / Ashby  
**Bases :** tests sur `hirelink_test` (Maven create-drop) + schéma isolé `hirelink_ext_phase01` (V002+V004)  
**Base principale `hirelink` :** non modifiée (106 offres externes inchangées, colonnes Phase 1 absentes)

---

## 1. Modifications réalisées et fichiers concernés

### Phase 0 — Préparation (constats)

| Élément | État |
|---|---|
| Plan `docs/GLOBAL_JOB_AGGREGATION_IMPLEMENTATION_PLAN.md` | Aligné avec le code (`ExternalJob*`, catalogue 2 sources, sample Maroc homepages) |
| Composants agrégation | `ExternalJobOffer`, `ExternalJobImportService`, `ExternalJobSyncFacade`, `ExternalSourceCatalog`, parsers Maroc/Remote OK, controllers public/admin, FE candidat/landing/offer-card |
| Git | branche `main`, working tree déjà dirty (travaux antérieurs) — **aucun commit / push** |
| Démo | port **8081** répond (health 401 = process UP + sécurité), **5500** HTTP 200 — **non redémarrés** |
| Isolation | `hirelink_test` pour Maven ; DB `hirelink_ext_phase01` créée pour V004 ; **pas d’écriture sur `hirelink`** |
| Rollback soft | `hirelink.external-jobs.url-quality.enforce=false` |

### Phase 1 — Qualité / fiabilité (code)

| Fichier | Rôle |
|---|---|
| `UrlQuality.java` | États `DEEP` / `UNKNOWN` / `GENERIC_HOME` / `INVALID` |
| `UrlQualityService.java` | Heuristiques locales + `classifyAfterRedirect` (sans scraping) |
| `ExternalJobOffer.java` | `urlQuality`, `urlQualityReason`, `quarantined`, `quarantineReason`, `contentHash` |
| `ExternalJobImportService.java` | Classification à l’import ; quarantaine ; **ne copie plus** `originUrl` → `applyUrl` |
| `RemoteOkParser.java` | `apply_url` absent → `null` (pas d’invention) |
| `ExternalJobController.java` | Expose `urlQuality`, `linkReliable`, `directAccessTrusted` ; filtre quarantaine |
| `ExternalJobAdminController.java` | Compteurs `quarantinedCount` + brief qualité |
| `database/migrations/V004__external_offer_url_quality.sql` | Migration idempotente (**appliquée seulement** sur `hirelink_ext_phase01`) |
| `application.properties` (+ test) | `url-quality.enforce`, `max-age-days=180` |
| `frontend/js/offer-card.js` | `externalLinkPolicy()` — CTA direct seulement si `DEEP` |
| `frontend/js/candidate-home.js` | Boutons prudents / message si lien non fiable |
| `frontend/js/landing-offers.js` | Pied de carte aligné |
| Tests | `UrlQualityServiceTest`, `ExternalUrlQualityIntegrationTest` ; ajustements Remote OK / ExternalJob IT |

---

## 2. Règles exactes de validation des URLs

**Principe :** une URL `https` syntaxiquement correcte **ne prouve pas** que l’annonce existe encore. Aucun téléchargement HTML / scraping. Les contrôles HTTP (404, redirection) ne sont utilisés que via `classifyAfterRedirect` (tests / future sonde admin), pas à l’import courant.

### Classification `originUrl`

| Qualité | Conditions (résumé) | Vitrine | CTA « accès direct » |
|---|---|---|---|
| **DEEP** | `https` + chemin avec identifiant (`\d{2,}`, UUID) **ou** segments type `/jobs/…`, `remote-jobs`, `/apply/…`, `/posting/…` | Oui | Oui |
| **UNKNOWN** | `https` multi-segments / slug ambigu sans id clair | Oui (prudent) | Non — lien optionnel « non vérifié » |
| **GENERIC_HOME** | `/`, `/jobs`, `/careers`, `/emploi`, index carrières sans id | Non (quarantaine) | Non |
| **INVALID** | vide, non-`https`, espaces, `localhost`, illisible, trop longue | Non (quarantaine) | Non |

### `applyUrl`

- Conservé **séparément** ; jamais fabriqué depuis `originUrl`.
- Absent → `null` (UNKNOWN côté apply, sans invention).
- Si fourni mais `INVALID` → stocké comme `null`.

### Quarantaine (conserve la ligne)

Si `enforce=true` et :

1. qualité origine ∈ {`GENERIC_HOME`, `INVALID`}, **ou**
2. `publishedAt` &lt; maintenant − `max-age-days` (défaut **180**) → raison `EXPIRED` (présomption, pas preuve HTTP),

alors : `quarantined=true`, `available=false`, `quarantine_reason` renseigné. **Aucune suppression.**

### Redirection / inaccessible (API de classification)

- HTTP ≥ 400 ou status ≤ 0 → `INVALID` (« URL inaccessible »).
- Cible finale homepage / invalide → `GENERIC_HOME`.

---

## 3. Résultats des tests

Commande (isolée, port aléatoire, base `hirelink_test`) :

```text
mvn -Dtest=UrlQualityServiceTest,RemoteOkParserTest,MarocPartnerFeedParserTest,ExternalJobAndCvIntegrationTest,ExternalUrlQualityIntegrationTest,SkillMatchingServiceTest test
```

| Suite | Tests | Résultat |
|---|---|---|
| `UrlQualityServiceTest` | 8 | OK |
| `RemoteOkParserTest` | 5 | OK |
| `MarocPartnerFeedParserTest` | 3 | OK |
| `ExternalJobAndCvIntegrationTest` | 4 | OK |
| `ExternalUrlQualityIntegrationTest` | 5 | OK |
| `SkillMatchingServiceTest` | 12 | OK |
| **Total ciblé** | **37** | **0 failure** |

Scénarios couverts : URL deep valide, homepage, malformée / non-https, inaccessible (redirect helper), redirection → homepage, offre expirée, externe sans `applyUrl`, sample Maroc homepages, non-régression offre interne + candidature, refus candidature `ext-*`, matching skills.

---

## 4. Comptages environnement isolé

### A. Maven / `hirelink_test` (create-drop, source de vérité Phase 1)

| Scénario | Valides (available, non quarantaine) | Suspectes / quarantaine | Supprimées |
|---|---|---|---|
| Deep Lever-like sans apply | **1** | 0 | 0 |
| Homepage + http + expired | 0 | **3** | 0 |
| Sample Maroc (8 jobs homepages) | **0** | **8** | 0 |
| Import Remote OK deep (`/remote-jobs/9`) | listables DEEP | — | 0 |

Interprétation produit : avec le seul feed sample actuel, **la vitrine externe tombe à 0 offre honnête** après enforce — conforme au plan Phase 1 (« vitrine peut être vide »).

### B. `hirelink_ext_phase01`

- Schéma V002 + **V004** appliqué.
- Aucune synchro internationale réelle exécutée (pas d’appel Greenhouse/Lever/Ashby/Remote OK live).

### C. Base principale `hirelink` (contrôle)

| Contrôle | Valeur |
|---|---|
| `COUNT(external_job_offers)` | **106** (inchangé) |
| Colonne `url_quality` | **absente** |
| Colonne `quarantined` | **absente** |

---

## 5. État de la démo et intégrité

| Service / donnée | État |
|---|---|
| Backend démo `:8081` | Process toujours UP (health protégé → 401) — **non redémarré** |
| Frontend `:5500` | HTTP **200** — **non redémarré** |
| Données `hirelink` | Intactes ; **pas** de migration V004 |
| Secrets | Non modifiés |
| Sync internationale | Non activée (`remoteok.enabled=false` ; pas de connecteurs ATS) |

**Attention ops :** le code source contient désormais les champs JPA Phase 1. Un **redémarrage** du JAR démo avec `ddl-auto=update` ajouterait les colonnes sur `hirelink`. Ne redémarrer **qu’après** autorisation + application contrôlée de V004 (ou flag enforce).

---

## 6. Limites et risques restants

1. **Heuristique ≠ vérité terrain** — un slug DEEP peut pointer vers une offre retirée ; UNKNOWN peut être une vraie annonce.
2. **Pas de sonde HTTP en production** à l’import (volontaire) — pas de faux positifs réseau, mais pas de détection 404 live.
3. **Sample Maroc** : après enforce, **0 offre** en vitrine jusqu’à Phase 2 (deep links réels).
4. **Redémarrage démo** sans V004 contrôlée = risque de drift schéma via Hibernate `update`.
5. **Messages sync** ASCII-ifiés dans quelques fichiers (encodage Windows) — fonctionnel, polish UTF-8 possible plus tard.
6. Absence longue (7 j) et quarantaine coexistent : une ligne peut être unavailable pour plusieurs raisons.

---

## 7. Préparation recommandée pour la Phase 2

**Ne pas démarrer sans autorisation explicite.**

1. Valider juridiquement une **allowlist courte** (5–15 boards) Greenhouse / Lever / Ashby publics.
2. Appliquer **V004 sur `hirelink`** uniquement après OK + fenêtre de maintenance ; ou démarrer un nouveau processus sur autre port / autre schéma.
3. Introduire SPI `ExternalJobConnector` + fixtures WireMock (aucun appel live en CI).
4. Flags `greenhouse/lever/ashby.enabled=false` par défaut sur la démo.
5. Remplacer ou retirer le sample homepage ; exiger deep `originUrl` / `applyUrl` dans le contrat feed Maroc.
6. Pagination / filtres pays (MA) — Phase 3 du plan, après premiers boards.

---

## Gate

Phases **0 et 1 terminées**.  
**Stop** — attendre autorisation explicite avant toute connexion Greenhouse, Lever ou Ashby.