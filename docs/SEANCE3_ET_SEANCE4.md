# HireLink — Livrables Séance 3 & Séance 4

## Séance 3 — Modélisation JPA / Hibernate

### Objectif
Construire le modèle de données et le relier au backend.

### Entités métier identifiées
| Entité | Table | Rôle |
|--------|-------|------|
| User | users | Candidat / Recruteur / Admin |
| Organization | organizations | Entreprise |
| Membership | memberships | N–N User ↔ Organization (+ rôle) |
| JobOffer | job_offers | Offre d'emploi |
| Application | applications | Candidature |
| Tag | tags + job_offer_tags | Tags d'offre (@ManyToMany) |
| RefusalReason | refusal_reason_catalog | Motifs de refus |
| InAppNotification | notifications | Notifications |

### Relations JPA
| Relation | Annotation |
|----------|------------|
| User → JobOffer | `@OneToMany` / `@ManyToOne` |
| User → Application | `@OneToMany` / `@ManyToOne` |
| JobOffer → Application | `@OneToMany` / `@ManyToOne` |
| User ↔ Organization | N–N via **Membership** |
| JobOffer ↔ Tag | `@ManyToMany` (`job_offer_tags`) |
| Organization → User (owner) | `@ManyToOne` |

### Configuration
`application.properties` :
- `spring.jpa.hibernate.ddl-auto=update`
- datasource MySQL/MariaDB `hirelink`

### Livrables S3
- Diagramme de classes : `docs/diagrammes/diagramme_classes_hirelink.png` (+ `.puml`)
- Entités JPA dans `backend/.../model/`
- Mapping relations (ci-dessus)
- Base MySQL fonctionnelle (phpMyAdmin → `hirelink`)
- Formulaires frontend alignés (offres, profil, pipeline)

---

## Séance 4 — CRUD complet + API REST

### 3 entités avec CRUD complet

#### 1) JobOffer (`/api/v1/offers`)
| Méthode | Endpoint | Action |
|---------|----------|--------|
| POST | `/offers` | Create |
| GET | `/offers/mine` | Read list |
| GET | `/offers/{id}` | Read one |
| PUT | `/offers/{id}` | Update |
| DELETE | `/offers/{id}` | Delete |

Frontend : `app/recruiter-offers.html` (liste + créer + modifier + supprimer)

#### 2) RefusalReason (`/api/v1/refusal-reasons`)
| Méthode | Endpoint | Action |
|---------|----------|--------|
| GET | `/refusal-reasons` | Read (actifs) |
| GET | `/refusal-reasons/{id}` | Read one |
| POST | `/refusal-reasons` | Create (admin) |
| PUT | `/refusal-reasons/{id}` | Update (admin) |
| DELETE | `/refusal-reasons/{id}` | Delete (admin) |

#### 3) InAppNotification (`/api/v1/notifications`)
| Méthode | Endpoint | Action |
|---------|----------|--------|
| POST | `/notifications` | Create |
| GET | `/notifications` | Read list |
| POST | `/notifications/{id}/read` | Update (lu) |
| DELETE | `/notifications/{id}` | Delete |

### Autres endpoints utiles
- Auth : `/api/v1/auth/login`, OTP, Google
- Applications / pipeline : `/api/v1/applications/**`
- Organizations : `/api/v1/organizations/mine` (GET/PUT) + membres
- Offres publiques : `GET /api/v1/offers/public`

### Postman
Collection : `docs/postman/HireLink_CRUD_Seance4.postman_collection.json`

1. Ouvrir Postman → Import → ce fichier  
2. Lancer **Login recruteur** (récupère le token)  
3. Exécuter les dossiers CRUD Offres / Notifications  
4. Pour motifs de refus : **Login admin** puis CRUD Motifs  

### Compétences couvertes
JPA, Hibernate, ORM, relations, Spring Data, Repository, Service, REST Controller, CRUD, HTTP.
