# Checklist Séance 1 — HireLink

Thème : **Conception et initialisation du projet**

Objectif : Comprendre l’architecture globale d’une application Web et mettre en place l’environnement de développement.

## Travail demandé

- [x] Domaine unique choisi : **recrutement / ATS bilatéral (HireLink)**
- [x] Acteurs définis : Candidat, Recruteur, Administrateur
- [x] Fonctionnalités et règles métier documentées (`README.md`, CDC)
- [x] Backend Spring Boot + Maven créé (`backend/`)
- [x] Dépendances configurées (`pom.xml`)
- [x] Connexion MySQL configurée (`application.properties` + `database/schema.sql`)
- [x] Frontend HTML/CSS/JS structuré (`frontend/`)
- [x] Première page d’accueil (`frontend/index.html`)
- [x] Architecture documentée : Frontend → REST → Backend → MySQL

## Livrables

- [x] Cahier des charges (voir dossier CDC / PDF)
- [x] Diagramme d’architecture générale (`docs/architecture/`)
- [x] Projet Spring Boot fonctionnel
- [x] Fichier `pom.xml`
- [x] Base de données configurée
- [x] Première page Frontend

## Preuves de fonctionnement

1. `GET http://localhost:8081/api/v1/health` → `status: UP` + MySQL `connected: true`
2. `GET http://localhost:8081/api/v1/meta` → acteurs + contraintes
3. Page Frontend : badges Backend / MySQL au vert


## Enrichissement post-Séance 1
- Fonctionnalités marché + ATS exhaustif documentées dans `ACTEURS_FONCTIONNALITES_REGLES.md` et CDC/benchmarking mis à jour.

