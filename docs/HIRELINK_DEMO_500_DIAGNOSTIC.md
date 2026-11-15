# HireLink — Diagnostic des erreurs 500 (démo 8081)

**Date :** 2026-11-14
**Phase :** lecture seule — **aucune réparation appliquée**
**Démo :** PID `17648` — `java -jar …/hirelink-1.0.0-SNAPSHOT.jar` + `application-cutover-runtime.properties`
**Contraintes respectées :** pas de restart, pas de modification `hirelink`, pas de migration, pas de commit/push

---

## Verdict

# CAUSE RACINE IDENTIFIÉE — PAS UNE INCOHÉRENCE SCHÉMA / MIGRATIONS

Les 500 recruteur (`/organizations/mine`, `/offers/mine`, `/applications/pipeline`) viennent d’un **classpath JVM corrompu** : le **JAR exécutable Spring Boot (~71 Mo)** a été **écrasé par un JAR « thin » Maven (~848 Ko)** pendant que le processus 8081 était encore vivant.
`spring-boot:repackage` a ensuite **échoué** (fichier verrouillé) → le disque a gardé le JAR thin **sans** `BOOT-INF/lib`.

Les classes déjà chargées continuent de répondre (health, login, catalogue candidat).
Dès qu’un chemin recruteur charge une classe Hibernate pas encore résolue → **`NoClassDefFoundError`** → 500.

---

## 1. Reproduction (sans écriture BDD)

| Appel | Compte | Résultat |
|---|---|---|
| `GET /api/v1/health` | — | **200 UP** |
| `POST /auth/login` | `recruteur@hirelink.ma` | **200** |
| `GET /organizations/mine` | recruteur | **500** (ex. ref `429acea9`, puis `8016a7ae`) |
| `GET /offers/mine` | recruteur | **500** (ex. `81b3e333`) |
| `GET /applications/pipeline` | recruteur | **500** (ex. `f78adf64`) |
| Mêmes 3 endpoints | OWNER `n.bennani@…` | **500** |
| `GET /offers/public` + `/catalog/offers` | candidat | **200** (107) |
| `GET /applications/mine` | candidat | **200** |

Base `hirelink.job_offers` : **123** (inchangée). FE 5500 : **200**.

---

## 2. Preuves logs (backend actuel)

Fichier vivant : `docs/deploy-prep/vague0-restart-8081.log` (s’allonge à chaque 500).

Exception typique :

```text
Erreur interne […] : jakarta.servlet.ServletException: Handler dispatch failed:
  java.lang.NoClassDefFoundError: org/hibernate/internal/util/collections/BoundedConcurrentHashMap$1

Caused by: java.lang.ClassNotFoundException:
  org.hibernate.internal.util.collections.BoundedConcurrentHashMap$1
```

Chaîne métier (exemple `/organizations/mine`) :

1. `OrganizationController.mine`
2. `OrganizationService.ensureForRecruiter`
3. `MembershipRepository.findByUserIdAndActiveTrue` (requête JPQL + cache HQL Hibernate)
4. échec chargement classe interne Hibernate → 500

Autres symptômes du **même** incident classpath (pas des bugs métier distincts) :

- `NoClassDefFoundError: …AbstractHandlerMethodMapping$MatchComparator`
- `ClassNotFoundException: io.jsonwebtoken.lang.DateFormats`

Première vague Hibernate NCDFE observée : **2026-11-14T19:44:23+01:00**.

---

## 3. Chronologie fatale (disque + Maven)

| Heure (locale / log) | Événement |
|---|---|
| ~17:46 | Démo démarre : `Started HireLinkApplication` (PID 17648) sur **JAR fat Spring Boot** |
| **17:48:49** | `mvn … package` (campagne P0) : `jar:jar` **réécrit** `target/hirelink-1.0.0-SNAPSHOT.jar` en JAR **thin** |
| 17:48:50 | `spring-boot:repackage` **FAIL** : *Unable to rename …jar to …jar.original* (JAR verrouillé par la JVM) |
| Après | Sur disque : JAR **848 138 octets**, **sans** `Main-Class` / `Start-Class`, **0** entrée `BOOT-INF/lib/*` |
| 19:44+ | Endpoints recruteur → NCDFE Hibernate |

Preuve build : `tmp/p0-build.log` lignes 27–37 :

```text
--- jar:3.4.2:jar --- Building jar: .../hirelink-1.0.0-SNAPSHOT.jar
--- spring-boot:3.3.13:repackage ---
BUILD FAILURE
Unable to rename '...jar' to '...jar.original'
```

| Artefact | Taille | Nature |
|---|---|---|
| `backend/target/hirelink-1.0.0-SNAPSHOT.jar` **actuel** | **848 138** | JAR thin (classes app seulement) |
| `docs/deploy-prep/rollback-vague0-runtime-20261114-164237/…jar` | **71 081 592** | JAR fat OK (`JarLauncher`, **101** libs) |
| `backend/target-audit/…`, `hirelink-phase3-…` | ~71 Mo | JAR fat de référence |

---

## 4. Schéma / migrations — écarté

Comparaison lecture seule `hirelink` vs `hirelink_github_clone_test` :

| Contrôle | Résultat |
|---|---|
| Nombre de tables | **34 = 34** |
| Colonnes `memberships` | **Identiques** (`org_role` enum OWNER/RECRUTEUR/HIRING_MANAGER, `active`, `suspended_at`, …) |
| Données membership recruteur `user_id=11` | Présentes et cohérentes |

→ **Pas** un décalage V000–V006 / code / données.
Le clone isolé fonctionne car il tourne via **`spring-boot:run`** (classpath Maven + `target/classes`), **pas** via ce JAR thin cassé.

---

## 5. Pourquoi le candidat marche encore

| Zone | Classes | Effet |
|---|---|---|
| Health, auth login, catalogue, offres publiques | Déjà chargées en Metaspace au boot / premiers appels | OK |
| Pipeline / org / offers mine | Nouveaux chemins JPQL Hibernate (cache HQL → `BoundedConcurrentHashMap$1`) | Chargement depuis l’archive **désormais thin** → **NCDFE** |

Ce n’est **pas** un bug UI CSP (déjà corrigé à part).

---

## 6. Correctif minimal recommandé (en attente d’autorisation)

### Action

1. **Autoriser l’arrêt** du processus 8081 (PID 17648) — seule réparation réelle possible.
2. Reconstruire un JAR fat **pendant que 8081 est arrêté** :
   `mvn -DskipTests -f backend/pom.xml package`
   Vérifier : taille ~70 Mo+, `Main-Class: org.springframework.boot.loader.launch.JarLauncher`, libs dans `BOOT-INF/lib`.
3. Relancer exactement comme avant :
   `java -jar backend/target/hirelink-1.0.0-SNAPSHOT.jar --spring.config.additional-location=file:backend/application-cutover-runtime.properties`
4. FE 5500 inchangé.

### Alternative sûre

Lancer depuis une **copie** hors `target/` (ex. le rollback fat déjà présent) pour éviter qu’un futur `mvn package` n’écrase le binaire en cours d’exécution.

### Ce qu’il ne faut **pas** faire pour ce bug

- Migrer / toucher `hirelink`
- Modifier le code métier des 3 endpoints
- Activer des connecteurs
- Changer les secrets

### Risques

| Risque | Niveau | Mitigation |
|---|---|---|
| Interruption courte de la démo | Moyen (attendu) | Fenêtre planifiée ; FE peut rester up |
| Seed au redémarrage | Faible | Seed idempotent / données déjà présentes |
| Recréer un JAR thin si package pendant run | Élevé si on ignore la règle | Ne jamais `package` le même chemin sous `-jar` actif |

### Tests après réparation (à faire une fois autorisé)

- `GET /organizations/mine` → 200 (recruteur + OWNER)
- `GET /offers/mine` → 200
- `GET /applications/pipeline` → 200 + Kanban UI
- Candidat catalogue / Pour toi inchangés
- `hirelink.job_offers` toujours 123
- Connecteurs toujours off
- Aucune NCDFE dans le nouveau log

### Prévention (optionnelle, plus tard)

- Documenter : « ne pas `mvn package` sur `target/*.jar` tant que la démo `-jar` tourne »
- Ou `spring-boot.repackage` avec classifier / sortie dédiée
- Toujours démarrer la démo depuis un fichier **copié** hors `target/`

---

## 7. Impact

| Domaine | Impact du diagnostic | Impact du correctif proposé |
|---|---|---|
| Code source | Aucun | Aucun (redémarrage + rebuild) |
| Base `hirelink` | Aucun | Aucun |
| Démo actuelle | Toujours UP mais recruteur cassé | Remise en service après restart autorisé |
| Clone GitHub / CSP | Hors sujet | Hors sujet |

---

## 8. Synthèse

| Question | Réponse |
|---|---|
| Cause exacte ? | JAR fat écrasé par JAR thin + repackage échoué sous JVM active → `NoClassDefFoundError` Hibernate |
| Migrations / schéma ? | **Non** |
| Bug applicatif endpoints ? | **Non** (code OK sur clone / classpath sain) |
| Correctif minimal ? | **Restart 8081** après rebuild (ou lancement) d’un **JAR fat** intact |
| Réparation faite ? | **Non — en attente d’autorisation** |

Preuves annexes : `docs/evidence/demo-500/summary.txt`, logs `docs/deploy-prep/vague0-restart-8081.log`, `tmp/p0-build.log`.
