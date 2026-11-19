# HireLink

**Plateforme bilatérale de gestion du recrutement (ATS)** — ENSA Tétouan · Frameworks Technologie Web · Douae El Assal

---

## 1. Présentation

HireLink relie **candidats** et **organisations recruteuses** autour d’un ATS : publication d’offres, candidatures avec CV, pipeline d’états contrôlés côté serveur, entretiens, abonnements (paiement de démonstration) et espace d’administration.
Côté candidat, la recherche réunit les offres publiées sur HireLink et une **recherche web en direct** sur des sites d’emploi publics (Remotive, Arbeitnow, RemoteOK, Himalayas, Jobicy, Adzuna, We Work Remotely). Ces annonces s’ouvrent sur le site d’origine et **ne créent pas** de candidature HireLink.

---

## 2. Fonctionnalités principales

- Authentification : mot de passe, code de vérification par e-mail, double authentification (TOTP) et clés d’accès (passkeys) facultatives, connexion Google facultative
- Candidat : profil, CV (PDF, TXT, MD ; images PNG, JPG ou WEBP lues par OCR pour le profil ; 8 Mo maximum ; pas de Word), candidatures, suivi, entreprises suivies, outils IA du profil
- Recruteur : offres, pipeline Kanban, refus motivés (catalogue de motifs), agenda et entretiens, équipe et invitations
- Organisations multi-membres (Owner / Recruteur / Hiring Manager), badge « entreprise vérifiée » attribué automatiquement à partir de l’e-mail professionnel (domaine de l’entreprise)
- Cinq formules : **Gratuit / Performance** (candidat) · **Essentiel / Professionnel / Entreprise** (recruteur)
- Crédits IA plafonnés selon la formule ; paiement de démonstration (aucun débit réel)
- Administration : tableau de bord (revenus, formules, inscriptions), entreprises (désactivation réversible, retrait), comptes, modération, formules et factures, journal d’activité, e-mails

---

## 3. Architecture technique

```
Frontend (HTML/CSS/JS)  :5500
        │  HTTP / JSON
        ▼
API Spring Boot /api/v1  :8081
        │
Controller → Service → Repository → MariaDB / MySQL
```

| Couche | Techno |
|--------|--------|
| Backend | Spring Boot 3.3 · Java 21 · Maven · JWT + sessions · JPA |
| Frontend | Pages HTML/CSS/JS (sans framework SPA), ordinateur uniquement |
| Base | MariaDB 10.4+ (XAMPP) ou MySQL 8 (utf8mb4) |
| Tests | JUnit · Playwright (`e2e/`) |

---

## 4. Prérequis

| Outil | Version minimale |
|-------|------------------|
| JDK | 21+ |
| Maven | 3.9+ |
| Base de données | XAMPP (MariaDB 10.4+) ou MySQL 8 |
| Python | 3.x (pour servir le frontend : `py -m http.server 5500`) |
| Navigateur | Chrome, Edge ou Firefox, sur ordinateur |
| Node.js | 18+ (uniquement pour les tests Playwright) |

Vérification Windows : `scripts\VERIFY-PREREQS.bat`

---

## 5. Installation pas à pas

1. Récupérer le dépôt (clone Git ou archive fournie).
2. `scripts\VERIFY-PREREQS.bat`
3. Démarrer **MySQL** dans le panneau XAMPP (phpMyAdmin permet ensuite de consulter la base).
4. Créer une fois la base de développement `hirelink_test`, puis appliquer les migrations SQL
   dans l’ordre (le backend ne crée plus la base ni le schéma au démarrage) :
   ```bat
   mysql -u root -e "CREATE DATABASE IF NOT EXISTS hirelink_test CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
   mysql -u root hirelink_test < database\migrations\V000__schema_baseline_mariadb.sql
   ```
   Appliquer ensuite les migrations nécessaires selon `database/migrations/README.md`.
   L’URL par défaut vise `hirelink_test`; `ddl-auto=validate` vérifie le schéma sans le modifier.
5. Les paramètres locaux dans `backend\application-local.properties` peuvent contenir SMTP/IA,
   mais ne doivent pas rediriger la datasource vers `hirelink`.

La vraie base locale `hirelink` est refusée sans le profil `realdb`, une URL explicite
`HIRELINK_REALDB_URL` et `HIRELINK_REALDB_CONFIRMATION=I_CONFIRM_HIRELINK_REALDB`.
Ne l’utilisez qu’après autorisation et sauvegarde vérifiée.
6. Lancer le backend puis le frontend (section 7).

Guide court : [`docs/DEMARRER.md`](docs/DEMARRER.md) (environ 10 à 15 minutes la première fois).

---

## 6. Configuration

Modèles **sans secrets** :

- `backend/src/main/resources/application-local.properties.example`
- `.env.example`

| Sujet | Démo sans clé externe |
|-------|------------------------|
| Base de données | `hirelink_test` par défaut, schéma validé sans modification |
| JWT | Clé générée automatiquement dans `data/jwt.key`, relatif au dossier de lancement (`backend\data\jwt.key` avec `scripts\LANCER-BACKEND.bat`) |
| SMTP | Vide → e-mails **journalisés** (pas d’envoi réel), visibles dans l’administration |
| IA / LLM | Sans clé → repli local |
| URL FE/BE | `5500` / `8081` (modifiables par variables d’environnement) |
| Paiement | Démonstration — aucune carte bancaire débitée |
| MFA admin | Procédure : [`docs/ADMIN_MFA_DEMO.md`](docs/ADMIN_MFA_DEMO.md) |

Ne jamais committer `application-local.properties`, `.env`, `jwt.key`.

---

## 7. Démarrage

Terminal 1 (backend) puis terminal 2 (frontend) :

```bat
scripts\LANCER-BACKEND.bat
scripts\LANCER-FRONTEND.bat
```

`scripts\LANCER-FRONTEND.bat` revient à lancer `py -m http.server 5500` depuis le dossier `frontend`.

Variables utiles : `HIRELINK_PORT`, `HIRELINK_FE_ORIGIN`, `HIRELINK_FE_PORT`, `HIRELINK_DB_*`.

Santé de l’API : `http://127.0.0.1:8081/api/v1/health`
Application : `http://127.0.0.1:5500`

---

## 8. Comptes de démonstration

| Email | Mot de passe | Rôle |
|-------|--------------|------|
| `candidat@hirelink.ma` | `Candidat123!` | Candidat |
| `s.elamrani@gmail.com` | `Candidat123!` | Candidat |
| `a.tazi@gmail.com` | `Candidat123!` | Candidat |
| `n.bennani@atlasdigital.ma` | `Recruteur123!` | Owner · Atlas Digital |
| `k.benali@atlasdigital.ma` | `Recruteur123!` | Recruteur · Atlas Digital |
| `m.alaoui@atlasdigital.ma` | `Recruteur123!` | Hiring Manager · Atlas Digital |
| `recruteur@hirelink.ma` | `Recruteur123!` | Recruteur · Atlas Digital |

Le compte administrateur n’est pas publié ici : ses identifiants sont remis séparément.

Les données de démonstration comprennent Atlas Digital, une vingtaine de grandes entreprises marocaines et des entreprises fictives, avec leurs offres, candidatures, entretiens et abonnements. **Données fictives et pédagogiques** : ce n’est pas une copie de production.

---

## 9. Parcours de test recommandés

1. **Candidat** : connexion → offres → candidature + CV → suivi.
2. **Recruteur / Owner** : pipeline → changement d’étape → entretien → lecture du CV.
3. **Administrateur** : tableau de bord → entreprises → comptes → journal d’activité et e-mails.
4. **Abonnement** : choix d’une des cinq formules → facture.
5. **Négatif** : un candidat ne peut pas lire le CV d’une candidature qui n’est pas la sienne (403/404).

---

## 10. Paiement

Le paiement est une démonstration : il crée des enregistrements locaux (abonnement / facture). **Aucun débit bancaire.** Les libellés de la page d’accueil correspondent aux cinq formules ci-dessus.

---

## 11. Limites IA / SMTP

- **Sans SMTP** : messages enregistrés et visibles dans l’administration — **ne pas** les présenter comme « e-mail envoyé ».
- **Sans clé LLM** : analyse en repli local — **ne pas** la présenter comme une analyse du fournisseur IA.
- Crédits IA : réservation puis règlement selon la formule ; la concurrence est couverte par des tests d’intégration.

---

## 12. Résolution des problèmes courants

| Symptôme | Piste |
|----------|--------|
| Port déjà utilisé | `scripts\ARRETER-HIRELINK.bat`, ou changer `HIRELINK_PORT` / `HIRELINK_FE_PORT` |
| Base existe déjà | `INSTALL-DB.bat` refuse — choisir un autre `HIRELINK_DB_NAME` |
| Le frontend n’atteint pas l’API | CORS : `HIRELINK_FE_ORIGIN` doit correspondre à l’URL réelle du frontend |
| MFA admin | Voir `docs/ADMIN_MFA_DEMO.md` |

---

## 13. Exécution des tests

```bat
cd backend
mvn -B test
```

```bat
cd e2e
npm install
npx playwright test
```

Prérequis E2E : backend + frontend joignables (`HL_API`, `HL_FE`).
Intégration continue : `.github/workflows/ci.yml`.

---

## 14. Arrêt

```bat
scripts\ARRETER-HIRELINK.bat
```

Le script lit avec `netstat` les processus à l’écoute sur les ports HireLink (8081 et 5500 par défaut, ou `HIRELINK_PORT` / `HIRELINK_FE_PORT`) et n’arrête que ceux-là.

---

## Docs utiles

| Document | Fichier |
|----------|---------|
| Démarrage vérifié | [`docs/DEMARRER.md`](docs/DEMARRER.md) |
| MFA admin démo | [`docs/ADMIN_MFA_DEMO.md`](docs/ADMIN_MFA_DEMO.md) |
| Guide installation Windows (détail) | `docs/HIRELINK_INSTALL_WINDOWS.md` |
| Guide + scénarios | `docs/GUIDE_INSTALLATION_ET_TEST.md` |
| Sécurité | `docs/SECURITE.md` |
| API | `docs/API.md` |
| Migrations | `docs/MIGRATIONS.md` |
| Fonctionnement de l’administration | `docs/FONCTIONNEMENT_ADMIN.md` |

---

## Licence / contexte

Projet pédagogique ENSA Tétouan. Limites de sécurité : `docs/SECURITE.md`.
