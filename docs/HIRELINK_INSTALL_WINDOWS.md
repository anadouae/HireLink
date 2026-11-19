# HireLink — Guide d’installation Windows (professeur / clone GitHub)

**Objectif :** cloner le dépôt, démarrer l’API et le frontend, tester candidat / recruteur / admin **sans** fichiers secrets de l’auteur.

**Prérequis machine :** Windows 10/11, droits d’installation logicielle, port `3306` / `8081` / `5500` libres.

---

## 1. Logiciels à installer

| Outil | Version | Vérification |
|---|---|---|
| JDK | **21** ou plus | `java -version` |
| Apache Maven | 3.9+ | `mvn -version` |
| MySQL ou MariaDB | 8.x / 10.4+ (XAMPP OK) | service sur `3306` |
| Python | 3.x | `python --version` |
| Navigateur | Edge ou Chrome | — |

Git : [https://git-scm.com](https://git-scm.com).

---

## 2. Cloner le projet

```bat
cd %USERPROFILE%\Desktop
git clone https://github.com/anadouae/HireLink.git
cd HireLink
```

Ne copiez **pas** de dump SQL depuis une autre machine.

---

## 3. Base de données

1. Démarrez MySQL/MariaDB (XAMPP → Start MySQL).
2. Compte attendu par défaut : utilisateur `root`, **mot de passe vide**.
3. Si votre `root` a un mot de passe :
   - Créez `backend\application-local.properties` (ce fichier est **gitignored**) :

```properties
spring.datasource.password=VOTRE_MOT_DE_PASSE
```

4. La base `hirelink` est créée automatiquement au premier démarrage (`createDatabaseIfNotExist=true`).
5. **Option schéma explicite** (recommandé pour coller à la prod) :

```powershell
cd database
.\apply-migrations.ps1 -Database hirelink -Fresh
```

Cela applique **V000** puis **V001…V006** (catalogue, ATS boards, qualité URL, factures candidat).  
Sans `-Fresh`, le script réapplique seulement V001…V006 de façon idempotente (pas de DROP).

---

## 4. Secrets (optionnels pour la démo scolaire)

| Besoin | Action |
|---|---|
| Démo locale minimale | **Rien** — JWT auto-généré dans `backend/data/jwt.key` |
| E-mails SMTP | Variables `HIRELINK_MAIL_*` ou `application-local.properties` (voir GUIDE §5.1) |
| IA cloud | `HIRELINK_GEMINI_API_KEY` / Groq / Mistral — sinon repli local |
| Google Sign-In | `HIRELINK_GOOGLE_CLIENT_ID` |

**Ne committez jamais** `application-local.properties` ni `backend/data/`.

---

## 5. Lancer le backend (port 8081)

Double-clic ou :

```bat
scripts\LANCER-BACKEND.bat
```

Attendre le log : `Started HireLinkApplication`. Par défaut, le backend utilise `hirelink_test`
et `ddl-auto=validate`; aucun seed, bootstrap ou import planifié ne s’exécute au démarrage.

Contrôle : ouvrir [http://127.0.0.1:8081/api/v1/health](http://127.0.0.1:8081/api/v1/health) → `status: UP`.
Pour voir la base associée aux connexions MySQL actives :

```powershell
& 'C:\xampp\mysql\bin\mysql.exe' -u root -h 127.0.0.1 -P 3306 -e "SELECT ID, USER, DB, HOST, COMMAND FROM information_schema.PROCESSLIST WHERE DB IS NOT NULL;"
```

---

## 6. Lancer le frontend (port 5500)

Dans un **second** terminal :

```bat
scripts\LANCER-FRONTEND.bat
```

(équivalent : `py -m http.server 5500` lancé depuis le dossier `frontend`)

Ouvrir [http://127.0.0.1:5500/](http://127.0.0.1:5500/) (pas `file://`).

---

## 7. Comptes de démonstration (créés au premier seed)

| E-mail | Mot de passe | Rôle |
|---|---|---|
| `candidat@hirelink.ma` | `Candidat123!` | Candidat |
| `recruteur@hirelink.ma` | `Recruteur123!` | Recruteur (membre) |
| `n.bennani@atlasdigital.ma` | `Recruteur123!` | **Owner** (facturation) |
| `admin@hirelink.ma` | `Admin123!` | Administrateur |

Détail : `docs/GUIDE_INSTALLATION_ET_TEST.md`.

---

## 8. Catalogue d’offres (Phase 3)

Sur une **base neuve**, le seed crée les offres internes puis **initialise automatiquement** `catalog_listings` si la projection est vide. Aucune action admin n’est requise.

Les offres seedées sont des **offres internes HireLink**. Les connecteurs externes restent **OFF** (pas d’import fixture MAROC / RemoteOK / ATS).

Un second démarrage **ne reconstruit pas** le catalogue s’il est déjà peuplé.

---

## 9. Arrêt

- Fenêtre backend : `Ctrl+C`
- Fenêtre frontend : `Ctrl+C`

---

## 10. Dépannage rapide

| Symptôme | Cause fréquente | Remède |
|---|---|---|
| `Access denied for user 'root'` | Mot de passe MySQL | `application-local.properties` |
| `Port 8081 was already in use` | Ancien Java | `netstat -ano \| findstr :8081` puis fin de processus |
| Page blanche / Failed to fetch | Mauvais hôte ou CSP | Utiliser `127.0.0.1:5500` et API `127.0.0.1:8081` |
| `403` devis billing en recruteur | Non-OWNER | Utiliser `n.bennani@atlasdigital.ma` |
| Grille offres vide (catalogue) | Seed non terminé / mauvais API | Attendre fin seed ; vérifier `GET /api/v1/catalog/offers` |

Guide long : `docs/GUIDE_INSTALLATION_ET_TEST.md`.
