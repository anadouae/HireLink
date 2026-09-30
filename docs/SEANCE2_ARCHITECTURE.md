# Séance 2 — Architecture Backend avec Spring

## Objectif

Mettre en place l’architecture interne du backend et comprendre IoC / injection de dépendances.

## Statut : FAIT

### Backend — packages standards

| Package | Rôle |
|---------|------|
| `controller` | Points d’entrée REST (`@RestController`) |
| `service` | Logique métier (`@Service`, injection constructeur) |
| `repository` | Accès données (`JpaRepository`) |
| `model` | Entités JPA (équivalent « entity » du cours) |
| `config` / `security` / `dto` | Config, JWT, contrats API |

### Première fonctionnalité métier

**Offres d’emploi** : création, liste, publication, SLA —  
`JobOfferController` → `JobOfferService` → `JobOfferRepository` → entité `JobOffer`.

### Frontend associé

- Liste : `frontend/app/recruiter-offers.html`
- Formulaire ajout (wizard compétences) : `frontend/js/offer-wizard.js`
- Côté candidat : consultation / postulation `frontend/app/candidate-offers.html`

### Livrable diagramme de flux

`docs/architecture/flux_controller_service_repository.png`

### Compétences couvertes

IoC · conteneur Spring · Beans · injection de dépendances · architecture en couches · séparation des responsabilités.
