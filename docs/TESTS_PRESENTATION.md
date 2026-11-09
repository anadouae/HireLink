# HireLink — Fichier de test guidé (tous les rôles)

Document de référence pour la séance de test. Chaque étape indique **ce qu’on clique** et **ce qu’on doit voir**.
Tous les libellés et tous les messages attendus ont été relevés dans l’application réellement exécutée
(navigateur automatisé + appels API réels), pas dans la spéculation. Si un libellé affiché diffère de ce fichier,
considérez-le comme une anomalie à signaler : le fichier et l’application doivent être identiques.

Durée totale d’exécution : **55 à 70 minutes**. Les scénarios se suivent dans l’ordre : le scénario 2 crée
l’offre sur laquelle le scénario 3 postule, et le scénario 4 traite cette même candidature.

---

## 1. Règles de lecture (à lire une fois, 2 minutes)

1. **Un seul compte à la fois dans le même navigateur.** La session est stockée par origine : si vous ouvrez
   deux comptes dans deux onglets du même navigateur, le second remplace le premier. Pour changer de rôle :
   menu profil (en haut à droite) → **Déconnexion**, puis reconnexion. Pour comparer deux rôles côte à côte,
   utilisez deux navigateurs différents (Chrome et Edge).
2. **Rechargez avec Ctrl+F5** sur la page ouverte avant chaque scénario (les fichiers du front sont versionnés,
   un simple F5 peut servir une ancienne copie).
3. **Adresse à taper : `http://localhost:5500`** (jamais `127.0.0.1`, jamais un fichier ouvert par double-clic) :
   les cookies de session et les passkeys exigent `localhost`.
4. **Ne testez pas un mot de passe au jugé.** Deux échecs de connexion font apparaître un contrôle anti-robot ;
   des échecs répétés verrouillent l’adresse locale 15 minutes. Utilisez uniquement les comptes du tableau §3.
5. Les étapes marquées **⚠ FACULTATIF** modifient un état difficile à rétablir ; elles sont décrites pour
   la documentation mais ne sont pas nécessaires à la validation.
6. Les étapes marquées **↺** sont conçues pour être annulées dans la foulée par l’étape suivante : le scénario
   laisse la base dans son état d’origine.

---

## 2. Démarrage

Terminal 1 — le serveur (laissez la fenêtre ouverte ; pour que les codes d’inscription y paraissent, voir §12) :

```
cd <dossier-du-depot>\HireLink\backend
mvn spring-boot:run -Dspring-boot.run.jvmArguments="-Dspring.mail.host= -Dhirelink.otp.log-to-console=true"
```

`<dossier-du-depot>` désigne l’endroit où vous avez cloné ou extrait HireLink : aucun chemin personnel n’est requis.
Sans `-Dhirelink.otp.log-to-console=true`, aucun code ni lien de sécurité n’est écrit dans la console (c’est la
valeur par défaut, choisie pour la sécurité) : il faut alors un SMTP (§5.1 du guide d’installation).

Vérification : ouvrez http://localhost:8081/api/v1/health → `{"application":"HireLink","status":"UP",…,"database":{"connected":true}}`.

Terminal 2 — le site :

```
cd <dossier-du-depot>\HireLink\frontend
python -m http.server 5500
```

Vérification : http://localhost:5500/ affiche la page d’accueil HireLink.

MySQL doit tourner (service `MySQL80` ou XAMPP → Module MySQL « Start »). C’est la seule dépendance externe.

---

## 3. Comptes de test

| Rôle présenté | E-mail à copier | Mot de passe | Entreprise | Particularité |
|---|---|---|---|---|
| Visiteur (aucun compte) | — | — | — | recherche publique, fiche entreprise publique |
| **Candidat** | `candidat@hirelink.ma` | `Candidat123!` | — | Sara El Amrani, profil complet, CV déposé, 12 dossiers |
| **Recruteur** | `recruteur@hirelink.ma` | `Recruteur123!` | Atlas Digital | Salma Berrada ; offres, pipeline, entretiens, agenda |
| **Responsable du poste** | `m.alaoui@atlasdigital.ma` | `Recruteur123!` | Atlas Digital | Mehdi Alaoui ; ne voit que les dossiers qui lui sont confiés |
| **Créateur de l’espace** | `n.bennani@atlasdigital.ma` | `Recruteur123!` | Atlas Digital | Nadia Bennani ; équipe, fiche entreprise, formule |
| Recruteur d’une autre entreprise | `recrutement.ocpgroup@hirelink.ma` | `Recruteur123!` | OCP Group | sert à prouver l’isolation entre entreprises |
| Second recruteur de la même équipe | `k.benali@atlasdigital.ma` | `Recruteur123!` | Atlas Digital | sert à la démonstration « changer un rôle » |
| **Administrateur** | `admin@hirelink.ma` | `Admin123!` | — | modération, entreprises, comptes, factures, journal |

Un compte de démonstration n’a **pas** de double authentification active : la page Sécurité est donc atteignable
directement par mot de passe.

## 4. Chiffres à retrouver au départ

Ouvrez http://localhost:5500/app/admin.html (compte administrateur), vue **Tableau de bord** :

| Indicateur | Valeur attendue |
|---|---|
| Comptes | 130 (dont 32 candidats, 97 recruteurs) |
| Offres | 117, dont **106 en vitrine** |
| Entreprises | 42, dont **40 avec au moins une offre en vitrine** |
| Dossiers de candidature | 217 |
| Signalements à trancher | 4 |
| Factures | 5 |

Le détail de répartition des dossiers : 61 « Reçues », 51 « Présélection », 23 « Shortlist »,
28 « Entretien », 47 refusés, 7 retirés. Atlas Digital occupe 4 places d’équipe sur 5.

---

## 5. Scénario 1 — Le site public, sans compte (5 min)

| # | Action | Vous devez voir |
|---|---|---|
| 1.1 | Ouvrez http://localhost:5500/ | Menu « Offres · Entreprises · Candidats · Métiers · Parcours · Formules » et le bouton **Se connecter** ; le titre du hero annonce le nombre d’offres ouvertes au Maroc. |
| 1.2 | Dans le widget, onglet **Trouver des offres**, tapez `java` dans « Rechercher un poste, une entreprise… », laissez « Lieu » vide, contrat « Type de contrat » par défaut, cliquez **Rechercher** | Une liste d’offres dont le titre ou l’entreprise contient « java », avec ville, contrat et entreprise. |
| 1.3 | Tapez `zzzzz` puis **Rechercher** | « Aucune offre pour cette recherche ». Aucun code d’erreur, aucune page cassée. |
| 1.4 | Repositionnez le champ sur « Lieu » = `Tétouan`, puis effacez le mot-clé | Les offres se rechargent filtrées sur Tétouan ; en effaçant, la liste complète revient. |
| 1.5 | Faites défiler jusqu’à « Les entreprises qui recrutent », cliquez sur une entreprise (par ex. OCP Group) | Fiche publique de l’entreprise : **logo** de l’entreprise, ses offres publiées, son secteur, sa ville. L’onglet du navigateur porte le nom de l’entreprise (« OCP Group — HireLink »). |
| 1.6 | Ouvrez http://localhost:5500/company.html?name=Entreprise%20Inexistante | « Aucune entreprise ne porte ce nom sur HireLink. » |
| 1.7 | Cliquez **Se connecter**, puis le lien **Inscription** | Onglets « Inscription » / « Connexion », étape « Email » avec le champ « Email » (placeholder `Entrez votre adresse e-mail`), bouton **Continuer**, et un bloc « Contrôle anti-robot » avec une image à recopier. |
| 1.8 | Cliquez **Connexion** | Champs « Email » (placeholder `vous@exemple.ma`) et « Mot de passe », boutons **Se connecter**, « Mot de passe oublié ? », « Recevoir un code par e-mail (sans mot de passe) », « Se connecter avec une passkey ». |

**Ce que ce scénario prouve :** la vitrine publique fonctionne sans authentification, les logos sont servis
en local, et les états vides sont rédigés (pas d’erreur technique).

---

## 6. Scénario 2 — Le recruteur et le cycle de vie d’une offre (12 min)

Connecté avec `recruteur@hirelink.ma` / `Recruteur123!`. Page de départ : http://localhost:5500/app/recruiter.html

| # | Action | Vous devez voir |
|---|---|---|
| 2.1 | Ouvrez l’adresse de départ | Titre « Bonjour, Salma », boutons **Ouvrir le pipeline** et **Nouvelle offre**, sections « À faire aujourd’hui », « À traiter en priorité », « Prochains événements », « Entonnoir de recrutement » (Reçues · Présélection · Shortlist · Entretien · Offre · Embauchés), « Indicateurs ». |
| 2.2 | Menu latéral → **Offres** | Liste des offres de l’équipe, puces d’état « En ligne », « Hors ligne », « Clôturée », « Archivée » ; filtres « Toutes · En ligne · Hors ligne · Clôturées · Archivées · Avec candidatures ». |
| 2.3 | Cliquez **Nouvelle offre** | Assistant en 4 étapes : « Poste », « Conditions », « Profil recherché », « Publication ». |
| 2.4 | Étape « Poste » : dans « Intitulé du poste » tapez `Développeur Back-End Java — Test soutenance`, puis cliquez **Continuer** sans rien d’autre | Refus en français : « Donnez un intitulé clair (3 caractères minimum). » **↺** Complétez alors la **Description** avec le texte ci-dessous, **Type de contrat** = `CDI`, **Ville du poste** = `Tétouan`, **Mode de travail** = `Hybride`. |
| 2.5 | Tapez un intitulé de 3 caractères seulement (`Dev`) puis remettez le titre complet | Le refus disparaît dès que l’intitulé atteint 3 caractères : la règle annoncée est bien celle du serveur. |
| 2.6 | Étape « Conditions » : **Rémunération** = `Rémunéré`, **Salaire minimum** `9000`, **maximum** `13000`, **Devise** `MAD`, cochez **Début dès que possible**, **Date limite de candidature** = une date environ 30 jours plus tard | Aucune erreur. Si vous laissez « Salaire minimum » vide, le refus est : « Indiquez le montant de la rémunération. » |
| 2.7 | Étape « Profil recherché » : **Compétences et langues demandées** = `Java, Spring Boot, SQL, Anglais`, **Niveau d’expérience** = `Junior`, **Nombre de postes** = `2` | Refus possible si la zone compétences est vide : « Ajoutez au moins une compétence. » |
| 2.8 | Étape « Publication » : cochez **Publier immédiatement**, cliquez **Créer l’offre** | L’offre est créée et portée en vitrine. Le bandeau « Reconnaissance affichée aux candidats » explique le badge de réactivité de l’entreprise. |
| 2.9 | Recherchez le titre `Test soutenance` dans la liste | La carte porte le badge **« En ligne »** et la mention « En ligne · 0 candidature ». |
| 2.10 | Sur cette carte, cliquez **Modifier**, changez le **Nombre de postes** en `3`, **Enregistrer les modifications** | Retour à la liste, la carte est inchangée d’aspect, l’enregistrement est accepté. |
| 2.11 | **↺** Cliquez **Dépublier**, vérifiez, puis **Publier** | Badge « Hors ligne » (invisible dans la recherche publique), puis retour à « En ligne ». |
| 2.12 | **↺** Cliquez **Clôturer**, confirmez « Clôturer cette offre ? » par le bouton **Clôturer**, vérifiez, puis **Rouvrir les candidatures** | Confirmation rédigée : « “Développeur Back-End Java — Test soutenance” n’acceptera plus de candidature ; vos 0 dossiers en cours restent accessibles. » Badges « Clôturée » puis « En ligne ». |
| 2.13 | Menu « ⋯ » de la carte → **Prolonger la date limite**, choisissez `15 jours`, **Prolonger** | La date limite de l’offre passe à +15 jours (visible en **Modifier**). Une valeur hors 1–180 jours est refusée : « Indiquez une prolongation entre 1 et 180 jours. » |
| 2.14 | Menu « ⋯ » → **Dupliquer** | Une seconde carte « (copie) … » apparaît, en brouillon (« Hors ligne »). |
| 2.15 | **↺** Sur la copie : menu « ⋯ » → **Archiver**, confirmez « Archiver cette offre ? » → **Archiver**, puis **Restaurer** | Badge « Archivée », la copie sort de la liste par défaut (filtre « Archivées » pour la revoir), puis revient. |
| 2.16 | Sur la copie : menu « ⋯ » → **Supprimer**, confirmez | La copie disparaît. Le journal d’administration garde la trace (scénario 7). |
| 2.17 | Menu latéral → **Entreprise & équipe**, onglet **Offres** | Les offres de toute l’équipe sont listées avec leur état, y compris celles créées par Nadia ou Karim : un recruteur voit l’entreprise, pas seulement ses propres offres. |

Texte de **Description** à coller à l’étape 2.4 (aucun mot déclencheur de l’anti-fraude) :

```
Conception et développement des services internes de la plateforme : API Java/Spring,
modèle de données, revues de code et documentation technique. Travail en équipe réduite,
en lien direct avec les responsables de poste.
```

**Ce que ce scénario prouve :** le cycle complet d’une offre (création, publication, retrait, clôture,
réouverture, prolongation, duplication, archivage, suppression) est réellement piloté par le serveur, avec
des bornes identiques à celles annoncées à l’écran.

---

## 7. Scénario 3 — Le candidat postule et suit son dossier (10 min)

Déconnexion, puis connexion avec `candidat@hirelink.ma` / `Candidat123!`.

| # | Action | Vous devez voir |
|---|---|---|
| 3.1 | Ouvrez http://localhost:5500/app/candidate-dashboard.html | Titre « Bonjour Sara », tuiles « Entretiens à venir », « Réponses attendues », « Profil complété », « Offres pour vous », puis « Suivi de mes candidatures » et « Prochaines étapes ». |
| 3.2 | Menu **Offres**, puis dans la recherche tapez `Test soutenance` | L’offre créée au scénario 2 apparaît (badge d’entreprise, ville, contrat, score de compatibilité). |
| 3.3 | Cliquez l’offre, puis **Postuler** | Assistant en 6 étapes : « Offre → Compétences → CV → Lettre → Disponibilité → Envoi ». |
| 3.4 | Étape Compétences : laissez les compétences proposées, **Continuer** | Rien à saisir : le dossier reste cohérent avec le profil. Sans sélection, refus : « Compétences — Sélectionnez au moins une compétence ». |
| 3.5 | Étape CV : choix **Profil HireLink** (le CV déjà déposé est réutilisé), **Continuer** | L’« Aperçu du CV » affiche le CV enregistré du profil. |
| 3.6 | Étape Lettre : tapez 2–3 phrases dans « Pourquoi ce poste, pourquoi vous ? », **Continuer** | Étape suivante acceptée. (Le bouton **Passer** est disponible : la lettre est facultative.) |
| 3.7 | Étape Disponibilité : **Immédiatement**, préavis `Aucun`, **Continuer** | Étape « Envoi » récapitulative. |
| 3.8 | Cliquez **Envoyer ma candidature** | Confirmation « Candidature envoyée — Suivez-la dans Mes candidatures ». |
| 3.9 | Menu **Mes candidatures**, filtre **En cours** | La nouvelle ligne, statut **« Envoyée »**, avec son pourcentage de compatibilité ; filtre disponible : Toutes · En cours · Entretien · Refusées · Retirées. |
| 3.10 | Cliquez la ligne, puis **Voir le détail du score** | Section « Compatibilité profil × offre » : compétences couvertes et attendues. |
| 3.11 | **⚠ FACULTATIF** Cliquez **Synthèse de mon CV** ou **Expliquer ce score** | Réponse de l’assistant en texte simple. Si les crédits IA du mois sont épuisés, une fenêtre « Crédits IA épuisés pour ce mois » s’ouvre avec **Voir les formules** / **Plus tard** : ce n’est pas une panne. |
| 3.12 | **↺** Sur une ancienne candidature au statut « Envoyée » ou « Présélection », cliquez **Retirer ma candidature**, confirmez par **Retirer** | La ligne bascule en **« Retirée »**, reste dans la liste (filtre « Retirées »), et le message précise : « Le dossier reste dans Mes candidatures… Vous pouvez postuler à nouveau sur cette offre. » |
| 3.13 | Sur une candidature « Embauché(e) » ou déjà « Retirée », le bouton **Retirer ma candidature** n’est pas proposé | À l’inverse, l’API refuserait : « Retrait impossible à cette étape (R11) ». Le bouton absent est la traduction exacte de la règle serveur. |
| 3.14 | Menu **Profil & CV** | Blocs « Recherche », « Compétences et langues », « Prévisualisation CV » (le CV est déjà là), « Analyse de votre CV », bouton **Upload CV** (formats acceptés `.pdf, .txt, .md`, 8 Mo maximum). |
| 3.15 | Menu **Entreprises**, recherchez `Atlas Digital`, bouton **Page publique** | La fiche publique de l’entreprise, identique à celle vue par un visiteur (scénario 1.5). |
| 3.16 | Cloche en haut à droite → **Voir toutes les notifications** | Liste filtrable (type, période), bouton **Tout marquer comme lu**. |

**Ce que ce scénario prouve :** le parcours candidat complet, de la découverte de l’offre jusqu’au retrait,
avec les mêmes limites côté écran et côté serveur.

---

## 8. Scénario 4 — Le pipeline, la notation et l’entretien (12 min)

Retour avec `recruteur@hirelink.ma` / `Recruteur123!`.

| # | Action | Vous devez voir |
|---|---|---|
| 4.1 | Menu **Pipeline** (http://localhost:5500/app/recruiter-pipeline.html) | Quatre colonnes : « Reçues », « Présélection », « Shortlist », « Entretien ». Sous-titre : « Suivez chaque dossier jusqu’à l’entretien · délai de 1ʳᵉ réponse · refus motivé. » |
| 4.2 | Sélecteur « Toutes les offres » → `Développeur Back-End Java — Test soutenance` | Une seule carte : le dossier créé au scénario 3, avec la pastille « Réponse sous 3 j » et son pourcentage de match. |
| 4.3 | Cliquez **Dossier** | Volet « Dossier · Sara El Amrani » structuré en « 1. Candidature », « 2. Profil », « 3. CV » (boutons **Aperçu** / **Télécharger**), « 4. Lettre de motivation », « 5. Entretien », « 6. Suivi », plus le bouton **Historique**. Fermez-le. |
| 4.4 | Cliquez **Avancer →** | Volet « Avancer · Sara El Amrani », « Confirmez le passage à l’étape suivante », trois cases : **« Identité vérifiée »**, **« Dossier relu »**, **« Délai de 1ʳᵉ réponse »**, compteur « 0 / 3 ». Le bouton **Confirmer l’étape** est **désactivé**. |
| 4.5 | Cochez deux cases, décochez-en une | Le compteur suit en direct (2 / 3, puis 1 / 3) : le bouton reste désactivé tant que les trois cases ne sont pas cochées. |
| 4.6 | Cochez les trois cases, puis **Confirmer l’étape** | La carte passe en colonne **« Présélection »**. |
| 4.7 | **Avancer →** à nouveau, trois cases, **Confirmer l’étape** | Passage en **« Shortlist »**. |
| 4.8 | Ouvrez le volet **Dossier**, cliquez **Planifier l’entretien** | Refus net : « Ce dossier n’est pas à l’étape entretien. » La planification n’est pas accessible hors étape — c’est la règle métier R18. |
| 4.9 | **Avancer →** une troisième fois, trois cases, **Confirmer l’étape** | Passage en colonne **« Entretien »**. |
| 4.10 | Volet **Dossier** → **Planifier l’entretien** | Champs : « Mode de l’entretien » (Visioconférence / Sur site), « Nature de l’entretien » (Entretien RH, Entretien technique, Avec le manager, Étude de cas, Rencontre avec l’équipe, Entretien final), « Durée », « Date », « Heure », « Fuseau horaire », « Participants (facultatif) », « Consignes pour le candidat (facultatif) », bouton **Planifier et prévenir**. |
| 4.11 | Choisissez **Sur site**, date dans quelques jours, heure de matinée, durée 45, « Adresse * » = `12 rue de la Marina, Tétouan`, puis **Planifier et prévenir** | Le volet se ferme ; le dossier porte un créneau. Sans adresse : « Indiquez l’adresse de l’entretien. » ; avec une date antérieure : « Cette date est déjà passée. » |
| 4.12 | Cliquez **Évaluer** | Volet « Scorecard · Sara El Amrani », bloc **« Ma notation · 1 à 5 »**, trois critères créés pour l’offre : « Technique », « Soft skills », « Culture fit ». Bouton **Enregistrer les notes** désactivé. |
| 4.13 | Attribuez une note de 1 à 5 sur les trois critères, puis **Enregistrer les notes** | Notification « Notes enregistrées ». En rouvrant **Évaluer**, les étoiles sont déjà placées : la note est conservée, pas dupliquée. |
| 4.14 | Menu **Agenda**, vue **Semaine** | Le créneau planifié apparaît. En-tête : boutons **Planifier un entretien** et **Nouvel événement**, vues « Mois · Semaine · Jour · Liste », filtres « Tous les modes », « Tous les types », case « Afficher les annulés ». |
| 4.15 | Cliquez le créneau, puis **Annuler l’entretien** (puis re-planifiez-le si vous souhaitez le garder en vitrine pour la suite) | Le badge passe à « Annulé » ; le candidat voit « Indisponibilité signalée » / l’annulation, et le bouton **.ics** de ce créneau ne répond plus (message : « Cet entretien a été annulé. »). |
| 4.16 | Menu **Notifications** | Une ligne par événement important : nouvelle candidature, changement d’étape, créneau planifié ; filtres par type et par période. |

**Ce que ce scénario prouve :** la machine d’états est appliquee par le serveur (aucun saut d’étape possible,
checklist obligatoire avant de avancer, entretien conditionné à l’étape « Entretien »), et la notation est
persistée par critère.

---

## 9. Scénario 5 — Le responsable du poste : ce qu’il peut faire, et ce qu’il ne peut pas (6 min)

Déconnexion, connexion avec `m.alaoui@atlasdigital.ma` / `Recruteur123!` (Mehdi Alaoui, même entreprise
qu’au scénario 4).

| # | Action | Vous devez voir |
|---|---|---|
| 5.1 | Ouvrez le tableau de bord | Le menu latéral **n’affiche pas** la rubrique « Offres » : un responsable du poste ne crée pas d’annonces. Rubriques visibles : Tableau de bord, Pipeline, Candidats, Entretiens, Agenda, Assistant IA, Notifications, Entreprise & équipe, Sécurité, Paramètres. |
| 5.2 | Menu **Pipeline**, dossier dont Mehdi est le responsable (par ex. un dossier de l’offre « Développeur Full-Stack Java — produit ATS ») | Le volet s’ouvre normalement ; il peut avancer un dossier, le refuser, le noter, planifier un entretien. |
| 5.3 | Dossier qui ne lui est pas confié, en essayant d’y aller par l’URL (`recruiter-pipeline.html` puis clic sur une carte d’une autre offre) | Refus métier : « Dossier non assigné (R14) ». Aucun affichage partiel, aucun écran technique. |
| 5.4 | Menu **Entreprise & équipe** | Phrase affichée à la place des boutons d’équipe : « Seul le créateur de l’espace invite, change un rôle ou retire un membre. » |
| 5.5 | Menu **Agenda** | Le bouton **Nouvel événement** n’apparaît pas (l’API refuserait : « Les Responsables du poste consultent l’agenda mais ne créent pas d’événements »). Les entretiens assignés restent visibles. |
| 5.6 | Menu **Paramètres**, onglet **Formules** | Boutons de changement de formule inaccessibles et la mention : « Seul le créateur de l’espace change la formule. Demandez-lui de le faire depuis cette page. » |

**Ce que ce scénario prouve :** la séparation des rôles internes n’est pas cosmétique : elle est appliquée
côté serveur, et l’interface ne propose simplement pas ce qui serait refusé.

---

## 10. Scénario 6 — Le créateur de l’espace : équipe, fiche d’entreprise, formule (10 min)

Déconnexion, connexion avec `n.bennani@atlasdigital.ma` / `Recruteur123!` (Nadia Bennani, OWNER).

| # | Action | Vous devez voir |
|---|---|---|
| 6.1 | Ouvrez http://localhost:5500/app/recruiter-team.html | Quatre onglets : **Présentation**, **Membres**, **Offres**, **Paramètres**. En-tête : boutons **Nouvelle offre** et **Inviter un membre d’équipe**. Présentation : indicateurs « Vérification », « Crédits IA », « Membres & Capacité », carte « Fiche publique » et « Rôles de l’équipe ». |
| 6.2 | Onglet **Membres** | Trois groupes : « Créateur de l’espace », « Recrutement », « Responsables du poste ». Chaque ligne : initiales, nom, e-mail, rôle. Nadia n’a aucun bouton sur sa propre ligne. |
| 6.3 | Cliquez **Inviter un membre**, saisissez `nouveau@atlasdigital.ma`, rôle **Recruteur**, puis **Envoyer l’invitation** | Barre de capacité avant l’envoi : « Capacité d’équipe : 4 / 5 (1 place disponible) » et pastille « Plan Accès (5 places) ». Bandeau de succès : « Invitation envoyée … » ; la ligne apparaît dans le groupe **« Invitations en attente »**, mention « Invitation envoyée · Recruteur ». |
| 6.4 | Sur cette ligne : **Renvoyer l’invitation** | L’envoi est accepté ; le délai de validité reste « trois jours ». |
| 6.5 | **↺** Sur cette ligne : **Annuler l’invitation**, confirmez « Annuler cette invitation ? » → **Retirer l’invitation** | La ligne disparaît et la capacité revient à « 4 / 5 (1 place disponible) ». |
| 6.6 | Invitation en double : **Inviter un membre** avec `k.benali@atlasdigital.ma` | Refus explicite, bandeau « Ajout impossible » (le compte est déjà membre). |
| 6.7 | Ligne de Karim Benali : sélecteur **Rôle** → `Responsable du poste` | Notification immédiate du changement ; Karim passe du groupe « Recrutement » à « Responsables du poste ». **↺** Remettez `Recruteur`. |
| 6.8 | **↺** Ligne de Karim : **Mettre en pause**, confirmez (« Mettre ce membre en pause ? » — « …gardera son rôle et l’historique de ses dossiers ») → le groupe « Accès en pause » apparaît → **Rétablir l’accès** | Après la mise en pause, la ligne porte « En pause · Recruteur ». Avec un compte en pause, ouvrez une seconde session ? Inutile : le rétablissement suffit à clore l’étape. |
| 6.9 | Ligne de Karim : **Retirer** avec la confirmation « Retirer ce membre ? » | **Ne confirmez pas** : le retrait est définitif et casse le scénario 6.10. Cliquez « Annuler ». |
| 6.10 | Onglet **Paramètres** | Carte « Fiche entreprise » : champs **Nom**, **ICE** (placeholder `15 chiffres (ex. 001234567000014)`), **Site web**, **Secteur**, **Ville**, **Couleur de l’entreprise**, **Couleur du logo**, **Présentation**, boutons **Choisir une image** / **Retirer**, **Enregistrer la fiche**, **Relancer la vérification**. Le bloc d’identité affiche « ICE vérifié : entreprise identifiée » (contrôle `001544289000035`). |
| 6.11 | Changez l’ICE en `123` puis **Enregistrer la fiche** | Refus : « Format ICE invalide » (15 chiffres exigés). **↺** Remettez `001544289000035` et enregistrez. |
| 6.12 | Cliquez **Page publique** | La fiche publique suchée par un visiteur, avec le logo de l’entreprise et ses offres en ligne — même contenu qu’en 1.5. |
| 6.13 | Menu **Paramètres**, onglet **Formules** | Deux formules « Accès » et « Premium · 299 MAD / mois », boutons **Activer Premium** / **Revenir à Accès** accessibles uniquement au créateur. **↺** Vous pouvez activer Premium puis revenir à Accès : les crédits IA passent de 5 à 40 puis à 5, la capacité d’équipe de 5 à 25 puis à 5. |
| 6.14 | **⚠ FACULTATIF — ne faites pas cette étape pendant la soutenance** : « Transférer le rôle de créateur » | La confirmation est explicite : « …deviendra le seul compte à gérer l’équipe… Vous deviendrez Recruteur ». Le transfert est réversible en reconnectant l’ancien créateur, mais il inverse les rôles de tout le reste du document. |

**Ce que ce scénario prouve :** la gouvernance d’un espace (invitation, capacité, rôle, pause, fiche
légale, formule) est réservée au créateur, et chaque borne annoncée à l’écran est contrôlée par le serveur.

---

## 11. Scénario 7 — L’administrateur de la plateforme (10 min)

Déconnexion, connexion avec `admin@hirelink.ma` / `Admin123!`. Console : http://localhost:5500/app/admin.html

| # | Action | Vous devez voir |
|---|---|---|
| 7.1 | Vue **Tableau de bord** | Les chiffres du §4 ; tuiles « À traiter » (Offres à modérer, Entreprises à vérifier, Nouvelles entreprises, Invitations en attente, Comptes désactivés) ; « Recherche transversale » ; « Entreprises à vérifier » ; « Entonnoir de la plateforme » ; « Indicateurs ». |
| 7.2 | « Recherche transversale » : tapez `Atlas` | Resultats groupés en « Comptes », « Entreprises », « Offres », « Candidatures », chacun avec un bouton **Ouvrir**. Tapez `qqqzzz` : « Rien qui ressemble à « qqqzzz ». » |
| 7.3 | Vue **Entreprises**, puce **À vérifier** | Les 2 entreprises non vérifiées ; bouton **Détails** → fenêtre avec « Créateur de l’espace. », « Équipe », « Plan », « Période de facturation », et le bouton **Marquer comme vérifiée** / **Retirer la vérification**. |
| 7.4 | **↺** Sur une entreprise à vérifier, **Marquer comme vérifiée**, puis rouvrez et **Retirer la vérification** | L’état bascule dans les deux sens et le compteur de la tuile « À traiter » suit. |
| 7.5 | Vue **Comptes**, puce **Candidats**, recherchez `Sara` | Pastille « Actif », bouton **Désactiver**. **↺** Désactivez puis **Réactiver** (confirmations « Désactiver ce compte ? » / « Réactiver ce compte ? »). |
| 7.6 | Vue **Modération** | En-tête : « n offre(s) signalée(s) · k signalement(s) à trancher ». La liste : « Chargé(e) de marketing digital » (Zellige Studio, 3 signalements) et « Développeur Full-Stack Java — produit ATS » (Atlas Digital, 1 signalement). Boutons par ligne : **Suspendre l’offre**, **Rétablir l’offre**, **Écarter le signalement**. |
| 7.7 | Sur « Développeur Full-Stack Java — produit ATS » : **Suspendre l’offre**, saisissez le « Motif visible par le recruteur » (ex. `Signalement traité : demande de complément de description`), confirmez | L’offre quitte la file et passe en **« Suspendue »** ; elle disparaît de la recherche publique ; le recruteur reçoit une notification « Offre suspendue » avec le motif saisi. |
| 7.8 | Connectez-vous avec `recruteur@hirelink.ma`, vue **Offres**, filtre **Toutes** | La carte porte le badge **« Suspendue »** et le bouton **Voir le motif** affiche la phrase exacte écrite à l’étape 7.7. |
| 7.9 | Revenez en administrateur, **Rétablir l’offre**, puis en recruteur cliquez **Publier** | ↺ L’offre est rétablie puis remise en vitrine : la base retrouve son état de départ (106 offres en ligne). Les signalements de cette offre sont tranchés (« Sanction confirmée » dans la table « Dernières décisions »). |
| 7.10 | Vue **Offres** | Puces « Toutes · En ligne · Suspendues · Hors ligne · Clôturées · Archivées · Signalées » et les actions **Dépublier**, **Rétablir**, **Suspendre**. Confirmation de retrait : « Retirer cette offre de la vitrine ? ». |
| 7.11 | Vue **Plans & factures** | Cinq factures (ex. `HL-…-Kasbah Analytics`, plan Premium, montant HT/TVA/TTC, statut). Filtres « Toutes · À encaisser · Payées · Avoirs ». Boutons par ligne : **Ouvrir**, **Marquer payée** / **Marquer non payée**, **Créer un avoir** (avec « Motif de l’avoir »). **↺** Marquez une facture payée puis re-marquez-la non payée. |
| 7.12 | Vue **Motifs de refus** | Le catalogue des 7 motifs utilisés par le pipeline (ex. « Compétences techniques insuffisamment alignées sur le poste »). **↺** **Ajouter** un motif `TEST_SOUTENANCE` / libellé `Motif créé pendant la soutenance`, vérifiez qu’il est proposé côté recruteur (scénario 4, volet **Refuser**), puis **Supprimer** (« Supprimer « TEST_SOUTENANCE » ? — Les refus déjà enregistrés conservent leur libellé. »). |
| 7.13 | Vue **Journal d’audit** | Liste horodatée : acteur, action, cible, détail, adresse IP. Filtre « Toutes les actions » avec les codes réels (`LOGIN_SUCCESS`, `STAGE_CHANGE`, `OFFER_PUBLISHED`, `OFFER_SUSPENDED`, `MEMBER_INVITED`, `ACCOUNT_ERASED`…). Boutons **Actualiser** et **Exporter CSV** (titre de l’action : « 2 000 derniers évènements, filtre en cours inclus »). |
| 7.14 | Filtrez le journal sur `OFFER_PUBLISHED` | Toutes les lignes portent le titre de l’offre concernée — y compris la publication faite au scénario 2.8 et celle du scénario 7.9. C’est la preuve écrite que le cycle de vie des offres est journalisé. |
| 7.15 | **Exporter CSV** | Le fichier `audit-hirelink-<date>.csv` est téléchargé et contient uniquement les lignes du filtre en cours. |
| 7.16 | Vue **E-mails envoyés** | Les messages que l’application a produits, avec leur état : « Envoyé », **« Journalisé (sans SMTP) »**, « Échec d’envoi ». Bouton **Voir** : aperçu du contenu. Avec le démarrage du §2, l’état est « Journalisé (sans SMTP) » : l’application ne prétend jamais avoir envoyé ce qu’elle n’a pas envoyé. |

**Ce que ce scénario prouve :** la modération, la vérification légale, la facturation, le catalogue des refus
et le journal d’audit sont des fonctions réelles de l’administration, pas des écrans décoratifs.

---

## 12. Scénario 8 — Erreurs et garde-fous volontaires (8 min)

Chaque ligne ci-dessous doit produire **la phrase attendue**, jamais une page blanche, jamais un `500`.

| # | Manipulation | Résultat attendu |
|---|---|---|
| 8.1 | **Inscription** d’un nouveau candidat : http://localhost:5500/auth.html?mode=register, e-mail `etudiant.test@soutenance.ma`, répondez au contrôle anti-robot | Le message « Si l’adresse est valide, un code vient d’être envoyé (en démo locale : voir la console du serveur). » s’affiche **et la fenêtre du terminal du backend** affiche, encadrée : `HireLink OTP [REGISTER] pour etudiant.test@soutenance.ma → ######`. Reportez ces six chiffres dans le champ « Code à 6 chiffres », puis **Vérifier le code**. |
| 8.2 | Complétez le profil (Prénom, Nom, Téléphone `+212600000000`, Ville `Tétouan`, Nationalité, Date de naissance, Mot de passe 12 caractères), puis **Accéder au dashboard** | Le nouveau candidat arrive sur un tableau de bord **vide** rédigé : « Aucune candidature » / « Postulez à une offre pour suivre ici chaque étape. » |
| 8.3 | Revenez sur l’inscription et resaisissez `etudiant.test@soutenance.ma` | Refus : « Ce compte existe déjà : utilisez votre mot de passe ou un code e-mail. » |
| 8.4 | Connexion avec `candidat@hirelink.ma` et un mot de passe volontairement faux **une seule fois** | Bandeau « Connexion refusée ». Au deuxième échec, le bloc « Contrôle anti-robot » apparaît avec une image à recopier : c’est le verrouillage progressif voulu (ne pas insister). |
| 8.5 | Connecté en **recruteur**, ouvrez une offre d’une autre entreprise par l’URL (`recruiter-pipeline.html` d’un dossier d’OCP) | Refus : « Hors organisation (R02) ». Aucune donnée d’une autre entreprise n’apparaît. |
| 8.6 | Dans l’assistant d’offre (scénario 2), mettez une **Date limite de candidature** antérieure à la **Date de début** | Refus : « La date limite doit précéder la date de début. » |
| 8.7 | Candidat : postulez **deux fois** à la même offre | Le bouton est devenu « Voir ma candidature » ; l’API refuserait « Candidature déjà existante (RM01) ». |
| 8.8 | Recruteur : planifiez un entretien sur un dossier encore en « Présélection » | Refus : « Ce dossier n’est pas à l’étape entretien. » |
| 8.9 | Recruteur : essayez d’avancer un dossier sans cocher les trois cases de la checklist | Bouton **Confirmer l’étape** grisé, inaccessible. Le serveur refuserait de toute façon la demande. |
| 8.10 | Double-clic rapide sur un bouton d’envoi (invitation ou publication) | Une seule ligne dans le journal d’audit (filtre §7.13) : les envois sont verrouillés pendant le traitement. |
| 8.11 | Modifiez l’URL pour ouvrir `/app/admin.html` avec une session candidat | Réorientation vers la page correspondant au rôle, ou message « Accès refusé. ». Pas d’écran à moitié vide. |
| 8.12 | Laissez l’onglet ouvert sans activité, puis cliquez une action après expiration | Message de session expirée : « Session expirée — Reconnectez-vous pour continuer. », puis retour à la page où vous étiez après reconnexion. |

**Ce que ce scénario prouve :** les bornes sont contrôlées côté serveur, les messages sont rédigés en français,
et l’inscription d’un candidat est possible à tout moment, même sans serveur de messagerie.

---

## 13. Ce que la séance laisse dans la base (à savoir, pas à exécuter)

| Créé / modifié | Où | Annulation |
|---|---|---|
| 1 offre de test « Développeur Back-End Java — Test soutenance » | Offres | menu « ⋯ » → **Supprimer** |
| 1 dossier de candidature de Sara, passé jusqu’à « Entretien », noté, avec créneau | Mes candidatures / Pipeline / Agenda | aucun : c’est un dossier réel comme les autres |
| 1 candidature du nouveau compte `etudiant.test@soutenance.ma` | Comptes /Pipeline | aucune action nécessaire |
| 1 invitation annulée, 1 rôle changé puis remis, 1 pause puis rétablissement | Entreprise & équipe | déja annulés par les étapes ↺ |
| 1 offre suspendue, rétablie, republiée ; 1 signalement tranché | Modération | déja annulé par 7.9, sauf le signalement passé à l’état traité |
| 1 motif de refus ajouté puis supprimé ; 1 bascule de facture ; 1 désactivation de compte | Motifs / Factures / Comptes | déja annulés par les étapes ↺ |
| Nouvelles lignes dans le journal d’audit | Journal | le journal n’est jamais effacé volontairement |

Pour repartir d’une base identique : `mysqldump -u root hirelink > sauvegarde-avant-test.sql` avant la séance,
et `mysql -u root hirelink < sauvegarde-avant-test.sql` après (le backend doit être arrêté pendant l’import).

---

## 14. Si une étape ne se passe pas comme écrit

| Symptôme | Cause la plus probable | Vérification |
|---|---|---|
| Page blanche, listes vides, « Failed to fetch » | Backend arrêté | http://localhost:8081/api/v1/health doit répondre |
| « Port 8081 was already in use » | Un ancien backend tourne | `netstat -ano \| findstr :8081` puis arrêter ce processus |
| Une page semble ancienne (libellé qui ne correspond plus à ce document) | Cache du navigateur | **Ctrl+F5** sur la page |
| Connexion impossible avec un compte du §3 | Frappe incorrecte ou verrouillage 15 min après échecs répétés | attendre 15 minutes, puis réessayer **une** fois avec l’adresse exacte du §3 |
| « Crédits IA épuisés pour ce mois » | Quota d’appels à l’assistant (5/mois en Accès) | non bloquant : les autres fonctions restent disponibles |
| « Contrôle anti-robot » à une connexion | Deux échecs déjà commis | recopier les caractères affichés, ou **Nouvelle question** |
| Code d’inscription jamais reçu | Messagerie réelle non configurée (normal) | lire le code dans la fenêtre du terminal du backend (§12, étape 8.1) |

---

## 15. Annexe — présenter avec de vrais e-mails (facultatif)

Pour que les invitations, convocations et liens de réinitialisation partent réellement : configurez un
compte SMTP (voir `docs/GOOGLE_SETUP.md`), puis lancez le backend **sans** désactiver la messagerie.
Les messages apparaissent alors côté admin, vue **E-mails envoyés**, avec l’état « Envoyé » au lieu de
« Journalisé (sans SMTP) ».

---

*Document aligné sur le code exécuté le 06/11/2026 : backend Spring Boot 3.3.13 sur le port 8081, front statique
sur 5500, base MariaDB `hirelink` (130 comptes, 117 offres, 106 en vitrine, 40 entreprises avec offres publiées).*
