# HireLink

**Plateforme bilatérale de gestion du recrutement (ATS)** — ENSA Tétouan · Frameworks Technologie Web · Douae El Assal

---

## Description de l’application

HireLink n’est pas un simple jobboard : c’est un **ATS (Applicant Tracking System)** qui pilote le cycle de sélection **après** le dépôt de candidature.

L’application relie deux publics :

- le **candidat** (étudiant, jeune diplômé, salarié en mobilité) qui trouve une offre, postule et suit son dossier ;
- l’**organisation recruteuse** (PME / ETI, multi-sièges) qui publie des offres, fait avancer un pipeline d’étapes et décide avec des règles claires.

### Problème traité

Sans outil structuré, le suivi dépend de la rigueur manuelle du recruteur : pas de délai garanti, refus souvent non motivés, candidatures « fantômes ». HireLink impose un **pipeline à états contrôlés côté serveur**, un **SLA de réponse visible**, un **motif de refus obligatoire** (catalogue), et un **matching local** pour aider au tri — **sans jamais automatiser la décision finale**.

### Utilisateurs

| Qui | Rôle dans HireLink |
|-----|--------------------|
| Candidat | Profil, CV, candidatures, suivi SLA |
| Personne RH (Owner / Recruteur) | Offres, pipeline, équipe, entretiens |
| Hiring Manager | Scorecard / entretien sur dossiers assignés |
| Administrateur | Vérification entreprise (ICE), supervision |

---

## Fonctionnalités

### Candidat
- Inscription / connexion sécurisée (mot de passe, OTP, Google, double authentification, passkeys, mot de passe oublié, page Sécurité)
- Gestion du profil, compétences / langues, dépôt de CV (OCR)
- Recherche et consultation des offres publiques
- Candidature guidée (compétences → CV → lettre de motivation)
- Suivi des candidatures : statut, SLA restant, historique des transitions
- Assistance IA bornée (préparation entretien, suggestions de profil) avec crédits
- Export des données personnelles (droit d’accès)

### Organisation (recruteur)
- Fiche entreprise et vérification ICE
- Création, publication et clôture d’offres (SLA par offre)
- Pipeline Kanban : soumise → présélection → shortlist → entretien → proposition → embauché (ou refus motivé / retrait)
- Transitions sécurisées côté serveur (checklist + règles d’état)
- Motif de rejet obligatoire choisi dans un **catalogue fermé**
- Planification d’entretiens (téléphone, visio, présentiel, asynchrone)
- Scorecard : critères par poste, notes multi-évaluateurs
- Historique immuable des changements d’étape
- Organisation multi-membres : Owner / Recruteur / Hiring Manager (invite / rôles)
- Assistant IA recruteur (grille d’entretien, brouillon de refus) + crédits partagés

### Nouveautés de la version intégrée

- **Recherche intelligente** partout (module `frontend/js/search.js` + `TextMatcher` côté serveur) : tolérante aux fautes, aux accents et aux synonymes, classée, avec surlignage et messages « aucun résultat ».
- **Compétences** : sélecteur avec saisie libre et référentiel catégorisé (offre et profil) ; le profil candidat range les compétences dans des boîtes colorées par catégorie (« Autres » pour les inconnues), visibles aussi des recruteurs.
- **Correspondance** : l'interrupteur « Offres qui correspondent à mon profil » filtre la liste et affiche un score.
- **Offres détaillées par type de contrat** (CDI, CDD, stage, PFE, PFA, stage d'été, alternance, freelance, temps partiel) : rémunération, durée, dates, convention de stage, avantages, mode de travail, niveau… avec validation en ligne. Lieux avec drapeau et autocomplétion (Maroc en premier).
- **Carte d'offre unique** (`css/offer-card.css`, `js/offer-card.js`) réutilisée sur l'accueil, les pages entreprise et les espaces candidat et recruteur.
- **Entretiens** : mode (sur site, visio, téléphone), nature (RH, technique, manager, final), fuseau horaire (Africa/Casablanca par défaut), participants, consignes, report et annulation motivés, confirmation du candidat, fichier `.ics`, rappel la veille.
- **E-mails transactionnels** (HTML + texte, français) par SMTP configurable (`HIRELINK_MAIL_*` ou `application-local.properties`), envoi asynchrone sans bloquer l'application, boîte d'envoi pour l'administrateur, préférence de désinscription pour les messages de suivi.
- **Notifications en temps réel** (SSE, ticket à usage unique de 30 s, reconnexion, secours par interrogation).
- **Étapes Proposition et Embauché** dans le pipeline, avec e-mails, notifications et célébration.
- **Page d'accueil animée** (`index.html`, `css/home.css`, `js/home-motion.js`, `js/landing-offers.js`, bibliothèques d'animation locales dans `js/vendor/`) : elle réutilise la carte d'offre commune (variante « vitrine »), la recherche tolérante, la rémunération et les drapeaux ; elle respecte « réduire les animations » et reste lisible sans JavaScript.
- **Mascotte** (`js/mascotte.js`, `css/mascotte.css`) : notifications, fenêtres de confirmation (avec motif obligatoire pour une annulation), états vides et célébrations (embauche, première offre publiée, entreprise vérifiée, profil complet). La célébration d'embauche montre l'équipe de recruteurs côté recruteur et la tenue du candidat côté candidat.
- **Logos d'entreprise** : tuile blanche uniforme (image entière, petite marge) sur les cartes, listes, page entreprise, menu recruteur et administration ; initiale de repli si le logo manque ou ne se charge pas. Fichiers PNG/JPEG/WEBP/GIF ou adresse `https://`.

Détails : [`docs/API.md`](docs/API.md) pour les routes et les champs, [`docs/GUIDE_INSTALLATION_ET_TEST.md`](docs/GUIDE_INSTALLATION_ET_TEST.md) §5.1 (SMTP) et §18 (scénarios).

**Base existante** : au démarrage, Hibernate (`ddl-auto=update`) ajoute les nouvelles colonnes et les étapes `OFFER` / `HIRED` de `applications.status` ; aucune commande SQL n'est nécessaire (voir le guide, §3, pour le cas particulier).

### Transversal
- **Matching local (F14)** : score Jaccard compétences / offre, gratuit, déterministe, sans API externe
- **Assistant IA (F15)** : rédaction via LLM optionnel (Gemini / Mistral) — jamais de décision automatique ; pas d’identité envoyée au fournisseur
- **Crédits IA (F16)** : plafonds selon palier (Essentiel / Performance / Accès / Premium)
- Notifications in-app
- Espace administration

### Règles métier clés
- Une candidature ne peut avancer que vers des états autorisés
- Isolation des données par organisation
- Refus impossible sans motif catalogue
- Le matching **ne rejette jamais** automatiquement
- Un crédit IA n’est débité qu’en cas de succès de la réponse LLM

---

## Séance 2 — statut

**Déjà réalisé.** Architecture Spring en couches + première fonctionnalité métier (offres) + pages frontend.

| Attendu | Emplacement |
|---------|-------------|
| Packages `controller` / `service` / `repository` / entity | `backend/.../controller`, `service`, `repository`, `model` |
| IoC / DI (Spring Beans) | constructeurs `@Service` / `@RestController` |
| Liste + formulaire + détail | `frontend/app/recruiter-offers.html`, wizard offre, espaces candidat |
| Flux Controller → Service → Repository | [`docs/architecture/flux_controller_service_repository.png`](docs/architecture/flux_controller_service_repository.png) |

Guide d’installation et de test (Windows, pas à pas, avec captures) : [`docs/GUIDE_INSTALLATION_ET_TEST.md`](docs/GUIDE_INSTALLATION_ET_TEST.md) · [`.docx`](docs/GUIDE_INSTALLATION_ET_TEST.docx)  
Cas d’utilisation UML : [`docs/Diagramme_Cas_Utilisation_HireLink.docx`](docs/Diagramme_Cas_Utilisation_HireLink.docx)

---

## Stack

- **Backend** : Spring Boot 3.3 · Java 21 · Maven · Spring Security JWT + sessions · JPA · MySQL / MariaDB  
- **Frontend** : HTML / CSS / JS sans framework (pages + wizards). Les trois espaces (candidat, recruteur, administrateur) partagent le même shell : `css/shell.css` + `js/app-shell.js` (menu, en-tête, notifications, thème clair/sombre, cache, états de chargement) ; `css/app.css` ne garde que les composants des pages candidat ; animations légères dans `css/motion.css`, désactivées avec `prefers-reduced-motion`.  
- **Port API** : `8081` · **Front** : `5500`

```
Frontend (5500)
    │  HTTP / JSON
    ▼
API REST /api/v1  (8081)
    │
    ▼
Controller → Service → Repository → MySQL (hirelink)
```

---

## Démarrage rapide (IntelliJ)

1. MySQL : `CREATE DATABASE hirelink CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;` (l’UTF-8 complet est requis : les notifications contiennent des « → » et des accents)
2. IntelliJ → **Open** → dossier `backend/` (pom.xml) → Run `HireLinkApplication`
3. Health : http://localhost:8081/api/v1/health  
4. Frontend :
   ```bash
   cd frontend
   python -m http.server 5500
   ```
5. App : http://localhost:5500/auth.html (aucun script `.bat` : tout se lance avec les commandes ci-dessus)

### Parcours par rôle

| Rôle | Redirection après connexion | Pages |
|------|-----------------------------|-------|
| Recruteur / Hiring Manager | `app/recruiter.html` | Tableau de bord · Pipeline · Candidats · Entretiens · Offres · Assistant IA · Notifications · Entreprise & équipe · Paramètres |
| Candidat | `app/candidate-dashboard.html` | Tableau de bord · Offres · Entreprises · Mes candidatures · Profil / CV · Paramètres |
| Administrateur | `app/admin.html` | Tableau de bord · Entreprises (vérification ICE, plans) · Plans et factures · Comptes · Motifs de refus |

Un utilisateur qui ouvre la page d’un autre rôle est renvoyé vers son propre espace ; un jeton expiré (401) ramène à la connexion puis à la page demandée.

### Liens entre les trois espaces

- Une candidature envoyée apparaît dans le **pipeline** de toute l’organisation et notifie l’auteur de l’offre **et les Owners**.
- Chaque changement d’étape (présélection, entretien, refus motivé…) notifie le **candidat** et est visible dans « Mes candidatures ».
- La décision de l’**administrateur** sur l’ICE met à jour l’organisation *et* tous ses membres (badge « vérifiée », droit de publier) et les prévient par notification.
- Les motifs proposés au recruteur pour un refus viennent du catalogue géré par l’administrateur (« Motifs de refus »).
- Une offre qui a des candidatures ne peut pas être supprimée (elle se dépublie) : l’historique des candidats est conservé.

Détails, scénarios par rôle et dépannage : [`docs/GUIDE_INSTALLATION_ET_TEST.md`](docs/GUIDE_INSTALLATION_ET_TEST.md)

### Comptes seed

| Email | Mot de passe | Rôle |
|-------|--------------|------|
| `admin@hirelink.ma` | `Admin123!` **+ code TOTP** (activation à la première connexion, voir le guide §14) | Admin |
| `recruteur@hirelink.ma` | `Recruteur123!` | Recruteur |
| `candidat@hirelink.ma` | `Candidat123!` | Candidat |
| `n.bennani@atlasdigital.ma` | `Recruteur123!` | Recruteur · Owner d’Atlas Digital |
| `k.benali@atlasdigital.ma` | `Recruteur123!` | Recruteur (Atlas Digital) |
| `m.alaoui@atlasdigital.ma` | `Recruteur123!` | Hiring Manager (Atlas Digital) |

`recruteur@hirelink.ma` est rattaché à l’organisation **Atlas Digital** (Owner) : il voit les mêmes offres et le même pipeline que Nadia Bennani.

> Adapter `spring.datasource.password` dans `backend/src/main/resources/application.properties` selon votre MySQL (souvent vide en local), de préférence via `application-local.properties` (ignoré par Git).
>
> Ces mots de passe « faciles » ne concernent que les comptes de démonstration. Tout mot de passe créé dans l’application doit faire 12 à 64 caractères et ne pas être courant.
> L’application démarre **sans aucune clé IA** ; les secrets (clé JWT, clés IA, Google) se règlent par variables d’environnement ou `application-local.properties` (modèles : `.env.example`, `application-local.properties.example`).

### Sécurité

Authentification par jeton court (15 min) + refresh opaque haché en cookie `HttpOnly` (rotation, détection de réutilisation, expiration par inactivité et durée absolue), liste et révocation des sessions, mots de passe BCrypt 12 avec politique (12–64 caractères, liste noire), verrouillage et limitation de débit, double authentification TOTP (obligatoire pour l’administrateur, avec codes de secours), passkeys WebAuthn optionnelles, ré-authentification pour les actions sensibles, réinitialisation du mot de passe, contrôle d’accès par rôle **et** par organisation, validation des entrées, téléversements vérifiés par signature, CSP et en-têtes de sécurité, CORS restreint, journal d’audit, export et effacement RGPD, profil `prod`. Détails, table OWASP/ASVS et limites : [`docs/SECURITE.md`](docs/SECURITE.md).

---

## Structure

```
HireLink/
├── backend/                 # Spring Boot
│   └── src/main/java/ma/ensat/hirelink/
│       ├── controller/      # API REST
│       ├── service/         # Métier + IoC
│       ├── repository/      # Spring Data JPA
│       ├── model/           # Entités JPA
│       ├── security/        # JWT
│       ├── dto/
│       └── config/
├── frontend/                # UI HTML/CSS/JS
├── database/                # schema SQL
└── docs/                    # CDC, guides, diagrammes UML
```

---

## Docs utiles

| Document | Fichier |
|----------|---------|
| Guide d’installation et de test (Windows) | `docs/GUIDE_INSTALLATION_ET_TEST.md` / `.docx` |
| Sécurité (OWASP Top 10 / ASVS niveau 2) | `docs/SECURITE.md` |
| API : offres, entretiens, notifications, e-mails | `docs/API.md` |
| Cas d’utilisation UML | `docs/Diagramme_Cas_Utilisation_HireLink.docx` |
| Accès BDD | `docs/ACCES_BDD.md` |
| Cahier des charges | `docs/cahier-des-charges/` |

---

## Licence / contexte

Projet pédagogique ENSA Tétouan. Un durcissement de sécurité (OWASP / ASVS) a été appliqué et documenté dans `docs/SECURITE.md` ; les limites connues y sont listées (limitation de débit en mémoire, tests sur MariaDB…) : relisez-les avant tout déploiement réel.
