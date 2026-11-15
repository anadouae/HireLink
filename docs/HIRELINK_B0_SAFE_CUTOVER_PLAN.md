# HireLink — Plan de cutover sûr B0 (backend)

**Date :** 2026-11-15
**Statut :** **PRÉPARATION TERMINÉE** — déploiement 8081 **non autorisé / non exécuté**
**Objectif :** déployer le fat JAR B0 sur la démo **uniquement après accord explicite**, sans récidive d’arrêt accidentel.

---

## 1. RCA — pourquoi Maven / cleanup a tué 8081

### Chronologie

1. Un `mvn test` B0 est resté bloqué longtemps (shell agent).
2. Un script de nettoyage a listé les `java.exe` et a appliqué `Stop-Process -Force` sur ceux dont la **ligne de commande** matchait un motif trop large.
3. Motif fautif (essentiel) :
   `CommandLine -match 'surefire|ExternalOffer|hirelink'`
   → le JAR démo s’appelle **`hirelink-demo-8081.jar`** et son chemin contient **`HireLink`** / **`hirelink`** → **match positif** → PID démo tué.
4. Conséquence : `8081` DOWN ; restauration manuelle depuis le fat JAR démo (JDK 23).

### Cause racine

| Élément | Détail |
|---------|--------|
| Erreur | Filtre d’arrêt **par sous-chaîne générique** (`hirelink`, parfois `maven`) |
| Cible collatérale | Processus démo `java -jar …/hirelink-demo-8081.jar` |
| Ce qui n’était **pas** la cause | Le build Maven lui-même ; le fat JAR démo ; MySQL ; le port 5500 |

### Règle anti-récidive

**Interdit :** `Stop-Process` / kill sur tous les `java.exe` / `mvn`, ou filtre `hirelink|java|maven|surefire` seul.
**Autorisé :** arrêt d’un **PID précis** ou d’un **marqueur unique** d’instance isolée (`b0-fatjar-smoke`, `18084`, …) avec **refus** si le process écoute `8081`/`5500` ou charge `hirelink-demo-8081.jar`.

Implémentation : `scripts/hirelink-process-guard.ps1`
(`Get-ProtectedPids`, `Stop-HireLinkMarkedProcesses`, `Assert-DemoStillUp`).

---

## 2. Isolation stricte des processus de test

| Mécanisme | Emplacement | Rôle |
|-----------|-------------|------|
| Garde-fou process | `scripts/hirelink-process-guard.ps1` | Ports 8081/5500 + JAR `demo-runtime` = **protégés** |
| Build isolé | `scripts/build-b0-fat-jar.ps1` | `mvn -DbuildDirectory=build-iso/b0-fatjar/maven-target` — **hors** `demo-runtime/` |
| Smoke fat JAR | `scripts/smoke-b0-fat-jar.ps1` | Port **18084**, DB **`hirelink_b0_fatjar_smoke`**, marqueur `b0-fatjar-smoke` |
| Props smoke | `build-iso/b0-fatjar/application-b0-fatjar-smoke.properties` | Connecteurs off, seed off, JWT de test |

### Checklist agent / opérateur

1. Avant tout stop : `Assert-DemoStillUp` + noter PID `netstat :8081`.
2. Ne jamais matcher `hirelink` seul dans un kill.
3. Arrêter une instance iso uniquement via `Stop-HireLinkMarkedProcesses -Marker '<unique>'`.
4. Vérifier après : `8081` UP, `offers=123`, SHA JAR démo inchangé.

---

## 3. Artefact candidat B0 (vérifié)

| | Démo actuelle (ne pas écraser) | Candidat B0 |
|--|--------------------------------|-------------|
| Chemin | `docs/deploy-prep/demo-runtime/hirelink-demo-8081.jar` | `docs/deploy-prep/b0-candidate-runtime/hirelink-b0-backend.jar` |
| SHA-256 | `C5722732E351C1E0636A48A41D1AAADD5A30F8355D14B088B6DAEDB1C71B7A8E` | `F0197F1AF3D798B78E13A624CB0C2F30EC5181F61F72EC48E42F06F9B9AD8972` |
| Taille | ~71 MB (fat) | 71 083 488 octets (fat) |
| Contenu B0 | Ancien bytecode | `BOOT-INF/classes/.../ExternalOfferListability.class` présent |
| Build dir Maven | — | `build-iso/b0-fatjar/maven-target/` |

Métadonnées : `docs/deploy-prep/b0-candidate-runtime/BUILD_META.txt`
Preuve structure : `docs/deploy-prep/b0-candidate-runtime/jar-contents-check.txt`

---

## 4. Preuve smoke du **JAR exact** (pas `spring-boot:run`)

Exécuté : `scripts/smoke-b0-fat-jar.ps1`

| Contrôle | Résultat |
|----------|----------|
| `java -jar hirelink-b0-backend.jar` + props smoke | UP sur **18084** |
| DB | `hirelink_b0_fatjar_smoke` (pas `hirelink`) |
| Seed `MAROC_EMPLOI` + `REMOTE_OK` | `/external-offers` → **1** offre `Autorise Remote` / `REMOTE_OK` ; Maroc **exclu** |
| Arrêt | Marqueur `b0-fatjar-smoke` uniquement ; **18084** down |
| Démo pendant/après | **8081 UP**, offers **123**, SHA démo inchangé |

Preuves : `docs/evidence/b0-safe-cutover/fatjar-smoke-result.txt`, `smoke.log`, `fatjar-18084.log`.

---

## 5. Plan de déploiement 8081 (à exécuter **seulement** après autorisation)

### 5.1 Prérequis (T0)

- [ ] Autorisation écrite explicite « cutover B0 sur 8081 ».
- [ ] `Assert-DemoStillUp` ; noter PID 8081 et `COUNT(job_offers)=123`.
- [ ] SHA démo actuel = `C5722732…7A8E` (ou valeur mesurée T0).
- [ ] Candidat présent : `hirelink-b0-backend.jar` SHA `F0197F1A…8972`.
- [ ] Copie de sauvegarde du JAR démo courant :
  `docs/deploy-prep/demo-runtime/hirelink-demo-8081.jar.bak-pre-b0-<timestamp>`
- [ ] JDK 23 : `C:\Program Files\Java\jdk-23\bin\java.exe`.
- [ ] Config runtime existante : `backend/application-cutover-runtime.properties` (**ne pas modifier secrets**).
- [ ] Connecteurs restent `enabled=false` / `startup-sync=false` dans cette config.
- [ ] Fenêtre courte ; FE 5500 laissé tourner (pas de rebuild FE requis pour B0 API).

### 5.2 Arrêt contrôlé (T1)

1. Noter PID : `netstat -ano | findstr :8081`.
2. Arrêter **uniquement ce PID** : `Stop-Process -Id <PID_8081> -Force`.
3. **Interdit** : kill global java/maven ; filtre `hirelink` ; toucher 5500.
4. Confirmer port libre : plus de `LISTENING` sur 8081.

### 5.3 Basculer l’artefact (T2)

Option A (recommandée — historique clair) :

```text
copy hirelink-b0-backend.jar → demo-runtime/hirelink-demo-8081.jar
(en ayant sauvegardé l’ancien en .bak-pre-b0-*)
```

Option B : démarrer directement le candidat sous un nom distinct, puis mettre à jour `start-command.txt`.

Démarrage :

```text
"%JAVA23%\java.exe" -jar docs/deploy-prep/demo-runtime/hirelink-demo-8081.jar
  --spring.config.additional-location=file:backend/application-cutover-runtime.properties
```

Working directory : `backend/` (comme aujourd’hui).

### 5.4 Vérifications post-démarrage (T3) — GO / NO-GO

| # | Test | Critère GO |
|---|------|------------|
| 1 | `GET /api/v1/health` | `UP` |
| 2 | `COUNT(*) hirelink.job_offers` | **123** (aucune migration / seed) |
| 3 | `GET /catalog/offers?country=MA&size=1` | `totalElements` ≈ **107** (ordre de grandeur inchangé) |
| 4 | `GET /external-offers` | **0** source `MAROC_EMPLOI` si présentes en DB ; pas de régression internes |
| 5 | Landing FE 5500 (catalogue) | cartes internes OK |
| 6 | Connecteurs | toujours disabled (admin status / props) |
| 7 | SHA du JAR démarré | = candidat B0 |

Si un critère critique (1–3, 6) échoue → **rollback immédiat** (§5.5).

### 5.5 Retour arrière (T4)

1. Stop **uniquement** le PID 8081 du nouveau process.
2. Restaurer `hirelink-demo-8081.jar` depuis `.bak-pre-b0-*`.
3. Relancer avec la **même** `application-cutover-runtime.properties`.
4. Vérifier health UP + offers=123 + catalog≈107.
5. Consulter logs `demo-8081*.log` ; ne pas toucher `hirelink` en SQL.

Durée cible rollback : **&lt; 5 min**.

---

## 6. Hors scope (rappel)

- Pas de migration schema sur `hirelink`.
- Pas d’activation Jooble / ATS / Maroc / RemoteOK.
- Pas de commit / push.
- Pas de modification des secrets.
- FE fail-closed B0 déjà en place ; ce cutover ferme le trou API legacy.

---

## 7. Décision demandée

La préparation est **complète** :

- RCA documentée ;
- garde-fou process livré ;
- fat JAR B0 construit et smoké sur **18084** ;
- plan GO / NO-GO / rollback rédigé.

**Aucun redémarrage 8081 n’a été fait dans cette étape.**

→ En attente de ton autorisation explicite pour exécuter le cutover §5.
