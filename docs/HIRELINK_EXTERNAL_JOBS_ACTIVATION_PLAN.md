# HireLink — Plan d'activation progressive des offres externes

**Date :** 2026-11-14  
**Prérequis :** `HIRELINK_EXTERNAL_SOURCES_COMPLIANCE_MATRIX.md` · bascule Phase 3 SUCCESS  
**Règle :** aucune activation sur la démo `hirelink` / `8081` sans autorisation explicite séparée.

---

## 0. État actuel (ne pas activer)

| Élément | État |
|---|---|
| Catalogue | 107 listings **INTERNAL** |
| Externes en base | 106 (8 MAROC_EMPLOI available ; 98 REMOTE_OK hidden) |
| Greenhouse / Lever / Ashby / Jooble | **enabled=false** |
| Boards allowlist | tous `enabled=false` |
| Runtime cutover | `startup-sync=false` sur le JAR démo |

---

## 1. Principes d'activation

1. Une source à la fois.  
2. Backup SQL + smoke catalogue avant/après.  
3. Volume canary borné (1–2 boards).  
4. Attribution + liens `originUrl`/`applyUrl` obligatoires.  
5. Pas de candidature HireLink pour une offre externe.  
6. Monitoring : erreurs HTTP, 429, volume importé, quarantaine.  
7. Kill-switch immédiat : flag `enabled=false` + reindex si besoin.

---

## 2. Vague 0 — Hygiène démo (pas d'import)

**Statut :** **FAIT** — hygiène disque + **runtime 8081 appliqué** (`HIRELINK_VAGUE0_HYGIENE_REPORT.md`, `HIRELINK_VAGUE0_RUNTIME_RESTART_REPORT.md`).  
**Reste optionnel :** relance `5500` (UI) si autorisée séparément.

**Objectif :** clarté produit sans risque juridique.

| Action | Détail | Autorisation |
|---|---|---|
| Documenter aux utilisateurs | Catalogue actuel = offres HireLink ; externes historiques masqués/exclus | Non bloquant |
| Identifier orphelins de test | Offres `Bascule*`/`Sync*`/`LiveSync*` auditées ; `LiveSync D` = close+published, hors catalogue (cohérent) | **Fait** (pas de mutation métier) |
| Ne pas supprimer | Conserver pour audit | — |
| Aligner `startup-sync` | Properties fichier = true ; runtime cutover = false — documenter pour le prochain redémarrage | Votre OK avant changement config permanente |

---

## 3. Vague 1 — Canary ATS international (après OK juridique)

**Sources :** Greenhouse **ou** Lever **ou** Ashby (un seul provider d'abord).

### Conditions d'entrée

- [ ] Note juridique écrite (ou acceptation risque documentée) pour agrégation multi-boards.  
- [ ] Backup `hirelink`.  
- [ ] Fenêtre de maintenance courte optionnelle.

### Étapes techniques

1. Choisir **1 board** allowlist (ex. `leverdemo` pour smoke, puis un employeur réel).  
2. Passer ce board `enabled=true` **uniquement** dans DB `external_job_boards` ou fichier allowlist versionné.  
3. Activer **un** flag : `hirelink.external-jobs.greenhouse|lever|ashby.enabled=true`.  
4. Garder Jooble OFF.  
5. Lancer sync admin **manuelle** (pas de cron agressif).  
6. Vérifier : import count, `url_quality`, liens, attribution, exclusion quarantaine.  
7. `POST /catalog/reindex` ou s'appuyer sur `syncExternal`.  
8. Smoke FE : filtre source, redirection externe, pas d'apply interne.  
9. Si OK 24–48 h → ajouter 1–2 boards max.

### Critères de sortie

- 0 erreur 5xx liée à l'import.  
- Pas de 429 répétés.  
- Liens cliquables vérifiés (échantillon ≥ 10).  
- Volume catalogue cohérent (pas de duplication de pages).

### Rollback

`enabled=false` provider + boards ; hide admin des nouvelles lignes si besoin ; reindex.

---

## 4. Vague 2 — Jooble Maroc (priorité produit)

### Conditions d'entrée

- [ ] Clé API obtenue sur **https://ma.jooble.org/api/about**.  
- [ ] Validation ToS API / autorisation écrite si exigée.  
- [ ] Clé stockée hors git (`application-local.properties` / env), jamais commitée.

### Étapes

1. `hirelink.external-jobs.jooble.api-key=***` (local only).  
2. `jooble.enabled=true` + `max-pages=1` + keywords ciblés.  
3. Sync manuelle isolée d'abord (schema test), puis démo.  
4. Vérifier quota 100 req/j ; backoff 429.  
5. Attribution Jooble + `link` comme apply.  
6. Dates = `updated` uniquement si parseable ; sinon « Non précisé ».

### Rollback

`jooble.enabled=false` ; clé retirée ; hide des imports Jooble.

---

## 5. Vague 3 — Extension Maroc réelle

| Option | Condition |
|---|---|
| Partenariat Rekrute / Emploi.ma / ANAPEC | Contrat + feed officiel |
| Feed partenaire Ashby | Opt-in employeurs |
| Agrégateur commercial (JobDataFeeds, etc.) | Contrat + conformité RGPD |

**Sans contrat : ne pas scraper.**

---

## 6. Contrôles avant / après chaque vague

### Avant

- Compteurs : `job_offers`, `external_job_offers`, `applications`, `catalog_listings`.  
- Health `8081` / `5500`.  
- Backup SHA-256.

### Après

- Delta externes / catalogue.  
- Échantillon liens.  
- Login candidat + Pour toi + publish recruteur.  
- Pipeline / billing smoke.  
- Flags connecteurs encore conformes au plan.

---

## 7. Ce qui est activable « immédiatement » au sens technique

| Action | Immédiat ? |
|---|---|
| Continuer démo catalogue **internes** | Oui (déjà live) |
| Activer Jooble sans clé | **Non** |
| Activer ATS sans OK juridique | **Non recommandé** |
| Import fixture MAROC_EMPLOI au catalogue public | **Non** (exclu volontairement) |

---

## 8. Demande d'autorisation (prochaine étape)

Merci d'indiquer explicitement laquelle des options suivantes vous autorisez :

1. **Aucune activation** — conserver l'état actuel (recommandé tant que Jooble/ToS ouverts).  
2. **Vague 1 canary** — un board ATS nommé + provider.  
3. **Vague 2** — Jooble MA avec clé fournie hors chat.  
4. **Hygiène** — dépublier/resync `LiveSync D` et aligner config `startup-sync` (sans import).
