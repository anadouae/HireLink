# Accès à la base de données HireLink (MySQL)

## Connexion rapide

| Paramètre | Valeur |
|-----------|--------|
| Host | `localhost` |
| Port | `3306` |
| User | `root` |
| Password | _(vide)_ — MySQL Server 8.0 local |
| Database | `hirelink` |

### MySQL Workbench
1. Nouvelle connexion → `localhost:3306` / `root` / mot de passe vide  
2. Schéma **hirelink**  
3. Tables : `users`, `job_offers`, `applications`, `email_otps`

### Ligne de commande
```bat
"C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe" -uroot hirelink
```

```sql
SHOW TABLES;
SELECT id, email, role, skills, sought_roles, preferred_locations FROM users;
SELECT id, title, published, response_sla_days, location FROM job_offers;
SELECT id, status, match_score, job_offer_id, candidate_id FROM applications;
```

### Reset complet (re-seed riche)
```bat
"C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe" -uroot -e "DROP DATABASE IF EXISTS hirelink; CREATE DATABASE hirelink CHARACTER SET utf8mb4;"
```
Puis relancer le backend Spring Boot — le `DataSeeder` recrée comptes, offres et candidatures.

## Comptes seedés

| Email | Mot de passe | Rôle |
|-------|--------------|------|
| `admin@hirelink.ma` | `Admin123!` | ADMINISTRATEUR |
| `recruteur@hirelink.ma` | `Recruteur123!` | RECRUTEUR |
| `cabinet@atlasdigital.ma` | `Recruteur123!` | RECRUTEUR |
| `candidat@hirelink.ma` | `Candidat123!` | CANDIDAT (Sara) |
| `youssef.data@hirelink.ma` | `Candidat123!` | CANDIDAT |
| `amina.qa@hirelink.ma` | `Candidat123!` | CANDIDAT |
| `nouveau@hirelink.ma` | *(OTP only)* | CANDIDAT |

## API / Swagger
- API : `http://localhost:8081`
- Swagger : `http://localhost:8081/swagger-ui.html`
- Front : `http://localhost:5500`

## « Code email » = quoi ?
Ce n’est **pas** une feature produit mystérieuse : c’est la **connexion sans mot de passe** (OTP).  
Sur l’écran login : bouton *« Se connecter avec un code reçu par e-mail »* → HireLink envoie un code à 6 chiffres (affiché aussi dans la console backend en démo).
