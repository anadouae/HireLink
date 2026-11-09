# Accès à la base de données HireLink (MySQL)

## Connexion rapide

| Paramètre | Valeur |
|-----------|--------|
| Host | `localhost` |
| Port | `3306` |
| User | `root` |
| Password | _(vide)_ sur les installations locales |
| Database | `hirelink` |

> **Deux serveurs ne sont pas interchangeables.** `C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe` (MySQL 8)
> et `C:\xampp\mysql\bin\mysql.exe` (MariaDB 10.4, cas de ce dépôt) n'ont ni les mêmes options ni le même
> comportement sur `ENUM`, les `utf8mb4_*` collations ou `DROP`/`CREATE`. Vérifiez lequel tourne réellement :
> `C:\xampp\mysql\bin\mysql.exe -uroot -e "SELECT VERSION;"`.

### MySQL Workbench
1. Nouvelle connexion → `localhost:3306` / `root` / mot de passe vide  
2. Schéma **hirelink**  
3. Tables : `users`, `job_offers`, `applications`, `email_otps`

### Ligne de commande
```bat
C:\xampp\mysql\bin\mysql.exe -uroot hirelink
```

```sql
SHOW TABLES;
SELECT id, email, role, skills, sought_roles, preferred_locations FROM users;
SELECT id, title, published, response_sla_days, location FROM job_offers;
SELECT id, status, match_score, job_offer_id, candidate_id FROM applications;
```

### ⚠️ Reset complet (destructif — efface comptes, candidatures, offres, documents, historiques et audit)
**Ne commencez jamais par cette commande.** Sauvegardez d'abord, puis vérifiez que le fichier est exploitable :

```bat
mkdir backup-db 2>nul
C:\xampp\mysql\bin\mysqldump.exe -uroot hirelink > backup-db\hirelink-avant-reset.sql
C:\xampp\mysql\bin\mysql.exe -uroot -e "SHOW CREATE DATABASE hirelink;"
```

Ce n'est qu'après avoir contrôlé le dump :

```bat
C:\xampp\mysql\bin\mysql.exe -uroot -e "DROP DATABASE IF EXISTS hirelink; CREATE DATABASE hirelink CHARACTER SET utf8mb4;"
```

Puis relancer le backend — le `DataSeeder` recrée comptes, offres et candidatures **uniquement parce que la base
est vide** (`hirelink.seed.enabled=true` par défaut, `false` dans le profil `prod`). Le démarrage ne vide jamais
une base contenant déjà des comptes : aucune purge silencieuse, aucune ré-initialisation automatique.

**Avant de détruire quoi que ce soit**, sachez qu'il existe presque toujours une alternative non destructive :
re-créer une base de test à côté (`hirelink_test`, voir `backend/src/test/resources/application.properties`) ou
révoquer une session plutôt que de supprimer la ligne (`user_sessions` porte une chaîne de hachages).

## Comptes seedés

| Email | Mot de passe | Rôle |
|-------|--------------|------|
| `admin@hirelink.ma` | `Admin123!` | ADMINISTRATEUR |
| `recruteur@hirelink.ma` | `Recruteur123!` | RECRUTEUR (Salma Berrada, Atlas Digital) |
| `cabinet@atlasdigital.ma` | `Recruteur123!` | RECRUTEUR |
| `candidat@hirelink.ma` | `Candidat123!` | CANDIDAT (Sara) |
| `youssef.data@hirelink.ma` | `Candidat123!` | CANDIDAT |
| `amina.qa@hirelink.ma` | `Candidat123!` | CANDIDAT |
| `nouveau@hirelink.ma` | *(OTP only)* | CANDIDAT |

## API / Swagger
- API : `http://localhost:8081`
- Swagger : **désactivé par défaut** (`hirelink.swagger.enabled=false`). Pour l'ouvrir en local :
  `HIRELINK_SWAGGER_ENABLED=true` puis `http://localhost:8081/swagger-ui.html`
- Front : `http://localhost:5500`

## « Code email » = quoi ?
Ce n’est **pas** une feature produit mystérieuse : c’est la **connexion sans mot de passe** (OTP).  
Sur l’écran login : bouton *« Se connecter avec un code reçu par e-mail »* → HireLink envoie un code à 6 chiffres.
Sans serveur SMTP configuré, le code n'apparaît dans la console que si l'exploitant l'a explicitement autorisé
(`-Dhirelink.otp.log-to-console=true`) ; c'est une option de démonstration, jamais le comportement par défaut.
