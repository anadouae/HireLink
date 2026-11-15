# HireLink — Rapport de réparation démo 8081

**Date :** 2026-11-14
**Autorisation :** réparation explicite (JAR Spring Boot incomplet)
**Objectif :** supprimer les 500 recruteur sans perte de données
**Commit / push :** aucun

---

## Verdict

# PASS — DÉMO 8081/5500 RETROUVÉE FONCTIONNELLE

Backend relancé depuis un **JAR fat vérifié hors `target/`**.
Endpoints recruteur / OWNER en **200**, UI tableau de bord + pipeline OK, candidat / catalogue / Pour toi OK, **123 offres** conservées, connecteurs off, **0** `NoClassDefFoundError` dans le nouveau log.

---

## 1. Préparation (avant interruption)

| Étape | Résultat |
|---|---|
| PID / commande runtime sauvegardés | PID `17648` → `java -jar …/target/hirelink-1.0.0-SNAPSHOT.jar` + `application-cutover-runtime.properties` |
| Config runtime (secrets masqués) | `docs/deploy-prep/rollback-demo8081-repair-20261114-192542/application-cutover-runtime.properties.redacted` |
| Offres avant | **123** |
| FE 5500 | **200** (non modifié, non redémarré) |
| Point de retour JAR fat | Copie `hirelink-fat-rollback.jar` (71 081 592 o) dans le dossier rollback |

### JAR de démarrage (hors `target/`)

Tentative de build isolé (`-Dproject.build.directory=target-demo-repair`) : Maven a quand même ciblé `backend/target` et `repackage` a échoué (fichier verrouillé) — **non utilisé**.

JAR retenu (vérifié avant stop) :

| Contrôle | Valeur |
|---|---|
| Source | `backend/target-audit/hirelink-1.0.0-SNAPSHOT.jar` |
| Copie runtime | `docs/deploy-prep/demo-runtime/hirelink-demo-8081.jar` |
| Taille | **71 081 592** octets |
| Manifest | `Main-Class: JarLauncher`, `Start-Class: HireLinkApplication` |
| `BOOT-INF/lib` | **100** JARs |
| Hibernate | `hibernate-core-6.5.3.Final.jar` présent |
| JJWT | `jjwt-api/impl/jackson` présents |

Preuve : `docs/deploy-prep/demo-runtime/jar-verification.txt`

---

## 2. Redémarrage

| Étape | Résultat |
|---|---|
| Arrêt | PID **17648** uniquement (port 8081) |
| FE 5500 pendant l’arrêt BE | **200** |
| Offres pendant l’arrêt | **123** |
| Démarrage | `java -jar docs/deploy-prep/demo-runtime/hirelink-demo-8081.jar --spring.config.additional-location=file:…/application-cutover-runtime.properties` |
| Nouveau PID | **30456** |
| `Started HireLinkApplication` | **Oui** (~46 s) |
| Health | **UP** |

Logs boot utiles :

```text
Allowlist ATS chargee : 5 board(s), 0 actif(s)
Tomcat started on port 8081
Started HireLinkApplication
Seed OK — 126 users, 123 offres, 221 candidatures, 13 motifs
```

Config runtime inchangée (connecteurs `enabled=false`, `startup-sync=false`, DB `hirelink`, port `8081`).

---

## 3. Tests post-réparation

### API

| Test | Résultat |
|---|---|
| `GET /api/v1/health` | **UP** |
| Login recruteur | OK — Salma Berrada |
| `GET /organizations/mine` (recruteur) | **200** |
| `GET /offers/mine` (recruteur) | **200** |
| `GET /applications/pipeline` (recruteur) | **200** |
| Login OWNER | OK — Nadia Bennani |
| `/organizations/mine`, `/offers/mine`, `/pipeline` (OWNER) | **200** / **200** / **200** |
| Login candidat + offres publiques | **107** |
| Catalogue | **107** |
| Pour toi | **13** |
| `hirelink.job_offers` | **123** |
| `NoClassDefFoundError` / `ClassNotFoundException` (nouveau log) | **0** |
| Connecteurs | 0 actifs |

### UI (Playwright, ports 5500→8081, sans bypass CSP)

| Écran | Résultat |
|---|---|
| Tableau de bord recruteur | OK (plus d’« erreur interne ») |
| Pipeline recruteur | OK (Kanban) |
| Candidat + Pour toi | 20 cartes → 13 en mode profil |
| Violations CSP console | 0 |

Captures : `docs/deploy-prep/demo-runtime/post-repair-tests/`
(`shot-recruiter-dashboard.png`, `shot-recruiter-pipeline.png`, `shot-candidate-pour-toi.png`)

Détail texte : `post-repair-tests/api-results.txt`, `browser-results.txt`

---

## 4. Données & contraintes

| Contrainte | Statut |
|---|---|
| Pas de migration / ALTER sur `hirelink` | Respecté |
| Pas de suppression de données | Respecté (123 offres avant = après) |
| Secrets / comptes inchangés | Respecté |
| Frontend 5500 non modifié / non redémarré | Respecté |
| Correction CSP non intégrée à cette réparation | Respecté (JAR audit préexistant) |
| Pas de commit / push | Respecté |

---

## 5. Point de retour

Dossier : `docs/deploy-prep/rollback-demo8081-repair-20261114-192542/`

Contient : commande runtime, properties redacted, JAR fat de secours, compteur offres.

En cas de besoin futur : relancer le même binaire
`docs/deploy-prep/demo-runtime/hirelink-demo-8081.jar`
(ou la copie rollback) **sans** repartir de `backend/target/*.jar` tant qu’un `mvn package` peut l’écraser.

---

## 6. Recommandation opérationnelle

Ne plus lancer la démo depuis `backend/target/hirelink-1.0.0-SNAPSHOT.jar`.
Toujours utiliser une copie sous `docs/deploy-prep/demo-runtime/` pour éviter la récidive (JAR thin + `repackage` impossible sous JVM active).

---

## Verdict final

**PASS** — démo **8081/5500** de nouveau pleinement utilisable (recruteur, OWNER, candidat), sans perte de données.
