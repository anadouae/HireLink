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
- Inscription / connexion sécurisée (mot de passe, OTP, Google)
- Gestion du profil, compétences / langues, dépôt de CV (OCR)
- Recherche et consultation des offres publiques
- Candidature guidée (compétences → CV → lettre de motivation)
- Suivi des candidatures : statut, SLA restant, historique des transitions
- Assistance IA bornée (préparation entretien, suggestions de profil) avec crédits
- Export des données personnelles (droit d’accès)

### Organisation (recruteur)
- Fiche entreprise et vérification ICE
- Création, publication et clôture d’offres (SLA par offre)
- Pipeline Kanban : soumise → présélection → shortlist → entretien → offre → décision
- Transitions sécurisées côté serveur (checklist + règles d’état)
- Motif de rejet obligatoire choisi dans un **catalogue fermé**
- Planification d’entretiens (téléphone, visio, présentiel, asynchrone)
- Scorecard : critères par poste, notes multi-évaluateurs
- Historique immuable des changements d’étape
- Organisation multi-membres : Owner / Recruteur / Hiring Manager (invite / rôles)
- Assistant IA recruteur (grille d’entretien, brouillon de refus) + crédits partagés

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

Guide IntelliJ : [`docs/GUIDE_DEMARRAGE_INTELLIJ.docx`](docs/GUIDE_DEMARRAGE_INTELLIJ.docx)  
Cas d’utilisation UML : [`docs/Diagramme_Cas_Utilisation_HireLink.docx`](docs/Diagramme_Cas_Utilisation_HireLink.docx)

---

## Stack

- **Backend** : Spring Boot 3.3 · Java 21 · Maven · Spring Security JWT · JPA · MySQL  
- **Frontend** : HTML / CSS / JS (pages + wizards)  
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

1. MySQL : `CREATE DATABASE hirelink CHARACTER SET utf8mb4;`
2. IntelliJ → **Open** → dossier `backend/` (pom.xml) → Run `HireLinkApplication`
3. Health : http://localhost:8081/api/v1/health  
4. Frontend :
   ```bash
   cd frontend
   python -m http.server 5500
   ```
5. App : http://localhost:5500/auth.html

Détails + dépannage : `docs/GUIDE_DEMARRAGE_INTELLIJ.md`

### Comptes seed

| Email | Mot de passe | Rôle |
|-------|--------------|------|
| `admin@hirelink.ma` | `Admin123!` | Admin |
| `recruteur@hirelink.ma` | `Recruteur123!` | Recruteur |
| `candidat@hirelink.ma` | `Candidat123!` | Candidat |

> Adapter `spring.datasource.password` dans `backend/src/main/resources/application.properties` selon votre MySQL (souvent vide en local).

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
| Guide IntelliJ | `docs/GUIDE_DEMARRAGE_INTELLIJ.docx` |
| Cas d’utilisation UML | `docs/Diagramme_Cas_Utilisation_HireLink.docx` |
| Accès BDD | `docs/ACCES_BDD.md` |
| Cahier des charges | `docs/cahier-des-charges/` |

---

## Licence / contexte

Projet pédagogique ENSA Tétouan — non destiné à la production sans durcissement sécurité.
