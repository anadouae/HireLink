# HireLink — Rapport redémarrage runtime Vague 0

**Date :** 2026-11-14  
**Autorisation :** redémarrage contrôlé **backend 8081 uniquement** (appliquer config Vague 0)  
**Hors périmètre respecté :** pas de redémarrage `5500`, pas de migration, pas d’activation connecteur, pas de commit/push, pas de suppression métier, pas de restore SQL aveugle.

---

## Verdict

**SUCCESS** — backend `8081` redémarré avec config hygiène appliquée ; compteurs métier **inchangés** ; **aucun import fictif** au boot ; parcours API candidat / recruteur / catalogue / Pour toi / pipeline / facturation **OK**.

**Point d’attention :** le frontend `5500` était **déjà DOWN** avant l’opération et **n’a pas été relancé** (interdiction). Les pages FE existent sur disque ; la vérif HTTP UI est donc **N/A** jusqu’à une autorisation séparée de relance `5500`.

---

## 1. Pré-redémarrage

### 1.1 Sauvegarde Vague 0

| Attribut | Valeur |
|---|---|
| Fichier | `docs/deploy-prep/hirelink-vague0-20261114-015822.sql` |
| Accessible | **oui** |
| Octets | 2 197 014 |
| SHA-256 | `9B1DF5D68A92A0BCF74F2A4D50F9A73599B6547B0B32E237004AEA8289819639` (inchangé) |
| Dump | `Database: hirelink` · `Dump completed on 2026-11-14 1:58:29` |

### 1.2 État des services avant

| Service | État |
|---|---|
| `8081` | **DOWN** (aucun listener ; dernier pid connu cutover = 53912, déjà arrêté) |
| `5500` | **DOWN** (aucun listener) |
| MySQL `3306` | **UP** (XAMPP) |

Conséquence : l’opération est un **démarrage contrôlé** (même artefact / même schéma cutover), pas un kill+restart d’un process vivant.

### 1.3 Compteurs `hirelink` avant

| Entité | Avant |
|---:|---:|
| `job_offers` | 123 |
| `applications` | 221 |
| `users` | 126 |
| `organizations` | 42 |
| `external_job_offers` | 106 (MAROC 8 · REMOTE_OK 98) |
| `ext_available` | 0 |
| `catalog_listings` | 107 (source `HIRELINK`) |
| `offers_open_listable` | 107 |
| `billing_invoices` | 8 |
| `boards_enabled` | 0 |

Preuve : `docs/deploy-prep/vague0-restart-pre-counts.txt`.

### 1.4 JAR / config réellement utilisés (historique + cible)

| Élément | Constat |
|---|---|
| JAR cutover | `backend/target/hirelink-1.0.0-SNAPSHOT.jar` |
| Config runtime cutover | `backend/application-cutover-runtime.properties` via `--spring.config.additional-location` |
| JAR **avant rebuild** | embarquait encore `startup-sync=true` et `maroc.enabled=true` (stale vs source Vague 0) |
| Source `application.properties` | déjà hygiène (`false` pour sync/maroc/remoteok/jooble/ATS) |
| Cutover-runtime | déjà hygiène complète (`false` partout + `rebuild-on-startup=false`) |

**Mesure prise :** rebuild Maven (`mvn -o package -DskipTests`, BUILD SUCCESS) pour aligner le JAR embarqué, **puis** démarrage avec cutover-runtime (double verrouillage).

### 1.5 Flags qui seront / ont été appliqués

| Clé | Source JAR (après rebuild) | Cutover-runtime | Effet attendu |
|---|---|---|---|
| `startup-sync` | `false` | `false` | pas d’import auto au boot |
| `maroc.enabled` | `false` | `false` | pas de sync fixture MAROC |
| `remoteok.enabled` | `false` | `false` | OFF |
| `jooble.enabled` | `false` | `false` | OFF |
| `greenhouse/lever/ashby` | `false` | `false` | OFF |
| `catalog.rebuild-on-startup` | `false` | `false` | catalogue non reconstruit au boot |

### 1.6 Rollback préparé (sans restore DB)

Dossier : `docs/deploy-prep/rollback-vague0-runtime-20261114-164237/`

Contient :
- JAR **pré-rebuild** (`hirelink-1.0.0-SNAPSHOT.jar`)
- `application-cutover-runtime.properties`
- copie source `application.properties`
- `README.txt` (procédure stop → restore JAR/props → relance)

Backup SQL Vague 0 conservé pour cas de corruption données uniquement (non utilisé).

---

## 2. Opération

| Étape | Résultat |
|---|---|
| Rebuild JAR | SUCCESS (~18 s) — log `vague0-restart-build.log` |
| Démarrage | `java -jar ... --spring.config.additional-location=file:.../application-cutover-runtime.properties` |
| PID | **17648** (Java 23.0.1) |
| Started | `Started HireLinkApplication in 50.45 seconds` |
| Port | `8081` LISTEN |
| Logs | `docs/deploy-prep/vague0-restart-8081.log` |

Commande exacte (WMI) :

```text
C:\Program Files\Java\jdk-23\bin\java.exe -jar C:\Users\daurinia\Desktop\HireLink\backend\target\hirelink-1.0.0-SNAPSHOT.jar --spring.config.additional-location=file:C:\Users\daurinia\Desktop\HireLink\backend\application-cutover-runtime.properties
```

---

## 3. Paramètres réellement chargés (preuves runtime)

| Preuve | Observation |
|---|---|
| Allowlist ATS | `5 board(s), 0 actif(s)` |
| Import MAROC / RemoteOK / Jooble au boot | **aucune** ligne d’import dans le log |
| `last_synced_at` MAROC | `2026-11-14 01:45:02` — **inchangé** après restart |
| `last_synced_at` REMOTE_OK | `2026-11-12 22:15:04` — **inchangé** |
| Seed | `Seed OK — 126 users, 123 offres, 221 candidatures` (pas de création nette) |
| Externes | toujours 106 ; `available=0` |

Conclusion : les flags hygiène sont **effectifs** (pas d’import fictif).

---

## 4. Compteurs avant / après

| Entité | Avant | Après | Δ |
|---:|---:|---:|---:|
| `job_offers` | 123 | 123 | 0 |
| `applications` | 221 | 221 | 0 |
| `users` | 126 | 126 | 0 |
| `organizations` | 42 | 42 | 0 |
| `external_job_offers` | 106 | 106 | 0 |
| `catalog_listings` | 107 | 107 | 0 |
| `billing_invoices` | 8 | 8 | 0 |
| `offers_open_listable` | 107 | 107 | 0 |
| `boards_enabled` | 0 | 0 | 0 |

`LiveSync D` (id 163) : toujours `published=1` + `closed_at` renseigné — hors catalogue, cohérent.

---

## 5. Tests post-redémarrage

Preuve : `docs/deploy-prep/vague0-restart-smoke.txt`

| Test | Résultat |
|---|---|
| `GET /api/v1/health` | UP · DB connected |
| Offres publiques | 200 |
| Catalogue `totalElements` | **107** |
| Login candidat (`candidat@hirelink.ma`) | OK (Sara El Amrani) |
| Candidatures (`/applications/mine`) | OK (14) |
| Pour toi (`/catalog/offers?forYou=true`) | OK — **13** items, scanned **107** |
| Login recruteur | OK |
| Pipeline `/applications/pipeline` | **200** |
| Facturation `/billing/plans` | **200** |
| Facturation `/billing/invoices` | **200** |
| FE `5500` | **DOWN** (préexistant ; non relancé) |
| Import fictif auto | **aucun** |

---

## 6. Blocages / non-faits

| Item | Statut |
|---|---|
| Relance frontend `5500` | Non faite (hors autorisation) |
| Activation Jooble / ATS / RemoteOK / Maroc | Non faite |
| Commit / push | Non faits |
| Restore SQL | Non nécessaire |

---

## 7. Prochaine étape

1. Autoriser éventuellement la **relance du frontend 5500** pour smoke UI navigateur.  
2. Ensuite seulement : Vague 1–2 offres marocaines (Jooble MA + ToS, ou partenariat) selon `HIRELINK_EXTERNAL_JOBS_ACTIVATION_PLAN.md`.
