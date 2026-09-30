# Note de présentation — Séance 1 (HireLink)

À montrer / remettre pour la séance.

## 1. Domaine
**HireLink** : plateforme bilatérale de recrutement (ATS) — transparence du pipeline, délai de réponse engagé, refus motivé, matching local sans API payante.

## 2. Architecture (phrase clé)
> Frontend HTML/CSS/JS → API REST → Backend Spring Boot (Maven) → MySQL

## 3. Démo live (5 min)
1. Ouvrir `docs/architecture/architecture_seance1.pdf`
2. Montrer `backend/pom.xml` (dépendances Web, JPA, MySQL)
3. Montrer `application.properties` + `database/schema.sql`
4. Lancer le backend → `http://localhost:8081/api/v1/health`
5. Ouvrir `frontend/index.html` → badges Backend / MySQL
6. Remettre le CDC : `docs/cahier-des-charges/HireLink_Cahier_des_charges.pdf`

## 4. Fichiers à zippor pour le rendu
```
HireLink/
  README.md
  backend/
  frontend/
  database/
  docs/
```
