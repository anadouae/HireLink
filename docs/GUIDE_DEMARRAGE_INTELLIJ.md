# Guide démarrage HireLink (IntelliJ)

## Séance 2 — déjà fait

Oui. Packages Spring présents : `controller` · `service` · `repository` · `model` (entité) · `config` · `security` · `dto`.  
Injection de dépendances (IoC) via constructeurs. Première feature métier : **offres** (liste / création / publication) + pages frontend.

Diagramme couches : `docs/architecture/flux_controller_service_repository.png`

## Démarrer en 5 minutes

1. MySQL : `CREATE DATABASE hirelink CHARACTER SET utf8mb4;`
2. IntelliJ → Open → `HireLink/backend` → Run `HireLinkApplication`
3. Health : http://localhost:8081/api/v1/health
4. Frontend : `cd frontend && python -m http.server 5500` → http://localhost:5500
5. Login démo : `candidat@hirelink.ma` / `Candidat123!` ou `recruteur@hirelink.ma` / `Recruteur123!`

Détails complets : `docs/GUIDE_DEMARRAGE_INTELLIJ.docx`
