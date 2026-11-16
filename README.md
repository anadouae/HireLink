# HireLink

**Plateforme bilatérale de gestion du recrutement (ATS)** — ENSA Tétouan · Frameworks Technologie Web · Douae El Assal

---

## 1. Présentation

HireLink relie **candidats** et **organisations recruteuses** autour d’un ATS : publication d’offres, candidatures avec CV, pipeline d’états contrôlés côté serveur, entretiens, abonnements simulés et espace administrateur. Une vitrine d’offres externes (sources autorisées) complète le catalogue ; ces annonces s’ouvrent sur le site d’origine et **ne créent pas** de candidature HireLink.

---

## 2. Fonctionnalités principales

- Authentification (mot de passe, CAPTCHA, OTP, MFA TOTP optionnelle, passkeys optionnelles)
- Profil candidat, CV, candidatures, suivi SLA
- Offres, pipeline Kanban, refus motivés (catalogue), entretiens, scorecards
- Organisations multi-membres (Owner / Recruteur / Hiring Manager)
- Cinq formules catalogue : **Gratuit / Performance** (candidat) · **Essentiel / Professionnel / Entreprise** (recruteur)
- Crédits IA plafonnés ; paiement **simulé**
- Administration (ICE, comptes, plans, e-mails journalisés)

---

## 3. Architecture technique

```
Frontend (HTML/CSS/JS)  :5500
        │  HTTP / JSON
        ▼
API Spring Boot /api/v1  :8081
        │
Controller → Service → Repository → MySQL
```

| Couche | Techno |
|--------|--------|
| Backend | Spring Boot 3.3 · Java 21 · Maven · JWT + sessions · JPA |
| Frontend | Pages HTML/CSS/JS (sans framework SPA) |
| Base | MySQL 8+ (utf8mb4) |
| Tests | JUnit · Playwright (`e2e/`) |

---

## 4. Prérequis

| Outil | Version minimale |
|-------|------------------|
| JDK | 21+ |
| Maven | 3.9+ |
| MySQL | 8.0+ |
| Navigateur | Chrome, Edge ou Firefox |
| Node.js | 18+ (uniquement pour `npx serve` du frontend et les tests Playwright) |

Vérification Windows : `scripts\VERIFY-PREREQS.bat`

---

## 5. Installation pas à pas

1. Obtenir le dépôt (clone Git **après** autorisation, ou archive fournie).
2. `scripts\VERIFY-PREREQS.bat`
3. Créer une base **vierge** (le script refuse d’écraser une base existante) :
   ```bat
   set HIRELINK_DB_NAME=hirelink_demo
   scripts\INSTALL-DB.bat
   ```
4. Copier `backend\src\main\resources\application-local.properties.example`
   vers `backend\application-local.properties`
   et pointer JDBC vers la base créée (utilisateur / mot de passe locaux).
5. (Optionnel) migrations SQL : voir `database/migrations/README.md` et `docs/MIGRATIONS.md`.
   En démo locale, Hibernate `ddl-auto=update` crée le schéma au premier démarrage.
6. Lancer backend puis frontend (sections 7).

Guide court vérifié : [`docs/DEMARRER.md`](docs/DEMARRER.md) (**~8–15 min** la première fois, pas une promesse « 5 minutes »).

---

## 6. Configuration

Modèles **sans secrets** :

- `backend/src/main/resources/application-local.properties.example`
- `.env.example`

| Sujet | Démo sans clé externe |
|-------|------------------------|
| Base de données | JDBC local dédié (`hirelink_demo`, …) |
| JWT | Généré automatiquement dans `backend/data/jwt.key` |
| SMTP | Vide → e-mails **journalisés** (pas d’envoi réel) |
| IA / LLM | Sans clé → **repli local** explicite |
| CAPTCHA | Défis locaux (question / SVG) |
| URL FE/BE | `5500` / `8081` (surchargeables via scripts) |
| Paiement | **Simulé** — aucune carte bancaire |
| MFA admin | Procédure : [`docs/ADMIN_MFA_DEMO.md`](docs/ADMIN_MFA_DEMO.md) |

Ne jamais committer `application-local.properties`, `.env`, `jwt.key`.

---

## 7. Démarrage

```bat
scripts\LANCER-BACKEND.bat
scripts\LANCER-FRONTEND.bat
```

Variables utiles : `HIRELINK_PORT`, `HIRELINK_FE_ORIGIN`, `HIRELINK_FE_PORT`, `HIRELINK_DB_*`.

Health : `http://127.0.0.1:8081/api/v1/health`
App : `http://127.0.0.1:5500`

Lanceur historique (si présent) : `LANCER-HIRELINK-DEMO.bat`.

---

## 8. Comptes de démonstration

| Email | Mot de passe | Rôle |
|-------|--------------|------|
| `admin@hirelink.ma` | `Admin123!` | Administrateur (MFA activable manuellement) |
| `candidat@hirelink.ma` | `Candidat123!` | Candidat |
| `s.elamrani@gmail.com` | `Candidat123!` | Candidat |
| `a.tazi@gmail.com` | `Candidat123!` | Candidat |
| `n.bennani@atlasdigital.ma` | `Recruteur123!` | Owner · Atlas Digital |
| `k.benali@atlasdigital.ma` | `Recruteur123!` | Recruteur · Atlas Digital |
| `m.alaoui@atlasdigital.ma` | `Recruteur123!` | Hiring Manager · Atlas Digital |
| `recruteur@hirelink.ma` | `Recruteur123!` | Recruteur · Atlas Digital |

Deux organisations seed (Atlas Digital + seconde org) ; offres, candidatures, entretiens et abonnements fictifs. **Données anonymisées / pédagogiques** — pas un dump de production.

---

## 9. Parcours de test recommandés

1. **Candidat** : connexion → offres → candidature + CV → suivi.
2. **Recruteur / Owner** : pipeline → transition d’étape → entretien → lecture CV.
3. **Administrateur** : entreprises ICE → comptes → consultation des e-mails journalisés.
4. **Billing** : checkout d’une des cinq formules → facture simulée.
5. **Négatif** : autre candidat ne lit pas le CV d’une candidature étrangère (403/404).

---

## 10. Paiement simulé

Le checkout crée des enregistrements locaux (abonnement / facture). **Aucun débit bancaire.** Les libellés landing correspondent aux cinq formules catalogue ci-dessus.

---

## 11. Limites IA / SMTP

- **Sans SMTP** : messages enregistrés / visibles en admin — **ne pas** présenter comme « e-mail envoyé ».
- **Sans clé LLM** : assistant en repli local — **ne pas** présenter comme analyse IA fournisseur.
- Crédits IA : réserve / règlement selon le palier ; concurrence couverte par tests d’intégration.

---

## 12. Résolution des problèmes courants

| Symptôme | Piste |
|----------|--------|
| Port déjà utilisé | Changer `HIRELINK_PORT` / `HIRELINK_FE_PORT` — les scripts **n’arrêtent pas** un processus inconnu |
| Base existe déjà | `INSTALL-DB.bat` refuse — choisir un autre `HIRELINK_DB_NAME` |
| CAPTCHA / login | Rafraîchir le défi ; vérifier l’horloge système |
| Frontend 404 API | CORS / `HIRELINK_FE_ORIGIN` aligné sur l’URL réelle |
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
CI GitHub Actions : `.github/workflows/ci.yml` — **validée localement sur les commandes Maven** ; le succès sur GitHub n’est déclaré qu’après une exécution réelle (push autorisé séparément).

---

## 14. Arrêt

```bat
scripts\ARRETER-HIRELINK.bat
```

N’agit que sur les ports HireLink configurés (backend / `serve`).

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

---

## Licence / contexte

Projet pédagogique ENSA Tétouan. Limites de sécurité : `docs/SECURITE.md`. Aucun cutover / production sans GO explicite du responsable.
