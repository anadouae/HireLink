# HireLink — Audit final offres externes (agrégateur professionnel)

**Date :** 2026-11-14  
**Référence bascule :** `docs/HIRELINK_PHASE3_DEPLOYMENT_EXECUTION_REPORT.md` (SUCCESS)  
**Livrables associés :**  
- `docs/HIRELINK_EXTERNAL_SOURCES_COMPLIANCE_MATRIX.md`  
- `docs/HIRELINK_EXTERNAL_JOBS_ACTIVATION_PLAN.md`

**Contraintes respectées :** lecture seule sur `hirelink` ; **aucun** redémarrage `8081`/`5500` ; **aucune** activation connecteur démo ; **aucun** commit/push ; **aucune** suppression de données ; secrets non modifiés.

---

## Verdict final

# READY WITH CONDITIONS

**Justification :** la plateforme catalogue (pagination SQL, sync interne, matching honnête, exclusion sample/quarantaine, connecteurs ATS/Jooble **implémentés et testés en isolation**) est techniquement prête.  
**Blocage produit Maroc :** aucune source marocaine **légalement activable immédiatement** sans clé Jooble + ToS, ou partenariat portail. Les 106 « externes » historiques ne constituent pas un flux marché fiable (98 masqués, 8 fixtures MAROC_EMPLOI exclues du catalogue).  
**Ne pas** inventer d'annonces pour simuler un agrégateur.

---

## 1. Phase A — Audit et sécurisation

### Services (vérifiés)

| Service | État |
|---|---|
| `8081` | LISTEN (JAR cutover Phase 3, pid 53912 au moment de l'audit) |
| `5500` | LISTEN |
| `GET /offers/public` | 200 |
| `GET /catalog/offers` | 200, **total=107** |
| Connecteurs runtime | Jooble/GH/Lever/Ashby **OFF** ; boards enabled **0** |

### Compteurs `hirelink` (lecture seule)

| Métrique | Valeur audit |
|---:|---:|
| `job_offers` | 123 |
| dont publiées | 108 |
| `external_job_offers` | 106 |
| disponibles | 8 |
| `hidden_by_admin` | 98 |
| `quarantined` | 0 |
| `users` | 126 |
| `applications` | 221 |
| `organizations` | 42 |
| `billing_invoices` | 8 |
| `catalog_listings` | 107 (**toutes INTERNAL**) |

Répartition externes : `MAROC_EMPLOI` 8 available ; `REMOTE_OK` 98 hidden.

### Données de test bascule (conservées, non supprimées)

| id | Titre | published |
|---:|---|---:|
| 159 | Bascule Phase3 Test CDI Spring Modifie | 0 |
| 160 | SyncUpdate Beta Rabat | 0 |
| 161 | SyncProof Two | 0 |
| 162 | LiveSync B | 0 |
| 163 | LiveSync D | **1** (publiée mais **absente** du catalogue — dérive sync à traiter en hygiène) |

### Git / fichiers sensibles

- Branche `main` avec nombreuses modifications **non commitées** (Phases 1–3 + FE).  
- Non suivis : `application-audit-iso.properties`, `application-cutover-runtime.properties`, `application-deploy-prep.properties`, dumps SQL sous `docs/deploy-prep/`.  
- Présents (hors git attendu) : `application-local.properties`, `backend/data/jwt.key`.  
- Backups : `hirelink-cutover-20261114-010509.sql`, `hirelink-full-20261113-201206.sql`.

### Risques de régression

| Risque | Niveau | Mitigation |
|---|---|---|
| Activer ATS/Jooble sans ToS | Haut | Plan d'activation + votre OK |
| `startup-sync=true` dans properties vs `false` runtime cutover | Moyen | Documenter avant prochain restart |
| LiveSync D publié hors catalogue | Faible | Resync/unpublish (autorisation hygiène) |
| Présenter MAROC_EMPLOI comme réel | Haut | Déjà exclu du catalogue public |
| Scraping portails MA | Critique | Interdit |

---

## 2. Ce qui fonctionne réellement (démo live)

| Capacité | Preuve |
|---|---|
| Catalogue unifié SQL | `total=107`, pagination, filtres pays |
| Offres internes HireLink | Origin `INTERNAL`, apply HireLink |
| Exclusion MAROC_EMPLOI public | 0 externe dans `catalog_listings` |
| Matching Pour toi multicritère | Implémenté ; anonyme → 0 (pas de faux scores) |
| Sync create/update/close interne | Validée à la bascule |
| FE candidat pays / Pour toi / login | Validés post-bascule (Edge) |
| Pipeline / billing / notifs | Smoke OK post-bascule |
| Connecteurs désactivés | Flags + 0 board enabled |

---

## 3. Ce qui a été testé sur des sources **réelles** (lecture réseau, hors DB démo)

| Sonde | Résultat | Preuve |
|---|---|---|
| Greenhouse `stripe` | HTTP 200, **786** jobs | `docs/deploy-prep/external-live-probes.txt` |
| Lever `leverdemo` | HTTP 200, **10** jobs | idem |
| Ashby `Ashby` | HTTP 200, **76** jobs | idem |
| Jooble MA `/api/about` | HTTP **403** (WAF) | idem |

**Aucune** de ces réponses n'a été écrite dans `hirelink`.

---

## 4. Ce qui fonctionne uniquement avec des **fixtures** / HTTP local

| Suite | Résultat | Nature |
|---|---|---|
| `AtsConnectorsPhase2IsolationTest` (5) | OK | HttpServer local + schema `hirelink_ext_phase02` |
| `AtsConnectorFetchTest` (2) | OK | fixtures |
| `AtsHttpClientTest` (2) | OK | SSRF / timeouts |
| Parsers GH/Lever/Ashby (6) | OK | JSON fixtures |
| `UrlQualityServiceTest` (8) | OK | unitaire |
| `CatalogPhase3IsolationTest` (5) | OK | schema isolé |
| Fixture Jooble | `fixtures/jooble-ma-200.json` | pas d'appel clé réelle |
| Feed `maroc-emploi-partenaires.v1.json` | Exemple local | **non réel** |

Relance 2026-11-14 : `TEST_EXIT=0` ; compteurs `hirelink` inchangés (123 / 107).

---

## 5. Sources utilisables immédiatement

| Usage | Statut |
|---|---|
| Catalogue **offres internes** HireLink | **Oui** (déjà en production démo) |
| Afficher comme agrégateur Maroc « plein » | **Non** |
| Jooble MA | **Non** (pas de clé + ToS) |
| ATS multi-boards | **Non** sans OK juridique (technique OK) |

---

## 6. Sources nécessitant clé ou autorisation

| Source | Besoin |
|---|---|
| Jooble MA | Clé régionale + validation ToS/API |
| Greenhouse / Lever / Ashby (agrégation) | Clarification juridique ; boards allowlist |
| Rekrute / Emploi.ma / ANAPEC | Partenariat / feed officiel |
| Ashby partner feed | Contrat partenaire |

---

## 7. Problèmes techniques détectés

1. **Catalogue externe vide** malgré 106 lignes : masquage RemoteOK + exclusion MAROC_EMPLOI (volontaire / qualité).  
2. **Fixture MAROC_EMPLOI** : liens parfois génériques (page d'accueil) — correct d'exclure.  
3. **Dérive** `LiveSync D` publiée sans ligne catalogue.  
4. **Jooble about** inaccessible (403) depuis l'environnement de test.  
5. **Divergence config** `startup-sync` fichier vs runtime.  
6. Couverture **Maroc ATS** faible (peu de boards locaux dans l'allowlist).

---

## 8. Corrections effectuées en isolation

**Aucune modification de code produit** dans cette mission d'audit (interdiction d'intégration).  
Travaux limités à : sondes, tests Maven isolés, rédaction documentaire.

Corrections antérieures déjà en démo (Phases 1–3) : sync catalogue, Pour toi, `city_fold`, V006 `VARCHAR`, CSP `connect-src`, etc.

---

## 9. Tests et preuves (index)

| Preuve | Emplacement |
|---|---|
| Surefire ATS/catalogue/URL | `backend/target/surefire-reports/` |
| Log relance | `docs/deploy-prep/external-final-tests.log` |
| Sondes live | `docs/deploy-prep/external-live-probes.txt` |
| Allowlist boards | `backend/src/main/resources/feeds/ats-boards-allowlist.v1.json` |
| Matrice conformité | `HIRELINK_EXTERNAL_SOURCES_COMPLIANCE_MATRIX.md` |
| Plan activation | `HIRELINK_EXTERNAL_JOBS_ACTIVATION_PLAN.md` |

---

## 10. Expérience candidat (Phase D) — état

| Attendu | État |
|---|---|
| Recherche mots-clés | OK (catalogue) |
| Maroc défaut | OK (`defaultCountry=MA`) |
| International (`ALL`) | OK |
| Ville / contrat / mode | OK (filtres + fold) |
| Source | OK param API |
| Date fiable | Interne : oui ; externe : seulement si source fiable |
| Pagination SQL | OK |
| Distinction interne/externe | OK (`external`, labels) |
| Pour toi honnête | OK (seuil 50 %, pas d'invention) |
| Redirection externe | Prévue ; peu d'items externes listables aujourd'hui |
| Pas d'apply interne sur externe | Règle métier en place |
| « Non précisé » | À renforcer UX si champs null (pas d'invention côté API) |

---

## 11. Synchronisation et qualité (Phase E)

| Mécanisme | État |
|---|---|
| Sync incrémentale interne | Live |
| Sync externe à l'import | Code prêt ; inactif sur démo |
| Idempotence `(source, external_id)` / catalogue `(origin, ref_id)` | Oui |
| Quarantaine URL | Colonnes + enforce config |
| Flags disable par source | Oui |
| Rebuild startup | `false` (runtime) |
| Réconciliation périodique | Non activée sur démo |
| Perf catalogue | Mesurée en isolation pré-bascule (jusqu'à 25k) |

Non-régression recruteur/ATS/billing : smoke post-bascule OK ; pas de rejeu complet dans cet audit.

---

## 12. Risques résiduels

1. Attente juridique Jooble / ATS.  
2. Attente clé Jooble.  
3. Fausse perception utilisateur (« 106 externes » ≠ catalogue public).  
4. Orphan `LiveSync D`.  
5. Prochain restart sans `application-cutover-runtime.properties` pourrait réactiver `startup-sync=true` (maroc fixture cron) — à verrouiller avant.

---

## 13. Plan exact d'activation

Voir `docs/HIRELINK_EXTERNAL_JOBS_ACTIVATION_PLAN.md` (Vagues 0–3).

**En attente de votre choix explicite** parmi : aucune activation / canary ATS nommé / Jooble MA avec clé / hygiène LiveSync+config.

---

## 14. Réponse claire à l'objectif « agrégateur Maroc »

> **Aujourd'hui, HireLink est un SaaS de recrutement avec catalogue d'offres internes professionnel.**  
> **Ce n'est pas encore un agrégateur d'offres externes marocaines réelles et actualisées.**  
> Les briques techniques (connecteurs, qualité URL, catalogue) sont prêtes ; le frein est **légal / contractualisation / clé**, pas le manque de parsers.

Ne fabriquer aucune offre fictive pour combler ce trou.
