# HireLink — rapport de finalisation (2026-11-15)

**Verdict : application NON déclarée entièrement terminée.**
Cause bloquante live : JVM 8081 (PID 17008) en **classloader corrompu** (`NoClassDefFoundError: EntityUpdateAction`) → paiement 500 et d’autres écritures Hibernate.
Le **code** paiement + B2/D2/D5/F5/B0 + filtres match + entretiens FE est **corrigé et prouvé en isolation** ; déploiement 8081 **non exécuté** (autorisation requise).

---

## 1. Cause exacte du paiement HTTP 500 (`f050a1b2` / `95c59e7d`)

| | |
|--|--|
| Symptôme | `POST /api/v1/billing/payments` → 500 |
| Log | `docs/evidence/b0-cutover-8081/b0-8081.out.log` |
| Exception | `NoClassDefFoundError: org/hibernate/action/internal/EntityUpdateAction` (puis aussi `jakarta.servlet.http.Cookie`) |
| Classes dans le JAR | **Présentes** (`hibernate-core-6.5.3.Final.jar` imbriqué) |
| Conclusion | **Pas un bug métier de facturation** : classloader Spring Boot / JVM longue durée dégradée sur le process qui sert 8081 |
| Preuve code OK | `BillingPaymentIntegrationTest` **PASS** ; smoke JAR candidat `:18086` : `SUCCEEDED` → plan `PREMIUM`, crédits IA 199/200, replay OK, double **409**, non-OWNER **403** |

### Correctifs code paiement (sans changer les règles métier)

- Validation anticipée du scénario `SUCCESS`/`FAILURE` → 400 clair
- `rightsApplied` : `!Boolean.TRUE.equals(...)` pour ne pas sauter l’octroi si `null`
- Script build : **jamais écraser** un JAR verrouillé par une JVM (`hirelink-candidate-<stamp>-<sha12>.jar`)

### Déploiement live

Le paiement live restera en 500 **tant que le PID 17008 n’est pas redémarré** sur un JAR candidat sain (cutover autorisé).

---

## 2. JAR déployé vs JAR candidat

| Instance | Chemin | SHA-256 |
|----------|--------|---------|
| **8081 actif** | `…/b0-candidate-runtime/hirelink-b0-backend.jar` (verrouillé) | `C63FC7EEBB61B3BB7DC60689359030270080D6407B3FF60F06DF54C17BF97C4D` |
| **Nouveau candidat** | `hirelink-candidate-20261115-095739-E65802C4A567.jar` | `E65802C4A567CA159DB6E378C782A3B5E679D23C8E38C9A37EC2051CC779E644` |
| Pointeur | `hirelink-b0-backend-latest.jar` | = candidat E658… |
| Démo archivée | `demo-runtime/hirelink-demo-8081.jar` | `C5722732…` (inchangé) |

`legacy_updated=False` : le build n’a **pas** écrasé le JAR du PID 17008.

---

## 3. Anomalies initiales → statut réel

| ID | Sujet | Statut |
|----|-------|--------|
| Paiement 500 | Classloader JVM | **ROOT CAUSE IDENTIFIÉE** ; code OK isolé ; **FAIL live** jusqu’au restart |
| B0 Externes / MAROC | Exclusion | **PASS** isolé + live maroc=0 ; candidate JAR OK |
| B2 Places × pays | Filtre pays | **PASS** units + iso `:18086` ; **FAIL live** (bytecode/JVM) |
| D1 Historique UI | Bouton timeline | **PASS** navigateur (session précédente) + API timeline |
| D2 Refus → annule entretien | + notifs | **PASS** units ; live encore `REJECTED`+`SCHEDULED` (×3) |
| D3 Identité | Libellé | **PASS** (antérieur) |
| D5 firstDecisionAt | SLA 1ʳᵉ réponse | **PASS** code |
| F4 Disclaimer paiement | Checkout | **PASS** FE |
| F5 Crédits vs plan org | | **PASS** units + iso pay → AI `PREMIUM` |
| B3 Compatibilité min | Page vs corpus | **PASS** code : `minMatch` serveur avant pagination |
| Entretiens « À venir 0 » | vs pipeline | **PASS** FE : futur / à replanifier / passé alignés étape INTERVIEW |
| E1 Notifs vs e-mail | | **PASS** partiel (libellé centre) |
| A1/A2/A4/C1/C2/G1 | | **PASS** (antérieurs) |
| Admin reports | | **FAIL live** (500 classloader) |

---

## 4. Corrections apportées (cette vague)

1. Paiement : validation scénario + `rightsApplied` robuste
2. Build JAR : noms distincts, refus d’écrasement si verrouillé
3. Catalogue : param `minMatch` → filtre score **avant** pagination
4. FE candidat : envoi `minMatch` au serveur
5. FE entretiens : regroupement upcoming/todo/past cohérent avec le pipeline
6. Crédits IA : plan **organisation** (vague précédente, dans candidat E658…)

---

## 5. Tests

| Type | Résultat |
|------|----------|
| `BillingPaymentIntegrationTest` | **PASS** |
| `ApplicationServiceInterviewTest` (D2 + notifs) | **PASS** |
| `PlaceCatalogCountryFilterTest` (B2) | **PASS** |
| `AiCreditOrgPlanTest` (F5) | **PASS** |
| Smoke JAR E658… `:18086` | Health UP ; B2 places OK ; **paiement SUCCEEDED** ; IA PREMIUM ; idempotence ; 409 ; 403 non-OWNER |
| API live 8081 | Catalogue 107 ; B0 maroc 0 ; OWNER/admin login ; **paiement 500** ; places non filtrées |
| Navigateur | D1 PASS (antérieur) ; session actuelle : **écran verrouillé** (computer use impossible) — parcours UI complets **NON RETESTÉS** ici |

---

## 6. Parcours (preuve)

| Parcours | Live 8081 | Isolé / code |
|----------|-----------|--------------|
| Candidat login, catalogue, apps, notifs, IA | PASS API | — |
| Recruteur pipeline / permissions invite | PASS API (403) | — |
| OWNER membres, quote, subscribe | PASS API | PASS + pay |
| OWNER paiement simulé → facture → crédits | **FAIL 500** | **PASS** |
| Admin stats | PASS | — |
| Admin reports / modération | FAIL/404 | NON TESTÉ à fond |
| Matching / minMatch serveur | NON déployé | Code + FE prêts |
| Inscription bout-en-bout UI | NON TESTÉ | — |
| Analyse CV / synthèse IA UI | NON TESTÉ cette session | — |

---

## 7. Préservation démo

| Contrôle | Valeur |
|----------|--------|
| Health 8081 | UP PID **17008** |
| Offres `hirelink.job_offers` | **123** |
| Catalogue candidat | **107** |
| MAROC_EMPLOI API | **0** |
| Connecteurs | OFF |
| JAR démo archivé | inchangé |
| Iso smoke | DB `hirelink_pay_iso` / port 18086 — **stoppé** après tests |

---

## 8. GO / NO-GO cutover 8081

### GO si autorisé

1. Backup `mysqldump` hirelink → `backup-db/`
2. Stop **uniquement** PID 17008
3. Démarrer `hirelink-candidate-20261115-095739-E65802C4A567.jar` (ou `…-latest.jar`) avec la **même** `application-cutover-runtime.properties`
4. Smoke : health ; login OWNER ; **POST payments SUCCESS** ; places `Ta&MA` sans France ; pipeline ; AI plan PREMIUM si org payée

### NO-GO / rollback

- Relancer l’ancien JAR verrouillé (`C63FC7EE…`) ou `demo-runtime` `C5722732…`
- Vérifier offers=123

Plan détaillé : `docs/HIRELINK_CUTOVER_8081_PLAN.md` (à aligner sur SHA **E65802C4…**).

---

## 9. Actions nécessitant votre autorisation explicite

1. **Restart / cutover 8081** (seul moyen de soigner le paiement 500 live et d’activer B2/D2/F5/minMatch)
2. **Commit Git** (et push si demandé)
3. Recette navigateur complète une fois le desktop déverrouillé / après cutover

---

## 10. Ouvert après cutover (à revalider)

- 3 dossiers live `REJECTED`+`SCHEDULED` (données historiques) → re-smoke D2
- Admin signalements / reports UI
- Inscription + CV/IA bout-en-bout navigateur
- Compteurs facettes villes (B4) si encore page-locales
