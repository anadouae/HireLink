# HireLink — API : offres, entretiens, notifications, e-mails

Base : `/api/v1`. Toutes les routes exigent un jeton (`Authorization: Bearer …`) sauf mention contraire. La documentation interactive complète est servie par Swagger (`/swagger-ui.html`, désactivé en production).

## Offres

`POST /offers` et `PUT /offers/{id}` (rôle RECRUTEUR) acceptent :

| Champ | Type / valeurs | Remarques |
|---|---|---|
| `title` | texte ≤ 160 | obligatoire |
| `description` | texte ≤ 10 000 | obligatoire |
| `contractType` | `CDI`, `CDD`, `Stage`, `PFE`, `PFA`, `Stage d'été`, `Alternance`, `Freelance`, `Temps partiel` | libellé conservé tel quel en base : les anciennes offres restent valables |
| `location` | texte ≤ 120 | ville ou pays (catalogue : `/catalog/suggest?kind=place`) |
| `requiredSkills` | liste séparée par des virgules, ≤ 500 caractères | normalisée par le référentiel de compétences |
| `compensationType` | `PAID`, `UNPAID`, `NEGOTIABLE`, `UNSPECIFIED` | `UNPAID` réservé aux stages (PFE, PFA, stage d'été inclus) |
| `salaryMin`, `salaryMax` | entiers 0 – 10 000 000 | `salaryMin` obligatoire si `PAID` ; `max ≥ min` (montant fixe si `max` vide ou égal) |
| `salaryPeriod` | `HOUR`, `DAY`, `MONTH`, `YEAR` | mensuelle par défaut ; imposée à `MONTH` pour la gratification d'un stage ; `DAY` / `HOUR` pour un freelance |
| `salaryBasis` | `GROSS`, `NET` | |
| `currency` | `MAD`, `EUR`, `USD` | |
| `durationMonths` | 1 – 60 | obligatoire pour CDD, stages, alternance, freelance (12 mois maximum pour un stage) ; ignorée pour CDI et temps partiel |
| `startAsap`, `startDate` | booléen, date | `startDate` ignorée si `startAsap` ; la date limite doit précéder la date de début |
| `agreementRequired`, `hiringPossible` | booléens | stages uniquement |
| `probationMonths` | 0 – 12 | CDI et CDD uniquement |
| `schedule` | texte ≤ 120 | obligatoire pour l'alternance (rythme) et le temps partiel (volume horaire) |
| `workMode` | `ONSITE`, `HYBRID`, `REMOTE` | |
| `experienceLevel` | `NONE`, `JUNIOR`, `INTERMEDIATE`, `SENIOR`, `EXPERT` | |
| `educationLevel` | `BAC`, `BAC_2`, `BAC_3`, `BAC_4`, `BAC_5`, `DOCTORATE` | |
| `positions` | 1 – 500 | |
| `deadline` | date | ne peut pas être dans le passé ; l'offre n'est plus affichée après cette date |
| `benefits` | texte ≤ 400 | avantages séparés par des virgules |

Les champs qui ne concernent pas le type de contrat sont ignorés (jamais enregistrés). Les réponses ajoutent, pour un candidat, `matchedSkills`, `missingSkills` et `matchesProfile`.

## Recherche et référentiels

| Route | Rôle | Description |
|---|---|---|
| `GET /skills/suggest?q=` | tous | compétences classées par pertinence (fautes, accents, synonymes) |
| `POST /skills/classify` `{skills}` | tous | regroupe une liste de compétences par catégorie (langages, frameworks, bases de données, DevOps, données, sécurité, outils, design, méthodes, métiers, langues, savoir-être, `OTHER`) |
| `GET /catalog/suggest?kind=job\|place\|nationality\|school\|contract\|mode\|level&q=` | tous | suggestions classées ; `place` renvoie un drapeau (emoji), Maroc en premier |
| `GET /catalog/places` | tous | catalogue complet des lieux (ville, pays, drapeau) |

## Candidatures et entretiens

Étapes : `SUBMITTED` → `SCREENING` → `SHORTLISTED` → `INTERVIEW` → `OFFER` → `HIRED`, ou `REJECTED` / `WITHDRAWN`.

| Route | Rôle | Description |
|---|---|---|
| `PATCH /applications/{id}/status` | RECRUTEUR | change l'étape ; vers `INTERVIEW`, le corps contient `interview` ; `HIRED` notifie et écrit au candidat |
| `PUT /applications/{id}/interview` | RECRUTEUR | reporte l'entretien |
| `POST /applications/{id}/interview/cancel` `{reason}` | RECRUTEUR | annule (motif obligatoire) |
| `POST /applications/{id}/interview/answer` `{available, note}` | CANDIDAT | confirme ou indique une indisponibilité |
| `GET /applications/{id}/interview.ics` | le candidat ou un membre de l'organisation | fichier calendrier |

`interview` : `type` (`ONSITE`, `VISIO`, `PHONE`), `kind` (`HR`, `TECHNICAL`, `MANAGER`, `FINAL`), `startsAt` (heure locale ISO), `timeZone` (défaut `Africa/Casablanca`), `durationMinutes` (10 – 480), `location`, `mapUrl`, `videoUrl`, `phone`, `participants`, `instructions`. L'adresse (`location`) est obligatoire sur site, le lien en visio, le numéro au téléphone ; le créneau ne peut pas être dans le passé ; `timeZone` doit appartenir à la liste prise en charge.

## Notifications en temps réel (SSE)

1. `POST /notifications/stream-ticket` renvoie `{ticket}` : à usage unique, valable 30 secondes, lié à l'utilisateur.
2. `GET /notifications/stream?ticket=…` (sans en-tête `Authorization`) ouvre un flux `text/event-stream` : événements `ready`, `notification` (JSON de la notification) et battement de cœur toutes les 15 s. Huit flux simultanés au maximum par utilisateur.
3. Le client se reconnecte avec un nouveau ticket (délai croissant) ; `GET /notifications` reste utilisé en secours (toutes les 45 s si le flux est coupé).

Chaque utilisateur ne reçoit que ses propres notifications. La déconnexion ferme le flux côté navigateur.

## E-mails

| Route | Rôle | Description |
|---|---|---|
| `GET /admin/emails?q=` | ADMINISTRATEUR | boîte d'envoi (destinataire, objet, type, état `SENT` / `FAILED` / `LOGGED`, date) |
| `GET /admin/emails/{id}` | ADMINISTRATEUR | contenu HTML d'un message (absent pour les messages contenant un code ou un lien secret) |
| `PATCH /me` `{emailNotifications}` | tous | préférence des e-mails de suivi ; les e-mails de sécurité sont toujours envoyés |

Configuration SMTP : variables `HIRELINK_MAIL_HOST`, `HIRELINK_MAIL_PORT`, `HIRELINK_MAIL_USERNAME`, `HIRELINK_MAIL_PASSWORD`, `HIRELINK_MAIL_FROM` (voir `.env.example`) ou fichier `application-local.properties` (voir `application-local.properties.example` et le §5.1 du guide d'installation). Sans serveur, les messages sont journalisés et visibles dans la boîte d'envoi.
