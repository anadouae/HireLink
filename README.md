# HireLink

ATS bilatéral — candidats & organisations recruteuses.  
**ENSA Tétouan** · Frameworks Technologie Web · Douae El Assal

Pipeline à états contrôlés · SLA visible · motifs de refus catalogue · matching local · assistance IA bornée.

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

## Fonctionnalités (aperçu CDC)

- Auth (mot de passe / OTP / Google) · profils · offres · candidatures · pipeline  
- SLA · catalogue de refus · scorecard · historique immuable  
- Matching local (F14) · assistant LLM optionnel (F15) · crédits (F16)  
- Organisations / rôles Owner–Recruteur–HM · export données (F21)

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
