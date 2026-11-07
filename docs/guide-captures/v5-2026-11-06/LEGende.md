# Légende des captures — HireLink, série v5 (2026-11-06)

Prises de vue réelles de l'application en cours d'exécution (frontend `http://localhost:5500`,
API `http://localhost:8081/api/v1`), navigateur Chromium 1440×900, thème clair, données de
démonstration d'origine (aucune écriture : navigation et consultation uniquement).

| Fichier | Espace | Page | Ce que la capture démontre |
| --- | --- | --- | --- |
| 01-public-accueil.png | Public | frontend/index.html | Bloc « Les offres ouvertes » de la page d'accueil : grille de 106 offres publiques avec logos d'entreprise, badges de contrat, ville, fourchette de salaire et ancienneté. |
| 02-public-connexion.png | Public | frontend/auth.html | Écran de connexion à l'état neutre (onglet « Connexion » actif, champs e-mail et mot de passe vides, connexion par code e-mail, passkey et Google) — aucun message d'erreur. |
| 03-public-entreprise.png | Public | frontend/company.html?name=Atlas Digital | Fiche publique d'une entreprise : en-tête avec logo et badge « Identité vérifiée », statistiques (5 offres en ligne, 4 villes, dernière publication), présentation, présence, contrats, compétences recherchées et offres rattachées. |
| 04-public-securite.png | Public | frontend/security.html | Page publique « Sécurité & confiance » : engagements et garanties affichés aux visiteurs (données, comptes, modération). |
| 10-candidat-tableau-de-bord.png | Candidat | app/candidate-dashboard.html | Tableau de bord du compte candidat (Sara El Amrani) : synthèse personnelle, « L'essentiel », suivi des candidatures et prochaines étapes. |
| 11-candidat-offres.png | Candidat | app/candidate.html | Recherche d'offres côté candidat : panneau de filtres (ville, contrat, compatibilité, candidature) et liste complète des 106 offres avec score de compatibilité. |
| 12-candidat-detail-offre.png | Candidat | app/candidate.html | Volet détail d'une offre sélectionnée (« Développeur d'applications », Maroc Telecom) : rémunération, mode de travail, expérience, postes à pourvoir, avantages, compétences demandées et décomposition du score 71 %. Consultation seule, aucune candidature envoyée. |
| 13-candidat-mes-candidatures.png | Candidat | app/candidate-applications.html | Liste des 12 candidatures du candidat avec statuts (Envoyée, Présélection, Retirée, Entretien) et frise d'avancement du dossier ouvert (Envoyée → Présélection → Shortlist → Entretien). |
| 14-candidat-profil.png | Candidat | app/candidate-profile.html | Profil / CV du candidat : identité, recherche d'emploi, compétences et langues, progression du dossier. |
| 15-candidat-notifications.png | Candidat | app/notifications.html | Centre de notifications du candidat : historique des alertes de suivi de candidatures et des réponses d'entreprise. |
| 20-recruteur-tableau-de-bord.png | Recruteur | app/recruiter.html | Tableau de bord recruteur (Salma Berrada, Atlas Digital) : « À faire aujourd'hui », dossiers à traiter en priorité et prochains événements. |
| 21-recruteur-pipeline.png | Recruteur | app/recruiter-pipeline.html | Pipeline Kanban complet : indicateurs (22 dossiers actifs, 17 retards de réponse, 11 match ≥ 75 %, 5 entretiens, 17,9 % de conversion) et colonnes Reçues / Présélection / Shortlist / Entretien. |
| 22-recruteur-offres.png | Recruteur | app/recruiter-offers.html | Liste « Mes offres » du recruteur : offres publiées, leur statut et leurs compteurs de candidatures. |
| 23-recruteur-candidats.png | Recruteur | app/recruiter-candidates.html | Table de tous les candidats de l'organisation : colonnes de statut, compatibilité et suivi, avec tris et filtres. |
| 24-recruteur-entretiens.png | Recruteur | app/recruiter-interviews.html | Planificateur d'entretiens : onglets À venir / À replanifier / Passés, regroupement par date et fiche de l'entrevue du lundi 9 novembre. |
| 25-recruteur-agenda.png | Recruteur | app/recruiter-agenda.html | Onglet « Post-it » de l'agenda : tableau de notes personnelles (Idées source, Entretiens de la semaine, Relancer le client) avec couleurs, édition et réorganisation. |
| 26-recruteur-equipe.png | Recruteur | app/recruiter-team.html | Page Entreprise & équipe : fiche de l'organisation (Atlas Digital, vérifiée), accès à la fiche publique, rôles et membres de l'équipe. |
| 27-recruteur-assistant-ia.png | Recruteur | app/recruiter-assistant.html | Assistant IA : solde de crédits (14/20), formule en cours et outils de brouillon (grille d'entretien structurée, synthèse de CV). |
| 30-admin-tableau-de-bord.png | Administration | app/admin.html#dash | Vue « À traiter » du tableau de bord administrateur : offres à modérer, entreprises à vérifier, comptes désactivés, entonnoir plateforme et indicateurs globaux (130 comptes, 42 entreprises, 106 offres, 217 candidatures). |
| 31-admin-entreprises.png | Administration | app/admin.html#orgs | Gestion des entreprises : table complète des organisations avec ICE, plan, statut de vérification, effectif de l'équipe et nombre d'offres. |
| 32-admin-moderation.png | Administration | app/admin.html#moderation | Modération des offres : signalements en attente de décision, motif retenu (SUSPENDED_BY_REPORTS) et offres signalées avec actions de rétablissement ou suspension. |
| 33-admin-audit.png | Administration | app/admin.html#audit | Journal d'audit : connexions, accès refusés et exports, horodatés, avec filtres par type d'action. |
| 40-securite-compte.png | Compte | app/security.html | Sécurité du compte connecté : politique de mot de passe, double authentification, passkeys et liste des appareils/sessions actifs avec déconnexion. |
