# RAPPORT — Séances 3 & 4 | Projet HireLink

**Étudiante :** Douae El Assal  
**Formation :** ENSA Tétouan — Frameworks Technologie Web (GI3)  
**Projet :** HireLink — ATS (Applicant Tracking System)  
**Stack :** Spring Boot 3 · Java · JPA/Hibernate · MySQL/MariaDB · Frontend HTML/JS · Postman  
**Base de données :** `hirelink` (localhost:3306)  
**API :** `http://localhost:8081/api/v1`  
**Frontend :** `http://localhost:5500`

---

## 1. Introduction

Ce rapport présente les livrables des **séances 3** (modélisation JPA/Hibernate) et **4** (CRUD + API REST), avec :
- le contexte et les objectifs ;
- les étapes de réalisation ;
- l’architecture et le modèle de données ;
- le détail des endpoints ;
- les interfaces frontend ;
- les tests Postman ;
- un bilan et les perspectives.

HireLink n’est pas un simple jobboard : c’est un **ATS** qui pilote le cycle de sélection après candidature (pipeline, SLA, motifs de refus, matching, scorecard).

---

## 2. Séance 3 — Modélisation des données avec JPA/Hibernate

### 2.1 Objectif pédagogique
Construire le modèle de données de l’application et le relier au backend : entities JPA, relations, persistance MySQL, formulaires alignés.

### 2.2 Compétences couvertes
JPA, Hibernate, ORM, mapping objet-relationnel, relations entre entités, persistance.

### 2.3 Étapes réalisées

| Étape | Action | Résultat |
|------|--------|----------|
| 1 | Identifier les entités métier | User, Organization, Membership, JobOffer, Application, Tag, RefusalReason, Notification, Scorecard, Matching, etc. |
| 2 | Définir les relations | `@ManyToOne`, `@OneToMany`, `@ManyToMany` |
| 3 | Annoter les classes Java | package `ma.ensat.hirelink.model` |
| 4 | Configurer Hibernate | `spring.jpa.hibernate.ddl-auto=update` |
| 5 | Vérifier en MySQL | tables visibles dans phpMyAdmin (base `hirelink`) |
| 6 | Adapter le frontend | formulaires offres, profil, pipeline |

### 2.4 Comment les tables sont créées (point important)

**Méthode utilisée (moderne) :** ORM JPA/Hibernate  
1. On écrit une classe Java `@Entity` (ex. `JobOffer`).  
2. Au démarrage, Spring Boot démarre le conteneur et Hibernate lit les entities.  
3. Grâce à `ddl-auto=update`, Hibernate crée ou met à jour les tables MySQL.  
4. phpMyAdmin sert à **contrôler** le résultat, pas à inventer le schéma.

**Ancienne méthode :** scripts `.sql` manuels (`CREATE TABLE...`).  
Ce n’est **pas** la source de vérité dans HireLink : la source de vérité est le **modèle Java**.

Fichier clé : `backend/src/main/resources/application.properties`

### 2.5 Entités et tables

| Entité Java | Table MySQL | Rôle |
|-------------|-------------|------|
| User | users | Comptes (CANDIDAT / RECRUTEUR / ADMIN) |
| Organization | organizations | Entreprise (ICE, badge auto/admin, logo, secteur, plan) |
| Membership | memberships | Lien User↔Org + rôle (OWNER, RECRUTEUR, HM) |
| JobOffer | job_offers | Offres d’emploi |
| Application | applications | Candidatures + statut pipeline |
| ApplicationStatusHistory | application_status_history | Historique des transitions |
| Tag | tags | Tags d’offre |
| (join) | job_offer_tags | Table de jointure ManyToMany |
| RefusalReason | refusal_reason_catalog | Motifs de refus (catalogue) |
| InAppNotification | notifications | Notifications in-app |
| MatchingAnalysis | matching_analyses | Scores matching |
| ScorecardCriterion | scorecard_criteria | Critères d’évaluation |
| ScorecardEntry | scorecard_entries | Notes multi-évaluateurs |
| ConsentRecord | consent_records | Consentements |
| EmailOtp | email_otps | Codes OTP |
| AiUsageLog | ai_usage_logs | Conso IA |

**Enums :** `Role`, `OrgRole`, `ApplicationStatus`, `PlanTier`

### 2.6 Relations JPA (mapping)

| Relation | Cardinalité | Annotations |
|----------|-------------|-------------|
| User → JobOffer (recruteur) | 1 — * | `@OneToMany` / `@ManyToOne` |
| User → Application (candidat) | 1 — * | `@OneToMany` / `@ManyToOne` |
| JobOffer → Application | 1 — * | `@OneToMany` / `@ManyToOne` |
| User ↔ Organization | * — * | via **Membership** (association avec attributs) |
| Organization → User (owner) | * — 0..1 | `@ManyToOne` |
| JobOffer ↔ Tag | * — * | `@ManyToMany` + `job_offer_tags` |

### 2.7 Livrables Séance 3

| Livrable | Emplacement | Statut |
|----------|-------------|--------|
| Diagramme de classes | `docs/diagrammes/diagramme_classes_hirelink_FINAL.png` (+ `.puml`) | OK |
| Entités JPA | `backend/.../model/` | OK |
| Mapping des relations | annotations dans les entities | OK |
| Base MySQL fonctionnelle | phpMyAdmin → `hirelink` | OK (à montrer) |
| Formulaires/pages alignés | `frontend/app/*` | OK |
| Doc récap | `docs/SEANCE3_ET_SEANCE4.md` | OK |

---

## 3. Séance 4 — CRUD complet et API REST

### 3.1 Objectif pédagogique
Développer les CRUD backend, exposer les données en API REST, préparer les interfaces frontend, tester avec Postman.

### 3.2 Compétences couvertes
Spring Data/JPA, Repository, Service, REST Controller, CRUD, HTTP, manipulation de données.

### 3.3 Architecture d’une feature CRUD

```
Frontend (HTML/JS)
    │  fetch + JWT
    ▼
Controller  (@RestController)   ← endpoints HTTP
    │  injection
    ▼
Service     (@Service)          ← règles métier
    │  injection
    ▼
Repository  (JpaRepository)     ← accès BDD
    │
    ▼
Entity / Table MySQL
```

### 3.4 Trois entités avec CRUD complet

#### A) JobOffer — `/api/v1/offers`

| Opération | Méthode | Endpoint |
|-----------|---------|----------|
| Create | POST | `/api/v1/offers` |
| Read list | GET | `/api/v1/offers/mine` |
| Read one | GET | `/api/v1/offers/{id}` |
| Update | PUT | `/api/v1/offers/{id}` |
| Delete | DELETE | `/api/v1/offers/{id}` |
| Public list | GET | `/api/v1/offers/public` |

**Fichiers :**  
`JobOfferController` · `JobOfferService` · `JobOfferRepository` · `JobOffer`  
**Frontend :** `frontend/app/recruiter-offers.html` + `js/recruiter-offers.js` + `js/offer-wizard.js`  
(Actions : créer, modifier, supprimer)

#### B) RefusalReason — `/api/v1/refusal-reasons`

| Opération | Méthode | Endpoint |
|-----------|---------|----------|
| Read actifs | GET | `/api/v1/refusal-reasons` |
| Read all | GET | `/api/v1/refusal-reasons?all=true` |
| Read one | GET | `/api/v1/refusal-reasons/{id}` |
| Create | POST | `/api/v1/refusal-reasons` (ADMIN) |
| Update | PUT | `/api/v1/refusal-reasons/{id}` (ADMIN) |
| Delete | DELETE | `/api/v1/refusal-reasons/{id}` (ADMIN) |

**Frontend :** `frontend/app/admin-refusal-reasons.html`

#### C) InAppNotification — `/api/v1/notifications`

| Opération | Méthode | Endpoint |
|-----------|---------|----------|
| Create | POST | `/api/v1/notifications` |
| Read | GET | `/api/v1/notifications` |
| Update (lu) | POST | `/api/v1/notifications/{id}/read` |
| Delete | DELETE | `/api/v1/notifications/{id}` |

### 3.5 Autres APIs utiles (hors “3 CRUD” mais livrées)

- Auth : `/api/v1/auth/login`, OTP, Google  
- Applications / pipeline : `/api/v1/applications/**`  
- Organizations : `/api/v1/organizations/mine` (GET, PUT) + membres  
- Scorecard, AI, OCR, Admin…

### 3.6 Tests Postman

**Fichier :** `docs/postman/HireLink_CRUD_Seance4.postman_collection.json`

**Étapes de test :**
1. Importer la collection dans Postman  
2. Exécuter **Login recruteur** → token JWT enregistré  
3. CRUD Offres (POST → GET → PUT → DELETE)  
4. Login **admin** → CRUD Motifs de refus  
5. CRUD Notifications  

**Comptes démo :**
- `recruteur@hirelink.ma` / `Recruteur123!`  
- `admin@hirelink.ma` / `Admin123!`  
- `candidat@hirelink.ma` / `Candidat123!`

### 3.7 Livrables Séance 4

| Livrable | Emplacement | Statut |
|----------|-------------|--------|
| CRUD fonctionnel (≥ 3 entités) | Offres, Motifs, Notifications | OK |
| API REST | Controllers `/api/v1/...` | OK |
| Liste des endpoints | ce rapport + `SEANCE3_ET_SEANCE4.md` | OK |
| Interfaces frontend CRUD | recruiter-offers + admin-refusal-reasons | OK |
| Tests Postman | `docs/postman/...json` | OK (à exécuter / capturer) |

---

## 4. Liaison Frontend ↔ Backend (rappel)

| Côté | Fichier | Rôle |
|------|---------|------|
| FE | `frontend/js/config.js` | URL API `http://localhost:8081/api/v1` |
| FE | `frontend/js/api.js` | `fetch` + envoi JWT |
| BE | `WebConfig` / `SecurityConfig` | CORS + sécurité |
| BE | `controller/*` | Réception HTTP |
| BE | `HireLinkApplication` | Démarre le **conteneur Spring** (IoC) |

---

## 5. Bilan

### 5.1 Ce qui est acquis
- Modèle de données complet et cohérent avec le métier ATS  
- ORM JPA/Hibernate opérationnel (plus de dépendance à un `.sql` manuel comme source)  
- Architecture en couches claire et démontrable  
- CRUD REST sur 3 entités + Postman + UI  
- Documentation et diagrammes pour le suivi

### 5.2 Preuves à montrer au prochain suivi
1. Diagramme de classes (PNG)  
2. Entity `JobOffer.java` ↔ table `job_offers` dans phpMyAdmin  
3. Postman : login + CRUD offre  
4. Frontend RH : créer / modifier / supprimer une offre  

### 5.3 Points d’amélioration (optionnels)
- Utiliser activement les **Tags** (`@ManyToMany`) dans le wizard d’offre  
- Captures d’écran Postman dans le dossier `docs/`  
- Diagramme de classes “version finale exhaustive” (toutes les entities + attributs) via le prompt agent ci-dessous  

### 5.4 Conclusion
Les objectifs des séances 3 et 4 sont **atteints au niveau code et livrables**.  
Le prochain enjeu n’est plus de “produire”, mais de **savoir expliquer** le lien Entity → Hibernate → Table → API → Frontend.

---

## 6. Annexes — chemins utiles

```
HireLink/
  backend/src/main/java/ma/ensat/hirelink/
    model/          ← entities JPA
    repository/
    service/
    controller/
    config/
    security/
  frontend/app/
  docs/
    diagrammes/diagramme_classes_hirelink.png
    postman/HireLink_CRUD_Seance4.postman_collection.json
    SEANCE3_ET_SEANCE4.md
    RAPPORT_SEANCE3_SEANCE4.md   ← ce fichier
```
