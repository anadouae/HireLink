# HireLink — Rapport cutover B0 backend sur 8081

**Verdict : CUTOVER PASS**
**Date :** 2026-11-15
**Rollback :** non effectué (non nécessaire)

---

## 1. Synthèse

| Élément | Valeur |
|---------|--------|
| Mode | Option B — JAR B0 sous **son propre nom** |
| Artefact actif | `docs/deploy-prep/b0-candidate-runtime/hirelink-b0-backend.jar` |
| SHA-256 actif | `F0197F1AF3D798B78E13A624CB0C2F30EC5181F61F72EC48E42F06F9B9AD8972` |
| JAR stable | `docs/deploy-prep/demo-runtime/hirelink-demo-8081.jar` **intact** |
| SHA-256 stable | `C5722732E351C1E0636A48A41D1AAADD5A30F8355D14B088B6DAEDB1C71B7A8E` |
| PID avant | **4716** (`hirelink-demo-8081.jar`) |
| PID après | **17008** (`hirelink-b0-backend.jar`) |
| Base `hirelink` | **123** offres, catalogue **107** — inchangés |
| Connecteurs | **disabled** (`admin/external-offers/status` → `enabled=false`) |
| `MAROC_EMPLOI` | **8** lignes en DB, **0** dans `GET /external-offers` |

---

## 2. Horodatage

| Étape | Heure (UTC) |
|-------|-------------|
| Précheck GO | 2026-11-15T01:53:42Z |
| Stop PID 4716 | 2026-11-15T01:54:46Z |
| Start B0 PID 17008 | 2026-11-15T01:54:48Z |
| Health UP | 2026-11-15T01:55:27Z (~18 s) |
| Postcheck + smokes | 2026-11-15T01:56:16Z → 01:56:46Z |

---

## 3. Préchecks (avant tout arrêt)

Tous **GO** :

- health UP ; offers **123** ; catalog **107**
- FE 5500 : était **déjà down** au premier essai → serveur Python `http.server` relancé sur 5500 (**PID 8416**) pour permettre le smoke FE ; **8081 non touché**
- datasource = `hirelink` ; `startup-sync=false` ; connecteurs `false`
- backups SQL trouvés (`HireLink-sauvegardes/hirelink-20261109-*.sql`)
- PID unique **4716**, cmdline = JDK23 + `hirelink-demo-8081.jar` + `application-cutover-runtime.properties`
- candidat SHA attendu OK ; copie de sauvegarde du JAR stable : `hirelink-demo-8081.jar.bak-pre-b0-20261115`

Overrides runtime ajoutés au démarrage B0 (sans modifier les fichiers secrets) :

`seed=false`, `force-reset=false`, `ddl-auto=update`, sync/connectors/`rebuild-on-startup=false`.

---

## 4. Opération

1. Re-vérification identité PID 4716 → stop **uniquement** ce PID.
2. Port 8081 libre ; JAR stable SHA inchangé.
3. Démarrage :
   `java -jar …/b0-candidate-runtime/hirelink-b0-backend.jar --spring.config.additional-location=file:…/application-cutover-runtime.properties` (+ overrides sûrs).
4. 5500 laissé en place (non arrêté).

Fichier d’état : `docs/evidence/b0-cutover-8081/ACTIVE_ARTIFACT.txt`
Commande enregistrée : `docs/deploy-prep/b0-candidate-runtime/start-command-8081-b0.txt`

---

## 5. GO / NO-GO post-démarrage

| # | Critère | Résultat |
|---|---------|----------|
| 1 | health UP | **PASS** |
| 2 | offers = 123 | **PASS** |
| 3 | catalog = 107 | **PASS** |
| 4 | exclusion MAROC | **PASS** (8 en DB → 0 API) |
| 5 | FE landing / JS | **PASS** (107 offres affichées) |
| 6 | connecteurs off | **PASS** (`enabled=false`) |
| 7 | JAR actif = B0 | **PASS** (PID 17008) |
| — | JAR stable intact | **PASS** |

---

## 6. Smokes authentifiés

| Parcours | Résultat |
|----------|----------|
| Login candidat Sara | OK — catalog 107, apps mine 14 |
| UI candidat | OK — 20 cartes, hint 107 |
| Login recruteur Salma | OK — offers/mine **12** |
| `GET /applications/pipeline` | OK — **22** dossiers |
| UI pipeline | OK — page chargée, pas d’erreur réseau |
| Note | Un premier probe `/applications/inbox` a renvoyé 404 (mauvais chemin) — **non bloquant** ; endpoint réel `/applications/pipeline` OK |

Captures : `docs/evidence/b0-cutover-8081/shot-*.png`

---

## 7. Erreurs / incidents

| Incident | Traitement |
|----------|------------|
| FE 5500 déjà down avant cutover | Relance `python -m http.server 5500` ; pas d’impact 8081 |
| Probe API `/applications/inbox` 404 | Fausse alerte ; pipeline réel validé |

Aucun rollback. Aucune modification SQL sur `hirelink`. Aucun secret modifié. Aucun commit/push.

---

## 8. État final

- **8081** : UP — B0 fat JAR actif (`hirelink-b0-backend.jar`, PID **17008**)
- **5500** : UP (PID **8416**)
- **Stable JAR** : non écrasé (+ `.bak-pre-b0-20261115`)
- **Données** : 123 offres / catalog 107

**CUTOVER PASS**
