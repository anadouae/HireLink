# HireLink — Démarrer (installation vérifiée)

Guide destiné au professeur / jury. Comptez environ 10 à 15 minutes la première fois (Java, Maven et XAMPP déjà installés ; la première compilation Maven est plus longue).

## Prérequis

| Outil | Version |
|-------|---------|
| Java | 21+ |
| Maven | 3.9+ |
| Base de données | XAMPP (MariaDB 10.4+) ou MySQL 8 |
| Navigateur | Chrome / Edge / Firefox |
| Python | 3.x (pour servir le frontend) |

## Pas à pas

1. Récupérer le dépôt (clone Git ou archive fournie).
2. `scripts\VERIFY-PREREQS.bat`, puis démarrer **MySQL** dans le panneau XAMPP.
3. Créer une fois la base de développement `hirelink_test`, puis appliquer les migrations SQL dans l’ordre :
   ```bat
   mysql -u root -e "CREATE DATABASE IF NOT EXISTS hirelink_test CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
   mysql -u root hirelink_test < database\migrations\V000__schema_baseline_mariadb.sql
   ```
   Voir `database/migrations/README.md` pour les migrations suivantes.
   La base doit déjà exister : le backend ne la crée pas.
4. La configuration par défaut cible `hirelink_test` avec `ddl-auto=validate`.
   Ne redirigez pas `application-local.properties` vers `hirelink`.
5. Terminal 1 : `scripts\LANCER-BACKEND.bat`  
   Attendre `Tomcat started` sur le port configuré (`HIRELINK_PORT`, défaut 8081).
   Les seeds, migrations de démarrage et synchronisations sont désactivés par défaut.
   Contrôlez la base connectée avec :
   ```bat
   C:\xampp\mysql\bin\mysql.exe -u root -h 127.0.0.1 -P 3306 -e "SELECT ID, USER, DB, HOST, COMMAND FROM information_schema.PROCESSLIST WHERE DB IS NOT NULL;"
   ```
6. Terminal 2 : `scripts\LANCER-FRONTEND.bat` (équivalent : `py -m http.server 5500` depuis `frontend`)
   Ouvrir `http://127.0.0.1:5500`
7. Les comptes seed ne sont créés que si `hirelink.seed.enabled=true` est activé explicitement sur la base de test.

La base locale `hirelink` exige le profil `realdb`, une URL explicite et
`HIRELINK_REALDB_CONFIRMATION=I_CONFIRM_HIRELINK_REALDB`.

## Arrêt

`scripts\ARRETER-HIRELINK.bat` — n’arrête que les processus à l’écoute sur les ports HireLink (8081 et 5500 par défaut), repérés avec `netstat`.

## Limites honnêtes

- Sans SMTP : e-mails **journalisés** (statut LOGGED / hint SMTP_OFF), pas « envoyés ».
- Sans clé LLM : analyse en **repli local**.
- Paiement : démonstration, aucun débit réel.
