# Mise à jour de la démonstration — à n'appliquer qu'avec autorisation

Point d'arrêt : aucune migration sur `hirelink`, aucun démarrage 8081/5500, aucun commit ni push tant qu'ils ne sont pas explicitement autorisés.

## Sauvegarde validée (11 nov. 2026)

| Élément | Valeur |
|---|---|
| Fichier | `E:\HireLink-backups\hirelink-master-20261111-222130.sql` |
| Taille | 1?644?046 octets |
| En-tête | dump MariaDB 10.4.32, base `hirelink` |
| Restauration isolée | base `hirelink_verify_l3` |
| Comparaison | users 126, offers 119, applications 220, interviews datés 37, orgs 42 — identiques à `hirelink` |
| Tables externes sur `hirelink` | absentes (0) |
| Tables externes sur `hirelink_verify_l3` | présentes (2) après essai du script SQL, données métier inchangées |

Commande utilisée (ne pas utiliser `>` PowerShell) :

```
C:\xampp\mysql\bin\mysqldump.exe -uroot --single-transaction --routines --databases hirelink -r E:\HireLink-backups\hirelink-master-YYYYMMDD-HHMMSS.sql
```

## Migration préparée (non exécutée sur `hirelink`)

Fichier : `database/migrate_external_offers.sql`

- Deux `CREATE TABLE IF NOT EXISTS` uniquement.
- Aucun `DROP`, aucun `ALTER` sur les tables historiques.
- Testé deux fois sur `hirelink_verify_l3` : 220 candidatures conservées.
- Après démarrage du nouveau build, `ddl-auto=update` créerait les mêmes tables si le SQL n'est pas lancé ; le script reste la voie explicite.

Commande, **seulement après autorisation** :

```
C:\xampp\mysql\bin\mysql.exe -uroot hirelink < C:\Users\daurinia\Desktop\HireLink\database\migrate_external_offers.sql
```

Contrôles immédiats :

```
SHOW TABLES FROM hirelink LIKE 'external_%';
SELECT COUNT(*) FROM hirelink.applications;
SELECT COUNT(*) FROM hirelink.users;
```

Attendu : 2 tables, 220 candidatures, 126 utilisateurs. Import Remote OK reste `false`.

## Démarrage coordonné (après migration autorisée)

Prérequis : environ 2 Go de RAM libre, ports 8081 et 5500 libres, `JAVA_HOME` = JDK 23, MySQL UP.

1. Backend (nouveau code local, pas l'ancien JAR GitHub seul) :

```
cd C:\Users\daurinia\Desktop\HireLink
set JAVA_HOME=C:\Program Files\Java\jdk-23
set PATH=%JAVA_HOME%\bin;%PATH%
cd backend
mvn -B spring-boot:run -Dspring-boot.run.jvmArguments="-Xms256m -Xmx768m -Dspring.mail.host= -Dhirelink.otp.log-to-console=false"
```

Ou `LANCER-HIRELINK-DEMO.bat` une fois la RAM disponible (il refuse si 8081 est déjà pris).

2. Santé : `http://127.0.0.1:8081/api/v1/health` ? `UP`, base connectée.

3. Frontend :

```
cd C:\Users\daurinia\Desktop\HireLink\frontend
python -m http.server 5500 --bind 127.0.0.1
```

Ou `LANCER-FRONTEND.bat`.

4. Contrôles : offres publiques, candidatures inchangées, admin Sources externes (import désactivé), pas d'appel Remote OK.

## Retour arrière

1. Arrêter uniquement le JVM HireLink sur 8081 et le serveur HTTP 5500.
2. Relancer le build du commit `b1b5f7c` si nécessaire (les tables `external_*` peuvent rester : l'ancien code les ignore).
3. Si les données sont altérées : restaurer le dump dans une base intermédiaire, comparer, puis seulement remplacer `hirelink`.

Ne pas utiliser `git reset --hard`.
