# HireLink — Guide d’installation et de test (Windows)

Ce guide s’adresse à quelqu’un qui n’a jamais lancé le projet. Il explique, pas à pas, comment installer les outils, démarrer HireLink sur Windows, puis tester chaque rôle (candidat, recruteur, hiring manager, administrateur), les parcours croisés entre rôles et la sécurité. Les captures d’écran proviennent d’une exécution réelle de l’application.

> Honnêteté sur les tests : tous les scénarios de ce guide ont été exécutés pour de vrai, avec un navigateur automatisé, sur une base vide, **sous Linux avec MariaDB 10.11** (MySQL 8 n’était pas installable sur la machine de test). Les commandes spécifiques à Windows (installation des outils, PowerShell) sont des commandes standard mais n’ont pas été exécutées sur Windows. Le comportement de l’application ne dépend pas du système.

## 1. Ce dont vous avez besoin

| Outil | Version | À quoi il sert | Vérification (invite de commandes) |
|---|---|---|---|
| JDK (Java) | 21 | Exécuter le serveur (backend) | java -version  → doit afficher 21 |
| MySQL (ou MariaDB) | MySQL 8 / MariaDB 10.4+ | Base de données | mysql --version |
| IntelliJ IDEA Community **ou** Maven 3.9 | récente / 3.9 | Compiler et lancer le backend | mvn -v |
| Python | 3.9 ou plus | Servir le site (frontend) et calculer le code TOTP de l’administrateur | python --version |
| Google Chrome ou Edge | récente | Utiliser l’application | — |

### 1.1 Installer Java 21

1. Téléchargez **Temurin JDK 21** (https://adoptium.net, choisir « Windows x64 » puis « .msi »).
2. Lancez l’installeur. À l’étape « Custom Setup », activez **Set JAVA_HOME variable** et **Add to PATH**.
3. Ouvrez une **nouvelle** invite de commandes (touche Windows, tapez `cmd`, Entrée) et tapez `java -version`. Vous devez lire `openjdk version "21…"`. Si vous lisez 8, 11 ou 17, voir le dépannage « Mauvaise version de Java ».

### 1.2 Installer MySQL

1. Téléchargez **MySQL Installer** (https://dev.mysql.com/downloads/installer/) et choisissez « Server only ».
2. Gardez le port **3306**. Quand l’installeur demande un mot de passe pour l’utilisateur `root`, **notez-le** (vous pouvez aussi le laisser vide si l’installeur l’accepte, mais ce n’est pas conseillé).
3. Vérifiez : `mysql --version`. (Si la commande est introuvable, ajoutez `C:\Program Files\MySQL\MySQL Server 8.0\bin` au PATH.)

> XAMPP fonctionne aussi (MariaDB, utilisateur `root` sans mot de passe) : c’est la configuration par défaut du projet.

### 1.3 Installer IntelliJ IDEA (ou Maven)

1. Téléchargez **IntelliJ IDEA Community** (https://www.jetbrains.com/idea/download) et installez-le avec les options par défaut.
2. Alternative sans IntelliJ : installez Maven (https://maven.apache.org/download.cgi), dézippez-le dans `C:\tools\apache-maven-3.9.9`, ajoutez `C:\tools\apache-maven-3.9.9\bin` au PATH, puis `mvn -v` doit afficher la version de Maven **et** « Java version: 21 ».

### 1.4 Installer Python

1. Téléchargez Python 3 sur https://www.python.org/downloads/windows/ et **cochez « Add python.exe to PATH »** sur le premier écran de l’installeur.
2. `python --version` doit répondre `Python 3.x`. (Si `python` ouvre le Microsoft Store, utilisez `py` à la place de `python` dans tout le guide.)

## 2. Récupérer le projet

1. Option A — archive : dézippez `HireLink-final.zip` dans `C:\HireLink`. Vous devez avoir `C:\HireLink\backend`, `C:\HireLink\frontend`, `C:\HireLink\docs`.
2. Option B — Git : `git clone <adresse du dépôt> C:\HireLink` puis `cd C:\HireLink` et `git checkout fix/espace-recruteur`.

## 3. Préparer la base de données

1. Dans une invite de commandes : `mysql -u root -p` (saisissez votre mot de passe ; avec XAMPP, `mysql -u root`).
2. Collez cette commande puis Entrée (l’UTF-8 complet est indispensable : les notifications contiennent des « → » et des accents) :  
   `CREATE DATABASE hirelink CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;`
3. Tapez `exit;` pour quitter. (Les tables sont créées automatiquement au premier démarrage du backend.)
4. **Si votre mot de passe root n’est pas vide** : ouvrez `backend\src\main\resources\application.properties` et renseignez la ligne `spring.datasource.password=` (ex. `spring.datasource.password=MonMotDePasse`). Ne committez jamais cette valeur ; mieux : mettez-la dans `application-local.properties` (voir §5).

> **Vous aviez déjà une base `hirelink` (version précédente) ?** Ne la supprimez pas : au démarrage, le backend (`spring.jpa.hibernate.ddl-auto=update`) ajoute tout seul les nouvelles colonnes (conditions des offres, entretiens, préférences e-mail, journal d’e-mails) et ajoute les étapes `OFFER` et `HIRED` à la colonne `applications.status` (type ENUM de MySQL/MariaDB). Les offres, candidatures et comptes existants sont conservés ; les anciennes offres s’affichent simplement avec les conditions « non précisées ». Cette mise à jour a été vérifiée sur une copie d’une base de l’ancienne version. Aucune commande SQL n’est à saisir. **Seul cas particulier** : si le démarrage s’arrête avec une erreur du type `Data truncated for column 'status'` (colonne non modifiée, par exemple faute de droits), exécutez une fois : `ALTER TABLE applications MODIFY status ENUM('SUBMITTED','SCREENING','SHORTLISTED','INTERVIEW','OFFER','HIRED','REJECTED','WITHDRAWN') NOT NULL;` puis redémarrez. Sauvegardez toujours la base avant une mise à jour (`mysqldump -u root hirelink > sauvegarde.sql`).

## 4. Démarrer le backend

Choisissez **une** des deux méthodes.

### Méthode IntelliJ

1. IntelliJ → **Open** → sélectionnez le dossier `C:\HireLink\backend` (celui qui contient `pom.xml`). Attendez la fin de l’indexation et du téléchargement Maven (barre de progression en bas).
2. File → Project Structure → Project → **SDK : 21**.
3. Ouvrez `src/main/java/ma/ensat/hirelink/HireLinkApplication.java` et cliquez sur le triangle vert ▶ à côté de `main` → **Run**.

### Méthode ligne de commande

```
cd C:\HireLink\backend
mvn spring-boot:run
```

### Comment savoir que c’est prêt ?

1. Dans la console, attendez la ligne `Started HireLinkApplication in … seconds`.
2. **Attendez ensuite le bandeau « HireLink — comptes de démonstration »** (quelques secondes de plus au tout premier démarrage : les comptes et offres de démonstration sont créés *après* le message « Started »). Si vous testez avant ce bandeau, la connexion peut échouer : patientez, c’est normal.
3. Ouvrez http://localhost:8081/api/v1/health dans le navigateur. Vous devez voir `{"application":"HireLink","status":"UP",…,"database":{"connected":true}}`.

## 5. Clés et secrets (facultatif)

**L’application fonctionne sans aucune clé** : le scoring IA retombe sur l’analyse locale et la clé de signature des sessions (JWT) est générée automatiquement dans `backend\data\jwt.key` (dossier ignoré par Git). Si vous voulez brancher un fournisseur IA ou fixer vos propres secrets :

1. Copiez `backend\src\main\resources\application-local.properties.example` vers `backend\application-local.properties` (ce fichier est ignoré par Git, ne le committez jamais).
2. Remplacez les valeurs `COLLER_CLE_…` par vos clés et supprimez les lignes que vous n’utilisez pas.
3. Autre méthode : variables d’environnement (modèle : `.env.example` à la racine). Sous IntelliJ : Run → Edit Configurations → *Environment variables* (ex. `HIRELINK_GROQ_API_KEY=xxxx`). Sous Windows : `setx HIRELINK_JWT_SECRET "valeur-longue-aleatoire"` puis rouvrir le terminal.
4. Redémarrez le backend.

Variables reconnues : HIRELINK_JWT_SECRET, HIRELINK_CORS_ORIGINS, HIRELINK_LLM_API_KEY, HIRELINK_GEMINI_API_KEY, HIRELINK_GROQ_API_KEY, HIRELINK_MISTRAL_API_KEY, HIRELINK_GOOGLE_CLIENT_ID, HIRELINK_WEBAUTHN_RP_ID, HIRELINK_WEBAUTHN_ORIGINS, HIRELINK_FRONTEND_URL, HIRELINK_ADMIN_EMAIL, HIRELINK_ADMIN_PASSWORD, HIRELINK_SWAGGER_ENABLED. Détails et audit des secrets : `docs/SECURITE.md`.

### 5.1 E-mails : brancher un serveur SMTP (exemple Gmail)

HireLink envoie de vrais e-mails (HTML + texte, en français) : candidature reçue, étape suivante, entretien planifié / confirmé / reporté / annulé, rappel la veille d’un entretien, proposition, embauche, ainsi que les codes et liens de sécurité. L’envoi est asynchrone : une panne SMTP ne bloque jamais l’application (l’échec est journalisé et visible dans Administration → E-mails envoyés). **Sans serveur SMTP**, l’application fonctionne quand même : les messages sont seulement journalisés et consultables par l’administrateur.

1. Dans votre compte Google : *Sécurité* → activez la **validation en 2 étapes**, puis *Mots de passe des applications* → créez un mot de passe d’application (16 caractères). N’utilisez **jamais** le mot de passe habituel du compte.
2. Copiez `backend\src\main\resources\application-local.properties.example` vers **`backend\application-local.properties`** (ou `backend\src\main\resources\application-local.properties`) : ce fichier est ignoré par Git, ne le committez jamais.
3. Ajoutez (ou gardez) ces lignes en remplaçant les valeurs entre guillemets par les vôtres. Le mot de passe ci-dessous est un **modèle** à remplacer :

```
spring.mail.host=smtp.gmail.com
spring.mail.port=587
spring.mail.username=votre.adresse@gmail.com
spring.mail.password=COLLER_ICI_LE_MOT_DE_PASSE_D_APPLICATION_A_16_CARACTERES
hirelink.mail.from=HireLink <votre.adresse@gmail.com>
```

1. Redémarrez le backend. Dans la console, vous devez lire `E-mails : envoi SMTP via smtp.gmail.com:587 (expéditeur HireLink <votre.adresse@gmail.com>)`. Si vous lisez « aucun serveur SMTP configuré », le fichier n’a pas été trouvé.
2. Testez : connectez-vous en candidat, ouvrez *Paramètres* et vérifiez que « E-mails de suivi » est activé ; puis demandez un lien « Mot de passe oublié » : le message arrive dans la boîte Gmail.

Le fichier est lu à ces trois emplacements (le premier trouvé gagne) : `application-local.properties` dans le dossier de lancement, `backend/application-local.properties` si vous lancez depuis la racine du dépôt, ou `src/main/resources/application-local.properties` (placé dans le classpath). Chaque emplacement a été vérifié : la ligne de démarrage ci-dessus s’affiche. **Autre méthode, sans fichier** : variables d’environnement `HIRELINK_MAIL_HOST`, `HIRELINK_MAIL_PORT` (587 par défaut), `HIRELINK_MAIL_USERNAME`, `HIRELINK_MAIL_PASSWORD`, `HIRELINK_MAIL_FROM` (modèle : `.env.example`). Pour un serveur sans chiffrement ni authentification (outil de test local) : `HIRELINK_MAIL_AUTH=false` et `HIRELINK_MAIL_STARTTLS=false`.

> Tester sans compte Gmail : lancez un récepteur local comme **Mailpit** (`mailpit` écoute en SMTP sur 1025 et affiche les messages sur http://localhost:8025), puis démarrez le backend avec `HIRELINK_MAIL_HOST=127.0.0.1 HIRELINK_MAIL_PORT=1025 HIRELINK_MAIL_AUTH=false HIRELINK_MAIL_STARTTLS=false`. C’est ainsi que les e-mails de ce guide ont été vérifiés (la connexion réelle à Gmail n’a pas été exécutée : elle exige vos identifiants).

**Désinscription.** Les e-mails de suivi (candidatures, entretiens) respectent la préférence « E-mails de suivi » de *Paramètres* (candidat : *Confidentialité* ; recruteur : *Alertes*) ; chaque message contient le lien pour la modifier. Les e-mails de sécurité (codes, réinitialisation, alerte de connexion) sont toujours envoyés.

## 6. Démarrer le frontend

1. Ouvrez une **deuxième** invite de commandes (laissez le backend tourner dans la première).
2. `cd C:\HireLink\frontend` puis `python -m http.server 5500`. Vous devez lire « Serving HTTP on … port 5500 ».
3. Ouvrez **http://localhost:5500/** (et pas `127.0.0.1` ni un fichier ouvert par double-clic : les cookies de session et les passkeys exigent `localhost`).

## 7. Comptes de test

| E-mail | Mot de passe | Rôle |
|---|---|---|
| admin@hirelink.ma | Admin123!  (+ code TOTP, voir §14) | Administrateur |
| n.bennani@atlasdigital.ma | Recruteur123! | Recruteur, **Owner** d’Atlas Digital |
| k.benali@atlasdigital.ma | Recruteur123! | Recruteur (Atlas Digital) |
| m.alaoui@atlasdigital.ma | Recruteur123! | **Hiring Manager** (Atlas Digital) |
| recruteur@hirelink.ma | Recruteur123! | Alias Owner d’Atlas Digital |
| candidat@hirelink.ma | Candidat123! | Candidat (Sara El Amrani, alias) |
| y.idrissi@outlook.com | Candidat123! | Candidat (utilisé pour la sécurité) |
| i.chraibi@gmail.com | Candidat123! | Candidat (utilisé pour le verrouillage) |
| recrutement.ocpgroup@hirelink.ma | Recruteur123! | Recruteur d’une **autre** organisation (OCP Group) |

> Ces mots de passe « faciles » ne servent qu’aux comptes de démonstration créés par l’amorçage. Tout mot de passe que **vous** créez doit respecter la politique : 12 à 64 caractères, pas de mot de passe courant, pas votre e-mail ni votre nom (ex. « Atlas-Vert-Soleil-2026 »). Le code TOTP ou OTP n’est jamais envoyé par e-mail en développement : il s’affiche dans la **console du backend**.

> Les données de démonstration comprennent aussi des offres de **PFE** (gratification 3 000 MAD / mois, convention exigée) et de **Freelance** (2 500 – 3 200 MAD / jour, télétravail), des candidatures aux étapes **Proposition** (OFFER) et **Embauché** (HIRED), des entretiens avec type (sur site, visio, téléphone) et nature, et des notifications associées : de quoi voir toutes les nouveautés (§18) dès le premier démarrage.

## 8. Scénarios — Candidat

1. Allez sur http://localhost:5500/auth.html?mode=login. Saisissez `candidat@hirelink.ma` / `Candidat123!`.

   ![c-connexion](guide-captures/01-c-connexion.png)

2. Cliquez « Se connecter ». Vous arrivez sur le **tableau de bord** candidat.

   ![c-tableau-de-bord](guide-captures/02-c-tableau-de-bord.png)

3. Menu **Profil / CV**. Vous voyez le profil à compléter.

   ![c-profil](guide-captures/03-c-profil.png)

4. Choisissez le fichier `docs\guide-fichiers\CV_exemple.txt` : l’application lit le CV (aucune clé IA requise) et extrait les compétences (Java, Spring Boot, MySQL…).

   ![c-profil-cv-importe](guide-captures/04-c-profil-cv-importe.png)

5. Vérifiez les sections détectées.

   ![c-profil-sections](guide-captures/05-c-profil-sections.png)

6. Cliquez **Confirmer le CV** : le message « CV confirmé » apparaît.

   ![c-profil-cv-confirme](guide-captures/06-c-profil-cv-confirme.png)

7. Menu **Offres** : la liste des offres publiées.

   ![c-offres](guide-captures/07-c-offres.png)

8. Filtrez par ville « Tétouan » : la liste se réduit.

   ![c-offres-filtre-ville](guide-captures/08-c-offres-filtre-ville.png)

9. Ouvrez l’offre « Développeur Full-Stack Java ».

   ![candidat-offres-detail](guide-captures/09-candidat-offres-detail.png)

10. Cliquez **Postuler** : étape 1, rappel de l’offre.

   ![candidat-postuler-1-offre](guide-captures/10-candidat-postuler-1-offre.png)

11. Étape 2 : compétences à valider.

   ![candidat-postuler-2-competences](guide-captures/11-candidat-postuler-2-competences.png)

12. Étape 3 : CV du profil.

   ![candidat-postuler-3-cv](guide-captures/12-candidat-postuler-3-cv.png)

13. Étape 4 : lettre de motivation.

   ![candidat-postuler-4-lettre](guide-captures/13-candidat-postuler-4-lettre.png)

14. Étape 5 : envoi. Message « Candidature envoyée ».

   ![candidat-postuler-5-envoi](guide-captures/14-candidat-postuler-5-envoi.png)

15. Retour aux offres : l’offre est marquée comme déjà envoyée.

   ![c-apres-envoi](guide-captures/15-c-apres-envoi.png)

16. Répétez avec « Ingénieur QA » (lettre courte), puis ouvrez **Mes candidatures** : 2 candidatures, avec statut et délai de réponse.

   ![c-mes-candidatures](guide-captures/16-c-mes-candidatures.png)


## 9. Scénarios — Recruteur (Owner d’Atlas Digital)

1. Déconnectez-vous (ou ouvrez une fenêtre de navigation privée) puis connectez-vous avec `n.bennani@atlasdigital.ma` / `Recruteur123!`.

   ![r-connexion](guide-captures/21-r-connexion.png)

2. Tableau de bord recruteur : candidatures à traiter, entretiens à venir, délais.

   ![r-tableau-de-bord](guide-captures/22-r-tableau-de-bord.png)

3. Menu **Pipeline** : les candidatures par étape (Soumise, Présélection, Shortlist, Entretien, Refusée).

   ![r-pipeline](guide-captures/23-r-pipeline.png)

4. Ouvrez le dossier de Sara (bouton « Voir »).

   ![r-dossier](guide-captures/24-r-dossier.png)

5. Dans « Hiring Manager », choisissez Mehdi Alaoui puis **Assigner** : il verra ce dossier (voir §11).

   ![r-dossier-hm](guide-captures/25-r-dossier-hm.png)

6. Fermez, cliquez **Avancer** sur le dossier : étape Présélection. La note et les trois cases de confirmation (identité, CV lu, délai) sont obligatoires.

   ![r-avancer-preselection](guide-captures/26-r-avancer-preselection.png)

7. Avancez de nouveau vers **Shortlist** (même procédure).
8. Avancez vers **Entretien** : type Visio, date, lien et notes sont obligatoires.

   ![r-avancer-entretien](guide-captures/27-r-avancer-entretien.png)

9. Le dossier passe dans la colonne Entretien ; le candidat est notifié.

   ![r-pipeline-apres-entretien](guide-captures/28-r-pipeline-apres-entretien.png)

10. Sur la candidature QA, cliquez **Refuser** : choisissez un motif du **catalogue** (ou « Autre » avec un texte libre obligatoire).

   ![r-refus](guide-captures/29-r-refus.png)

11. Confirmez : la candidature passe en Refusée.

   ![r-pipeline-apres-refus](guide-captures/30-r-pipeline-apres-refus.png)

12. Variante « Autre » (sur une autre candidature) : choisissez **Autre** dans la liste ; la zone « Précision » apparaît et le bouton **Confirmer le refus** reste grisé tant que vous n’avez pas écrit au moins 3 caractères. Écrivez par exemple « Le profil ne correspond pas au niveau demandé pour cette mission. » : le candidat verra ce texte comme motif.

   ![r-refus-autre](guide-captures/29b-r-refus-autre.png)

13. Menu **Entretiens** : l’agenda montre l’entretien planifié.

   ![r-entretiens](guide-captures/31-r-entretiens.png)

14. Menu **Candidats** : tous les candidats de l’organisation.

   ![r-candidats](guide-captures/32-r-candidats.png)

15. Menu **Offres** : la liste, puis **Nouvelle offre** (assistant en 3 étapes).

   ![r-offres](guide-captures/33-r-offres.png)

16. Étape 1 : titre « Ingénieur DevOps Cloud », description, lieu « Tétouan ».

   ![r-offre-etape1](guide-captures/34-r-offre-etape1.png)

17. Étape 2 : cochez trois compétences.

   ![r-offre-etape2](guide-captures/35-r-offre-etape2.png)

18. Étape 3 : cochez « Publier maintenant » puis terminez.

   ![r-offre-etape3](guide-captures/36-r-offre-etape3.png)

19. L’offre apparaît dans la liste, publiée.

   ![r-offres-apres-creation](guide-captures/37-r-offres-apres-creation.png)

20. Cliquez **Modifier** sur cette offre et changez le titre en « … (senior) ».

   ![r-offre-modifier](guide-captures/38-r-offre-modifier.png)

21. Le nouveau titre est enregistré.

   ![r-offres-apres-modif](guide-captures/39-r-offres-apres-modif.png)

22. Menu **Entreprise & équipe**.

   ![r-equipe](guide-captures/40-r-equipe.png)

23. **Inviter** un collègue (`collegue@exemple.ma`, rôle Recruteur). Le collègue s’était inscrit avant avec ce même e-mail.

   ![r-invitation](guide-captures/41-r-invitation.png)

24. Le collègue apparaît dans l’équipe.

   ![r-equipe-invitation-envoyee](guide-captures/42-r-equipe-invitation-envoyee.png)

25. Envoyez le logo `docs\guide-fichiers\logo_exemple.png` : message « Image enregistrée — visible côté candidats ». (Un SVG ou un faux fichier image est refusé.)

   ![r-equipe-logo](guide-captures/43-r-equipe-logo.png)

26. Menu **Assistant IA** : trois outils (grille d’entretien, message de refus, synthèse de CV). Sans clé IA, ils répondent avec des modèles locaux.

   ![r-assistant](guide-captures/44-r-assistant.png)

27. Grille d’entretien : choisissez une offre puis « Générer ».

   ![r-assistant-grille](guide-captures/45-r-assistant-grille.png)

28. Message de refus : choisissez un motif du catalogue.

   ![r-assistant-refus](guide-captures/46-r-assistant-refus.png)

29. Synthèse de CV : choisissez une candidature.

   ![r-assistant-synthese](guide-captures/47-r-assistant-synthese.png)

30. Menu **Notifications**.

   ![r-notifications](guide-captures/48-r-notifications.png)

31. Menu **Paramètres**.

   ![r-parametres](guide-captures/49-r-parametres.png)

32. Bouton lune/soleil : thème sombre.

   ![r-tableau-de-bord-sombre](guide-captures/50-r-tableau-de-bord-sombre.png)


## 10. Scénarios — Hiring Manager

1. Connectez-vous avec `m.alaoui@atlasdigital.ma` / `Recruteur123!`.

   ![h-connexion](guide-captures/61-h-connexion.png)

2. Le tableau de bord est limité à son périmètre.

   ![h-tableau-de-bord](guide-captures/62-h-tableau-de-bord.png)

3. Le **pipeline ne montre que les dossiers qui lui sont assignés** (ici 4), jamais l’ensemble de l’organisation.

   ![h-pipeline](guide-captures/63-h-pipeline.png)

4. Ouvrez un dossier assigné.

   ![h-dossier](guide-captures/64-h-dossier.png)

5. Cliquez **Scorecard**, notez chaque critère de 1 à 5.

   ![h-scorecard](guide-captures/65-h-scorecard.png)

6. **Enregistrer les notes** : message « Notes enregistrées ».

   ![h-scorecard-enregistree](guide-captures/66-h-scorecard-enregistree.png)

7. Allez dans **Offres** : l’accès est refusé (« Accès réservé aux Owners et Recruteurs »).

   ![h-offres-restreint](guide-captures/67-h-offres-restreint.png)


## 11. Parcours croisés entre rôles

Reprenez le candidat (`candidat@hirelink.ma`) après les actions du recruteur :

1. Tableau de bord candidat : l’entretien apparaît.

   ![c2-tableau-de-bord](guide-captures/81-c2-tableau-de-bord.png)

2. **Mes candidatures** → « Full-Stack » : étape Entretien, type Visio, date, lien et notes visibles.

   ![c2-candidature-entretien](guide-captures/82-c2-candidature-entretien.png)

3. Bouton d’action : **Synthèse de mon CV** (aide à la préparation).

   ![c2-synthese](guide-captures/83-c2-synthese.png)

4. Candidature QA : statut Refusée **et motif** du catalogue visible.

   ![c2-candidature-refus](guide-captures/84-c2-candidature-refus.png)

5. **Notifications** : refus, entretien, assignation, etc.

   ![c2-notifications](guide-captures/85-c2-notifications.png)

6. Un clic sur une notification ouvre la candidature concernée.

   ![c2-notification-clic](guide-captures/86-c2-notification-clic.png)

7. Menu **Entreprises** : catalogue des organisations.

   ![c2-entreprises](guide-captures/87-c2-entreprises.png)

8. **Paramètres**.

   ![c2-parametres](guide-captures/88-c2-parametres.png)

9. Onglet **Compte** : « Exporter mes données » (droit d’accès RGPD).

   ![c2-parametres-compte](guide-captures/89-c2-parametres-compte.png)

10. Le fichier JSON téléchargé contient le profil, les candidatures et les consentements (sans mot de passe ni secret).

   ![c2-export](guide-captures/90-c2-export.png)

11. Thème sombre.

   ![c2-tableau-de-bord-sombre](guide-captures/91-c2-tableau-de-bord-sombre.png)

12. Offres en thème sombre.

   ![c2-offres-sombre](guide-captures/92-c2-offres-sombre.png)

13. Version mobile (écran 390 px).

   ![c2-mobile-tableau-de-bord](guide-captures/93-c2-mobile-tableau-de-bord.png)

14. Menu mobile.

   ![c2-mobile-menu](guide-captures/94-c2-mobile-menu.png)


## 12. Parcours : un nouveau recruteur, la vérification ICE par l’administrateur

1. Ouvrez http://localhost:5500/auth.html?mode=register, saisissez un e-mail : `rh@startup-demo.ma`.

   ![x-inscription-email](guide-captures/121-x-inscription-email.png)

2. Le code à 6 chiffres est **affiché dans la console du backend** (ligne `OTP [...] pour rh@startup-demo.ma → 123456`). Saisissez-le.

   ![x-inscription-code](guide-captures/122-x-inscription-code.png)

3. Choisissez le rôle **Recruteur**.

   ![x-inscription-role](guide-captures/123-x-inscription-role.png)

4. Renseignez prénom, nom, téléphone, ville, entreprise, fonction et un **mot de passe de 12 caractères minimum** (ex. `Recruteur-Atlas-2026`).

   ![x-inscription-profil](guide-captures/124-x-inscription-profil.png)

5. Choisissez au moins un type de mission (Stage, CDI…) puis **Suivant**.

   ![x-inscription-missions](guide-captures/125-x-inscription-missions.png)

6. Vous arrivez dans l’espace recruteur.

   ![n-tableau-de-bord](guide-captures/126-n-tableau-de-bord.png)

7. Menu **Entreprise & équipe** : le statut est « Vérification en attente ».

   ![n-equipe-avant](guide-captures/127-n-equipe-avant.png)

8. Saisissez l’ICE `001234567000099` puis enregistrez : « En attente du registre ».

   ![n-equipe-ice-saisi](guide-captures/128-n-equipe-ice-saisi.png)

9. Tentez de **publier** une offre : la publication est refusée tant que l’entreprise n’est pas vérifiée.

   ![n-offre-bloquee-publication](guide-captures/129-n-offre-bloquee-publication.png)

10. Résultat : message d’erreur explicite.

   ![n-offre-bloquee-resultat](guide-captures/130-n-offre-bloquee-resultat.png)

11. L’offre reste en brouillon.

   ![n-offres-brouillon](guide-captures/131-n-offres-brouillon.png)

12. Côté **administrateur** (§14) : menu Entreprises, ouvrez « Startup Demo ».

   ![a-entreprise-detail](guide-captures/132-a-entreprise-detail.png)

13. **Valider** la vérification : l’entreprise devient « Vérifiée ».

   ![a-entreprise-verifiee](guide-captures/133-a-entreprise-verifiee.png)

14. De retour côté recruteur, l’offre peut maintenant être publiée.

   ![n-offres-apres-verif](guide-captures/134-n-offres-apres-verif.png)


## 13. Scénarios — Administrateur

Le détail de la connexion administrateur (mot de passe **et** code TOTP) est au §14. Une fois connecté :

1. Tableau de bord : indicateurs globaux.

   ![a-tableau-de-bord](guide-captures/105-a-tableau-de-bord.png)

2. **Entreprises** : vérification ICE, plans.

   ![a-entreprises](guide-captures/106-a-entreprises.png)

3. **Comptes** : tous les utilisateurs, suspension / réactivation (la suspension révoque aussitôt toutes les sessions du compte).

   ![a-comptes](guide-captures/107-a-comptes.png)

4. Filtre par nom (ex. « tazi »).

   ![a-comptes-filtre](guide-captures/108-a-comptes-filtre.png)

5. **Plans et factures**.

   ![a-plans](guide-captures/109-a-plans.png)

6. Modifier un plan.

   ![a-plan-modifier](guide-captures/110-a-plan-modifier.png)

7. **Motifs de refus** : le catalogue. Ajoutez le code `TEST_DELAI` et le libellé « Délai de réponse du candidat dépassé ».

   ![a-motifs](guide-captures/111-a-motifs.png)

8. Le nouveau motif apparaît et sera proposé aux recruteurs (l’option « Autre » reste toujours disponible).

   ![a-motif-ajoute](guide-captures/112-a-motif-ajoute.png)


## 14. Se connecter en administrateur (professeur / correcteur)

Pour l’administrateur, le mot de passe seul **ne suffit jamais** : la double authentification (TOTP) est obligatoire. Voici le chemin complet, testé.

### Première connexion : activation du TOTP

1. Allez sur http://localhost:5500/auth.html?mode=login, saisissez `admin@hirelink.ma` / `Admin123!`.

   ![a-connexion](guide-captures/101-a-connexion.png)

2. Cliquez « Se connecter ». L’écran **« Activez la double authentification »** s’affiche avec un QR code **et** une clé manuelle (32 lettres/chiffres). **Recopiez cette clé.**

   ![a-totp-activation](guide-captures/102-a-totp-activation.png)

3. Deux possibilités pour obtenir le code à 6 chiffres : (a) scanner le QR code avec Google Authenticator / Microsoft Authenticator / Authy ; (b) sans téléphone, dans une invite de commandes : `python C:\HireLink\docs\outils\totp.py LA-CLE-RECOPIEE` — le script (bibliothèque standard Python, aucune installation) affiche le code et le temps restant. Saisissez le code.

   ![a-totp-code](guide-captures/103-a-totp-code.png)

4. Cliquez « Activer ». L’application affiche **10 codes de secours** à usage unique : **notez-les**, ils ne seront plus jamais réaffichés. Cliquez « J’ai noté mes codes ».

   ![a-codes-secours](guide-captures/104-a-codes-secours.png)

5. Vous êtes dans l’espace administrateur.

   ![a-tableau-de-bord](guide-captures/105-a-tableau-de-bord.png)


### Connexions suivantes

1. Même e-mail et même mot de passe : l’écran demande le **code à 6 chiffres** (celui de l’application, de `totp.py`, ou un code de secours).

   ![s-admin-connexion-code](guide-captures/164-s-admin-connexion-code.png)

2. Un code TOTP ne peut être utilisé **qu’une seule fois** : si vous obtenez « code invalide » alors que vous venez de vous connecter, attendez la prochaine période de 30 secondes.

> Alternative sans mot de passe ni TOTP : l’administrateur peut enregistrer une **passkey** (page Sécurité) et s’y connecter ensuite avec le bouton « Se connecter avec une passkey ».

> Si vous avez perdu la clé TOTP et les codes de secours en environnement de test : arrêtez le backend, supprimez la base (`DROP DATABASE hirelink;` puis recréez-la, §3) et relancez : l’administrateur de démonstration sera recréé et vous redemandera l’activation.

## 15. Sécurité — scénarios à exécuter

Ces scénarios utilisent le candidat `y.idrissi@outlook.com` (mot de passe initial `Candidat123!`). La page **Sécurité** est accessible dans le menu de chaque rôle.

### 15.1 Page Sécurité et mot de passe

1. Connexion de `y.idrissi@outlook.com`. La page de connexion propose « Se connecter avec une passkey » et « Mot de passe oublié ».

   ![s-connexion-passkey-oublie](guide-captures/141-s-connexion-passkey-oublie.png)

2. Ouvrez le menu **Sécurité** : mot de passe, double authentification, passkeys, appareils connectés, mes données.

   ![s-page-securite](guide-captures/142-s-page-securite.png)

3. Cliquez « Changer le mot de passe », saisissez l’ancien (`Candidat123!`) et un nouveau trop court (`azerty`).

   ![s-changer-mdp-saisie](guide-captures/143-s-changer-mdp-saisie.png)

4. Refus : « Mot de passe trop court : 12 caractères minimum » ; la fenêtre reste ouverte.

   ![s-mdp-faible-refuse](guide-captures/144-s-mdp-faible-refuse.png)

5. Saisissez `Cedre-Bleu-Montagne-2026` : « Mot de passe modifié. Vos autres appareils ont été déconnectés. »

   ![s-mdp-modifie](guide-captures/145-s-mdp-modifie.png)


### 15.2 Double authentification TOTP (optionnelle pour candidat/recruteur)

1. Cliquez « Activer » (la ré-authentification par mot de passe est demandée) : QR code et clé manuelle.

   ![s-totp-qr](guide-captures/146-s-totp-qr.png)

2. Un mauvais code est refusé.

   ![s-totp-code-faux](guide-captures/147-s-totp-code-faux.png)

3. Avec le bon code, 10 codes de secours s’affichent (à noter).

   ![s-totp-codes-secours](guide-captures/148-s-totp-codes-secours.png)

4. La double authentification est « Activée ».

   ![s-totp-active](guide-captures/149-s-totp-active.png)

5. Déconnectez-vous puis reconnectez-vous : le code est demandé.

   ![s-connexion-demande-code](guide-captures/153-s-connexion-demande-code.png)

6. Un faux code est refusé.

   ![s-connexion-code-faux](guide-captures/154-s-connexion-code-faux.png)

7. Avec un code de secours : connexion réussie (ce code est maintenant consommé).

### 15.3 Passkeys (optionnelles pour candidat/recruteur)

1. Sur la page Sécurité, « Ajouter une passkey » (ré-authentification), nommez-la.

   ![s-passkey-ajout](guide-captures/150-s-passkey-ajout.png)

2. Le navigateur demande Windows Hello / empreinte / code PIN, puis la passkey est listée.

   ![s-passkey-ajoutee](guide-captures/151-s-passkey-ajoutee.png)

3. Déconnectez-vous, cliquez « Se connecter avec une passkey » (sans saisir d’e-mail) : vous entrez directement.

   ![s-connexion-passkey-ok](guide-captures/155-s-connexion-passkey-ok.png)

4. Dans la liste « Appareils connectés », chaque session est visible (appareil, IP, activité). **Déconnecter** ferme une session précise, **Déconnecter les autres** garde seulement celle-ci, **Se déconnecter partout** ferme tout. Une session fermée ne peut plus se renouveler : son prochain accès renvoie à la page de connexion.

   ![s-appareils](guide-captures/152-s-appareils.png)


> Passkeys et ordinateur de test : le test automatisé a utilisé un authentificateur virtuel de Chrome. Sous Windows, le vrai dispositif (Windows Hello) est utilisé ; il exige l’adresse http://localhost:5500 (pas d’adresse IP).

### 15.4 Mot de passe oublié

1. Page de connexion → « Mot de passe oublié » → saisissez l’e-mail.

   ![s-oublie-formulaire](guide-captures/156-s-oublie-formulaire.png)

2. Message **identique que le compte existe ou non** (aucune fuite d’information).

   ![s-oublie-envoye](guide-captures/157-s-oublie-envoye.png)

3. En développement, le lien de réinitialisation est affiché dans la **console du backend** (ligne `MAIL simulé … token=…`). Ouvrez-le.
4. Un mot de passe courant (`password1234`) est refusé.

   ![s-reinit-faible](guide-captures/158-s-reinit-faible.png)

5. Saisissez `Olivier-Rouge-Horizon-2026`.

   ![s-reinit-saisie](guide-captures/159-s-reinit-saisie.png)

6. Mot de passe modifié ; toutes les sessions existantes sont fermées ; le lien ne peut plus être réutilisé et expire après 30 minutes.

   ![s-reinit-ok](guide-captures/160-s-reinit-ok.png)


### 15.5 Verrouillage après échecs de connexion

1. Sur la page de connexion, essayez 5 mauvais mots de passe pour `i.chraibi@gmail.com`.

   ![s-verrouillage-5-echecs](guide-captures/161-s-verrouillage-5-echecs.png)

2. Au 6ᵉ essai, **même avec le bon mot de passe** (`Candidat123!`), le compte est verrouillé 15 minutes (réponse 429 avec l’en-tête `Retry-After`).

   ![s-verrouillage-actif](guide-captures/162-s-verrouillage-actif.png)


> Le compteur est en mémoire : pour déverrouiller immédiatement en test, redémarrez le backend.

### 15.6 Contrôle d’accès : 403 sur les données d’une autre organisation

Le recruteur d’OCP Group demande la liste des candidatures d’une offre d’Atlas Digital. Résultat réel obtenu pendant le test (exécuté avec des requêtes HTTP, sans interface car aucun bouton ne propose cette action) :

```
GET /applications/by-offer/1 (recruteur Atlas Digital, propriétaire) -> 200
GET /applications/by-offer/1 (recruteur OCP Group, autre organisation) -> 403 {"status":403,"error":"Forbidden","message":"Hors organisation (R02)","timestamp":"2026-10-27T23:37:48.554828013Z"}
```

Pour le refaire sous Windows (PowerShell), après avoir récupéré un jeton via `POST /api/v1/auth/login` :

```
$r = Invoke-RestMethod -Method Post -Uri http://localhost:8081/api/v1/auth/login -ContentType 'application/json' -Body '{"email":"recrutement.ocpgroup@hirelink.ma","password":"Recruteur123!"}'
try { Invoke-WebRequest -Uri http://localhost:8081/api/v1/applications/by-offer/1 -Headers @{Authorization = "Bearer $($r.accessToken)"} } catch { $_.Exception.Response.StatusCode.value__ }
# -> affiche 403 (Hors organisation). Un nombre 200 signifierait que le contrôle d’accès ne fonctionne pas.
```

### 15.7 Journal d’audit (administrateur)

1. Connecté en administrateur, ouvrez `Journal d’audit` (http://localhost:5500/app/admin.html#audit) : connexions réussies/échouées, verrouillages, changements de mot de passe, MFA, accès refusés (403), etc., avec date, acteur, adresse IP et détail. Filtrable par type d’événement.

   ![s-audit-journal](guide-captures/165-s-audit-journal.png)


### 15.8 Autres contrôles à connaître

- Session : jeton d’accès de 15 minutes, renouvelé silencieusement par un cookie `HttpOnly` ; inactivité de 2 heures ou 7 jours au total = reconnexion ; réutilisation d’un ancien jeton = session révoquée.
- Téléversements : CV (PDF, PNG, JPG, WEBP, TXT) et logos (PNG, JPG, WEBP, GIF) vérifiés **par leur contenu**, pas par leur extension ; au-delà de 8 Mo, erreur 413.
- Toute saisie affichée à l’écran est échappée (test d’injection `"><i class=…>` : rien n’est exécuté).
- Détails complets, table OWASP/ASVS, limites : `docs/SECURITE.md`.

## 16. Dépannage

| Symptôme | Cause probable | Solution |
|---|---|---|
| `Port 8081 was already in use` | Un ancien backend tourne encore | `netstat -ano \| findstr :8081` puis `taskkill /PID <numéro> /F`, ou arrêtez l’exécution dans IntelliJ |
| `Port 5500` déjà utilisé / « Address already in use » | Un ancien serveur Python | `netstat -ano \| findstr :5500` puis `taskkill /PID <numéro> /F`, ou lancez `python -m http.server 5501` (et ouvrez http://localhost:5501 en ajoutant cette origine dans `HIRELINK_CORS_ORIGINS`) |
| `Access denied for user 'root'@'localhost'` | Mot de passe MySQL incorrect | Corrigez `spring.datasource.password` (ou `application-local.properties`), vérifiez que MySQL tourne (services Windows) |
| `Communications link failure` / `Connection refused` | MySQL arrêté ou autre port | Démarrez le service MySQL ; vérifiez le port 3306 |
| `Unknown database 'hirelink'` | Base non créée | Faites le §3 (ou laissez `createDatabaseIfNotExist=true` la créer) |
| `release version 21 not supported` / `invalid target release` | Mauvaise version de Java | `java -version` et `mvn -v` doivent indiquer 21 ; IntelliJ → Project Structure → SDK 21 ; vérifiez `JAVA_HOME` |
| Page blanche / liste vide / « Failed to fetch » | Backend arrêté, ou page ouverte par double-clic | Vérifiez http://localhost:8081/api/v1/health ; ouvrez le site via http://localhost:5500 (pas `file://`) |
| Erreur de console « blocked by CORS policy » | Site ouvert sur une autre adresse que `localhost:5500` | Utilisez http://localhost:5500 ou ajoutez votre origine dans `HIRELINK_CORS_ORIGINS` |
| Connexion refusée juste après le démarrage | Les comptes de démonstration ne sont pas encore créés | Attendez le bandeau « comptes de démonstration » dans la console |
| « Trop de tentatives » | Verrouillage anti-force-brute (15 min) | Attendez, ou redémarrez le backend (compteurs en mémoire) |
| Google : erreur 403 / console `[GSI_LOGGER]: The given origin is not allowed for the given client ID` | L’identifiant client Google du projet n’autorise pas `http://localhost:5500` | Sans importance pour les tests : utilisez e-mail + mot de passe. Pour activer Google : créez votre identifiant OAuth, ajoutez `http://localhost:5500` aux « origines JavaScript autorisées » et définissez `HIRELINK_GOOGLE_CLIENT_ID` (voir `docs/GOOGLE_SETUP.md`). Ce message ne casse rien |
| Le code TOTP est « invalide » | Horloge décalée ou code déjà utilisé | Synchronisez l’heure Windows ; attendez les 30 secondes suivantes ; sinon un code de secours |
| Passkey : « non supportée » | Adresse autre que localhost, ou navigateur trop ancien | Utilisez http://localhost:5500 avec Chrome/Edge récent |
| `mvn` n’est pas reconnu | Maven absent du PATH | Utilisez IntelliJ, ou ajoutez Maven au PATH (§1.3) |
| Aucun code OTP reçu par e-mail | Aucun SMTP configuré (normal en développement) | Le code est dans la console du backend (`OTP [...] → ...`) ; pour recevoir de vrais e-mails, voir §5.1 |
| Gmail : `Username and Password not accepted` / `Authentication failed` dans la console | Mot de passe habituel utilisé au lieu d’un mot de passe d’application, ou validation en 2 étapes inactive | Créez un mot de passe d’application (§5.1) et collez-le sans espaces dans `spring.mail.password` |
| Les e-mails n’arrivent pas, la console affiche `Envoi d'e-mail impossible` | Serveur SMTP injoignable ou identifiants refusés ; l’application continue de fonctionner | Consultez Administration → E-mails envoyés (colonne État) et la console ; vérifiez host, port 587 et identifiants |
| Notifications qui n’apparaissent qu’après quelques secondes | Le flux temps réel (SSE) est bloqué par un proxy ou un antivirus ; l’application bascule seule sur l’interrogation toutes les 45 s | Aucune action requise ; en cas d’usage derrière un proxy, autorisez les connexions longues vers `/api/v1/notifications/stream` |
| Un logo d’entreprise n’apparaît pas (initiale à la place) | Adresse `http://` (bloquée par la politique de sécurité), image supprimée ou illisible | Renvoyez le logo depuis *Entreprise & équipe*, ou utilisez une adresse `https://` ; formats PNG/JPEG/WEBP/GIF, 2 Mo maximum |
| Au démarrage : `Data truncated for column 'status'` | L’ancienne colonne `applications.status` n’a pas pu être étendue | Exécutez la commande `ALTER TABLE applications MODIFY status ENUM(…)` donnée au §3, puis redémarrez |

## 17. Récapitulatif de ce qui a été exécuté pour écrire ce guide

- Base vide recréée avant chaque campagne, backend démarré depuis le jar compilé, frontend servi par `python -m http.server 5500`.
- Parcours navigateur automatisés (Chrome) : candidat (§8), recruteur (§9), hiring manager (§10), parcours croisés (§11), nouveau recruteur + vérification admin (§12), administrateur avec activation TOTP (§13–14), sécurité (§15, avec une passkey virtuelle). Chaque capture est issue de ces exécutions.
- Tests d’API automatisés (plus de 250 vérifications) : authentification, sessions, MFA, passkeys, contrôles d’accès, téléversements, audit, profil `prod`, expiration des sessions et des liens — voir `docs/SECURITE.md`.
- Version intégrée (§18) : 23 tests unitaires Maven (`mvn package`), balayage de 43 pages (publiques et de chaque rôle), page d’accueil vérifiée sur ordinateur, mobile, avec animations réduites et sans JavaScript, parcours navigateur et API pour les trois rôles (recruteur, candidat, administrateur), deux navigateurs simultanés pour les notifications en temps réel, balayage de toutes les pages sans erreur JavaScript ni violation de la politique de sécurité (CSP), envoi d’e-mails vers un récepteur SMTP local, mise à jour automatique d’une base de l’ancienne version.
- Non exécuté : installation sur un vrai Windows, MySQL 8 (testé avec MariaDB 10.11), envoi d’e-mails vers un vrai compte Gmail (testé avec un récepteur SMTP local), connexion Google avec un identifiant client autorisé, passkey avec un vrai authentificateur matériel.

## 18. Nouveautés : scénarios de test

Cette version regroupe trois chantiers en un seul projet : la recherche et les offres détaillées, la **nouvelle page d’accueil animée** et la **mascotte HireLink** (notifications, confirmations, états vides et célébrations). Cette partie couvre la recherche intelligente, les compétences classées, la correspondance avec le profil, les offres détaillées par type de contrat, les entretiens, les e-mails, l’embauche et les notifications en temps réel. Les comptes sont ceux du §7.

### 18.1 Recherche tolérante et compétences classées

1. Connectez-vous en **candidat** (`candidat@hirelink.ma`) → *Profil / CV* → champ « Compétences ». Tapez `pyhton` (faute volontaire) : **Python** est proposé en tête. Les accents et les majuscules sont ignorés (`resau` trouve « Réseaux ») et les synonymes aussi (`js` → JavaScript).

   ![n-competences-faute](guide-captures/223-n-competences-faute.png)

2. Utilisez ↑ ↓ pour naviguer, Entrée pour choisir, Échap pour fermer. Tapez une compétence inconnue (ex. `Zorglub`) puis Entrée : elle est ajoutée telle quelle.
3. Sous le champ, la rubrique « Votre profil en un coup d’œil » range automatiquement chaque compétence dans une boîte colorée (langages, frameworks, bases de données, DevOps, outils, méthodes, langues…). Les compétences inconnues vont dans **Autres**. Les recruteurs voient les mêmes boîtes dans le dossier d’un candidat.

   ![n-profil-boites](guide-captures/224-n-profil-boites.png)

4. Toutes les barres de recherche (offres, entreprises, candidats, pipeline, notifications, administration) classent par pertinence, tolèrent les fautes, surlignent le texte trouvé et affichent un message clair quand il n’y a aucun résultat.

   ![n-recherche-faute](guide-captures/227-n-recherche-faute.png)


### 18.2 Offres qui correspondent à mon profil

1. Candidat → *Offres*. Activez l’interrupteur **« Offres qui correspondent à mon profil »** : la liste ne garde que les offres dont les compétences recoupent les vôtres, avec un pourcentage de correspondance. Le choix est mémorisé d’une visite à l’autre.

   ![n-offres-filtre-profil](guide-captures/225-n-offres-filtre-profil.png)

2. Survolez (ou touchez) une carte d’offre : un extrait apparaît et la flèche se détache. La même carte est utilisée sur la page d’accueil, les pages entreprise, l’espace candidat et l’espace recruteur.

   ![n-carte-survol](guide-captures/226-n-carte-survol.png)


### 18.3 Créer une offre adaptée au type de contrat

1. Recruteur (`n.bennani@atlasdigital.ma`) → *Offres* → **Nouvelle offre**. Étape 1 : titre (suggestions de métiers), type de contrat, lieu (ville ou pays avec drapeau ; le Maroc est proposé en premier), mode de travail.
2. Choisissez **Stage** ou **PFE** : l’étape « Conditions » propose la *gratification mensuelle* (et non un « salaire »), la durée en mois (obligatoire), la convention de stage, l’embauche possible à l’issue ; pas de période d’essai.

   ![n-offre-stage](guide-captures/221-n-offre-stage.png)

3. Choisissez **CDI** : fourchette de salaire (brut/net, par mois/an), devise, période d’essai, début dès que possible ou date précise, avantages. Un champ obligatoire vide ou une fourchette incohérente affiche une erreur en ligne, sous le champ, et bloque le bouton « Continuer ».

   ![n-offre-cdi](guide-captures/222-n-offre-cdi.png)

4. Les types **CDD, Alternance, Freelance (tarif par jour ou par heure), Temps partiel, PFA, Stage d’été** ont leurs propres champs. Les champs qui ne concernent pas le type choisi ne sont jamais enregistrés. Les compétences demandées utilisent le même sélecteur tolérant que le profil (500 caractères au total).
5. Étape 4 : l’aperçu montre la carte telle que les candidats la verront (rémunération avec icône monnaie, lieu avec drapeau). Publiez : *Offres* affiche la grille ; cliquez une carte pour le détail, ou « Modifier » / « ⋯ » pour la fiche d’évaluation, la dépublication ou la suppression.

   ![n-recruteur-offres](guide-captures/233-n-recruteur-offres.png)


### 18.4 Entretiens : planifier, confirmer, reporter

1. Recruteur → *Pipeline* → carte d’un candidat à l’étape *Shortlist* → **Avancer** vers *Entretien*. Le formulaire d’entretien est obligatoire : mode (**sur site / visioconférence / téléphone**), nature (RH, technique, manager, final), date, heure, **fuseau horaire (Africa/Casablanca par défaut)**, durée, adresse ou lien de visio ou numéro, participants, consignes. Les erreurs s’affichent en ligne.

   ![n-entretien-formulaire](guide-captures/228-n-entretien-formulaire.png)

2. Validez : le candidat reçoit une notification, un e-mail avec le fichier **.ics** (ajout à l’agenda) et un bouton « Confirmer ma présence ». Dans *Mes candidatures*, il voit les détails de l’entretien et peut **confirmer** ou **indiquer qu’il n’est pas disponible** avec une note ; le recruteur est prévenu.
3. Recruteur → *Entretiens* : **Reporter** (nouvelle date) ou **Annuler** (motif obligatoire) : le candidat est prévenu par notification et e-mail, le fichier .ics est mis à jour. La veille de l’entretien, un rappel est envoyé aux deux parties.

### 18.5 Proposition, embauche et notifications en temps réel

1. Le pipeline compte désormais deux étapes de plus : **Proposition** (OFFER) puis **Embauché** (HIRED), en plus de Reçues, Présélection, Shortlist et Entretien. Faites avancer une candidature jusqu’à *Embauché* : le candidat reçoit une notification, un e-mail de félicitations, et voit une animation de célébration ; le recruteur aussi : la mascotte fête l’événement avec **toute l’équipe de recruteurs** côté recruteur, et dans la **tenue du candidat** côté candidat (une seule fois par embauche).

   ![n-embauche](guide-captures/232-n-embauche.png)

2. **Temps réel** : ouvrez deux navigateurs (ou une fenêtre privée) : recruteur dans l’un, candidat dans l’autre. Faites avancer une candidature : la cloche, la pastille et la liste du candidat se mettent à jour en moins d’une seconde, sans recharger. Plusieurs onglets du même compte reçoivent le même message ; un autre compte ne reçoit rien.

   ![n-notifications-temps-reel](guide-captures/230-n-notifications-temps-reel.png)

3. Se déconnecter dans un onglet ferme le flux ; les jetons du flux sont à usage unique et valables 30 secondes. Si le flux est coupé (proxy, réseau), l’application se reconnecte seule puis, en dernier recours, interroge le serveur toutes les 45 secondes.

### 18.6 Administration : e-mails envoyés et vérification d’une entreprise

1. Connectez-vous en administrateur (§14) → **E-mails envoyés** : liste des messages (destinataire, type, état *Envoyé / Échec / Journalisé*, date) avec recherche tolérante, et aperçu du message dans une fenêtre isolée. Les messages contenant un code ou un lien secret ne conservent pas leur contenu. Le journal est purgé après 30 jours.

   ![n-admin-emails](guide-captures/231-n-admin-emails.png)

2. Règle de vérification : modifier l’**ICE** ou la **raison sociale** d’une entreprise relance la vérification (et peut retirer le badge « vérifiée »). Modifier le reste de la fiche (site, présentation, secteur…) ne change **jamais** le statut de vérification.

### 18.7 Page d’accueil animée, mascotte et logos

1. Ouvrez http://localhost:5500/ : la **page d’accueil animée** (défilement fluide, animations légères) affiche les chiffres clés, les offres ouvertes et les entreprises qui recrutent. Les cartes d’offres sont exactement les mêmes que dans l’espace candidat, avec la **rémunération**, le **drapeau** du lieu et le délai de réponse ; survolez-en une : les pastilles laissent place à un extrait. Sur téléphone, tout reste visible sans survol.

   ![n-accueil](guide-captures/234-n-accueil.png)

2. La présentation par rôle de la page d’accueil ne montre que les espaces **candidat** et **recruteur** : aucun lien, texte ni bouton n’y mentionne l’administration. Le compte administrateur se connecte comme les autres, par le bouton **Se connecter** (§14), et accède à son espace une fois authentifié.
3. La page respecte le réglage « réduire les animations » du système (les effets sont supprimés) et reste lisible si JavaScript est désactivé.
4. Les notifications, confirmations et messages « aucun résultat » sont portés par la **mascotte** : une petite notification en bas à droite pour chaque événement (jamais deux fois la même, même quand la cloche se met à jour), une fenêtre de confirmation pour les actions sensibles. Exemple : *Entretiens* → **Annuler** : la mascotte demande le motif (obligatoire) avant d’envoyer l’annulation au candidat.

   ![n-mascotte-annulation](guide-captures/235-n-mascotte-annulation.png)

5. **Logos d’entreprise** : un logo envoyé depuis *Entreprise & équipe* (PNG, JPEG, WEBP ou GIF, 2 Mo maximum) ou donné par une adresse `https://…` s’affiche partout de la même façon : tuile blanche arrondie, image entière avec une petite marge, sur les cartes d’offres, la liste des entreprises, la page entreprise, le menu du recruteur et l’administration. Sans logo, ou si l’image ne se charge pas, l’initiale de l’entreprise est affichée. Les adresses `http://` (non chiffrées) sont refusées par la politique de sécurité du navigateur : utilisez `https://` ou l’envoi d’un fichier.

   ![n-logos](guide-captures/236-n-logos.png)


### 18.8 Corrections de comportement

- Une entreprise déjà vérifiée le reste quand on enregistre sa fiche sans toucher à l’ICE ni à la raison sociale.
- La ville du candidat est indépendante de ses *lieux souhaités* : modifier l’une n’écrase plus l’autre.
- « Premier emploi » est enregistré, relu et affiché de la même façon dans le profil, les paramètres et les données de démonstration.
- Nouvelle étape *Embauché* avec e-mails, notifications et célébration.
- Une série de notifications rapprochées ne fige plus la page (limite de trois notifications visibles).
