# HireLink CDC v4 — théorie → code

Source : `HireLink_Cahier_des_charges_v4.docx` (copie dans `docs/cahier-des-charges/`).

Chaque ligne de ce tableau a été vérifiée dans le code du dépôt : la colonne « Implémentation » ne cite
qu'un fichier, une route ou une table réellement présente. Ce qui n'est pas construit est dit tel quel.

| Doctrine / UC v4 | Implémentation |
|---|---|
| 3 refus (IA décorative, kanban pauvre, asymétrie) | Pipeline sécurisé + motifs normalisés ; matching et IA calculés en local ; badge de réactivité mesuré (`ResponsivenessService`) |
| Zeigarnik / gradient profil | `frontend/js/apply-profile-gate.js` — complément de profil exigé avant la 1re candidature (barres d'étapes) ; `candidate-profile.js:530` — célébration « Profil à 100 % » |
| Intentions si-alors (SLA) | `recruiter-home.js` — tuile « Réponses à risque » sur `slaRemainingDays` / `slaBreached`, file triée par délai restant |
| Justice procédurale (rejet motivé) | Advance pipeline + motifs normalisés |
| Dotation crédits IA | `AiCreditService` — plafond lu dans `commercial_plans.credits` (Essentiel candidat 5, Performance 40, Accès recruteur 20, Premium 200) ; 1 usage IA = 1 crédit ; compteur dans `ai_usage_logs` |
| 4 usages IA | Matching Jaccard (`TextMatcher`) ; profile-rewrite ; interview-kit ; language-check |
| Contrat Confiance Bilatéral | `ResponsivenessService` + `GET /api/v1/applications/pace` — médiane réelle des premières réponses, jamais une promesse saisie à la main |
| Badge organisation (PLG) | Même service : badge « Réactif » / « Attentif » affiché sur les cartes d'offre (`offer-card.js:155`), la fiche entreprise (`company.js:107`), le dashboard recruteur (`recruiter-home.js:168`) ; filtre « entreprises réactives » (`candidate-home.js:85`) |
| Parrainage candidat | **Non implémenté** — aucune route `referral`, aucune table `referral_links`, aucun paramètre `?ref=` dans le front |
| Onboarding org | **Non implémenté** — pas de checklist persistée ; l'ouverture d'un espace se fait par la page Équipe (`recruiter-team.html`) et l'invitation nommée (`OrganizationService`) |
| Petites victoires | Dashboard recruteur `#wins-line` (`recruiter-home.js:63`) |
| Détecteur formulation genrée | `LanguageCheckService` à la création d'offre |

## Hors MVP académique (spécifié, non industrialisé)

- Flyway complet + OpenAPI springdoc synchronisé
- Digest notifications multicanal (préférences locales déjà présentes)
- RBAC Organisation / Memberships multi-sièges persisté (un seul créateur par espace, rôle transférable)
- Parrainage (`referral_links`) et checklist d'onboarding : spécifiés dans le cahier des charges v4, absents du code — rien n'est simulé en mémoire
