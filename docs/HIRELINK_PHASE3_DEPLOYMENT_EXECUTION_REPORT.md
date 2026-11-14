# HireLink — Rapport d'exécution bascule Phases 1–3

**Date :** 2026-11-14  
**Autorisation :** bascule contrôlée démo locale (message utilisateur)  
**Référence :** `docs/HIRELINK_PHASE3_DEPLOYMENT_READINESS.md`  
**Hors périmètre respecté :** pas de commit/push, Jooble/ATS non activés, pas de rotation de secrets, pas de déploiement public, pas de suppression métier.

---

## Verdict final

# SUCCESS

La démo `hirelink` / `8081` / `5500` intègre le catalogue Phases 1–3. Les données métier sont conservées (les écarts de compteurs correspondent aux **tests de bascule** : offres de preuve + 1 candidature). Les connecteurs externes restent OFF.

---

## 1. Prérequis (avant modification)

| Contrôle | Résultat |
|---|---|
| `8081` avant | LISTEN (pid 40920, `spring-boot:run`) |
| `5500` | LISTEN (pid 31584) — **non redémarré** |
| Compteurs avant | offres 118 · externes 106 · users 126 · apps 220 · orgs 42 · factures 8 · entretiens (proxy) 37 |
| Tables catalogue avant | absentes |
| V006 `run_id` | `VARCHAR(36)` confirmé |
| Flags Jooble/GH/Lever/Ashby | `false` |

### Sauvegarde créée et vérifiée

| Attribut | Valeur |
|---|---|
| Fichier | `docs/deploy-prep/hirelink-cutover-20261114-010509.sql` |
| Octets | 2 043 056 |
| SHA-256 | `3F49BF16D538F4D347326F69227EEE549131EE08DED0DFFD5E26AB8DC509326C` |
| Meta | `hirelink-cutover-20261114-010509.meta.txt` (compteurs uniquement, **sans PII**) |
| Restore test | `hirelink_cutover_restore_verify` — compteurs identiques source/destination |
| Rollback binaire | `docs/deploy-prep/rollback-20261114-010509/` (argfile runtime + zip `target/classes`) |
| JAR déployé | `backend/target/hirelink-1.0.0-SNAPSHOT.jar` (+ copie sous `docs/deploy-prep/`) |

---

## 2. Migrations réellement appliquées sur `hirelink`

Ordre : **V004 → V005 → V006** (exit 0 chacun).

| Migration | Effet vérifié |
|---|---|
| V004 | `url_quality`, `quarantined`, `content_hash`, index quarantaine |
| V005 | `external_job_boards`, `board_key`, `country_code`, `external_id varchar(160)` |
| V006 | `catalog_listings`, `catalog_for_you_run`, **`run_id varchar(36)`** |

Post-migration immédiat : offres 118 · externes 106 · users 126 · apps 220 · orgs 42 · factures 8 inchangés.  
`url_quality=UNKNOWN` pour 106 externes ; `quarantined=0`.

---

## 3. Backend / services

| Étape | Résultat |
|---|---|
| Arrêt ancien `8081` | pid 40920 (+ parent Maven 33136) |
| Démarrage | `java -jar hirelink-1.0.0-SNAPSHOT.jar` + `application-cutover-runtime.properties` |
| Overrides runtime | Jooble/GH/Lever/Ashby **false**, `startup-sync=false`, `rebuild-on-startup=false`, DB `hirelink` |
| Health public | `GET /api/v1/offers/public` → 200 |
| Catalogue avant reindex | total 0 |
| `POST /api/v1/catalog/reindex` (admin) | **indexed=107** |
| Catalogue après | total **107** |
| `5500` | toujours 200 (inchangé) |

---

## 4. Compteurs avant / après

| Entité | Avant | Après tests | Lecture |
|---|---:|---:|---|
| `job_offers` | 118 | **123** | + offres de preuve (publish/close) restées en base |
| `external_job_offers` | 106 | 106 | inchangé |
| `users` | 126 | 126 | inchangé |
| `applications` | 220 | **221** | +1 candidature de test (Youssef / offre 1) |
| `organizations` | 42 | 42 | inchangé |
| `billing_invoices` | 8 | 8 | inchangé |
| `catalog_listings` | 0 | **107** | projection technique (pas une perte métier) |

**Composition catalogue :** 107 `INTERNAL`. Externes absents du catalogue public car `REMOTE_OK` sont `available=0` / `hidden_by_admin=1` (98) et `MAROC_EMPLOI` est exclu (8) — comportement qualité prévu, pas une perte de table `external_job_offers`.

---

## 5. Tests exécutés

### API / métier

| Test | Résultat |
|---|---|
| Login candidat | OK |
| Login recruteur | OK |
| Recherche `q=Java` | OK (13) |
| Filtres MA / ALL | OK (107 / 107) |
| Pagination pages 0/1 sans doublons | OK |
| Pour toi | OK (13, scan 107, pas de HTTP 400) |
| Détail offre catalogue | OK |
| Candidature interne | OK (app id 262, offre 1) |
| Publish → catalogue | OK |
| Update (`published:true`) → catalogue | OK (`LiveSync D` / Agadir) |
| Close → retrait catalogue | OK |
| Pipeline | OK HTTP 200 |
| Candidatures / entretiens (proxy) | OK HTTP 200 |
| Notifications | OK |
| Plans facturation | OK HTTP 200 |
| Boards ATS enabled | **0** |

### Navigateur (Edge, formulaire réel, sans injection de session)

Preuves : `docs/deploy-prep/cutover-screenshots/` + `recette.json`.

| Parcours | Résultat |
|---|---|
| Landing `5500` | OK |
| Login candidat formulaire | OK → Sara El Amrani |
| Catalogue + `#f-country` | OK |
| Pour toi UI | OK |
| Login recruteur formulaire | OK → Salma Berrada |
| Mes offres | OK |
| Pipeline UI | OK |

### Incident corrigé pendant la bascule

Lors d'une préparation antérieure, un nettoyage CSP avait **tronqué** `connect-src` sur `auth.html` / `index.html` / `candidate.html` / `recruiter-offers.html` → toast « Réseau indisponible ».  
**Corrigé pendant l'exécution** : CSP complète restaurée (avec `connect-src` vers `8081`). Relance navigateur : **fails=0**.

---

## 6. État final du catalogue

- Endpoint : `GET /api/v1/catalog/offers` opérationnel sur `8081`
- Index : 107 listings internes listables
- Sync incrémentale : create / update (si reste publiée) / close vérifiée
- Matching Pour toi : multicritère interne via `evaluateIfAvailable`
- Reindex admin disponible

---

## 7. Connecteurs externes

| Connecteur | État |
|---|---|
| Jooble | **OFF** |
| Greenhouse | **OFF** |
| Lever | **OFF** |
| Ashby | **OFF** |
| `external_job_boards.enabled=1` | **0** |
| `startup-sync` | **false** |

---

## 8. Problèmes et corrections

| # | Problème | Correction |
|---|---|---|
| 1 | CSP tronquée → login FE « Réseau indisponible » | Restauration méta CSP complète |
| 2 | Tests update sans `published:true` dépubliaient l'offre | Retest avec `published:true` — sync OK |
| 3 | Faux échec pagination (mauvais champ id) | Retest sur `id` catalogue — pas de chevauchement |

Aucun rollback DB nécessaire. Aucune donnée métier effacée volontairement.

---

## 9. Rollback disponible (si besoin futur)

1. Arrêter `8081`.  
2. Remettre un binaire antérieur / classes zip sous `docs/deploy-prep/rollback-20261114-010509/`.  
3. **Ne restaurer le dump SQL qu'avec autorisation** si des données plus récentes doivent être préservées.  
4. Dump de référence : `hirelink-cutover-20261114-010509.sql` (SHA ci-dessus).

---

## 10. Limites respectées

- Pas de commit / push  
- Pas d'activation Jooble/ATS  
- Pas de rotation de secrets  
- Pas de déploiement public  
- `5500` non redémarré (fichiers FE mis à jour sur disque + CSP corrigée)

---

## 11. Conclusion

**SUCCESS** — intégration Phases 1–3 dans la démo locale terminée, catalogue opérationnel, authentification et parcours principaux validés, données historiques conservées, connecteurs externes désactivés.
