# HireLink CDC v4 — théorie → code

Source : `HireLink_Cahier_des_charges_v4.docx` (copie dans `docs/cahier-des-charges/`).

| Doctrine / UC v4 | Implémentation |
|---|---|
| 3 refus (IA décorative, kanban pauvre, asymétrie) | Pipeline sécurisé + motifs ; matching/IA locale ; scores fiabilité |
| Zeigarnik / gradient profil | `candidate.html` — anneau de complétion + nudge |
| Intentions si-alors (SLA) | `recruiter-home.js` — nudge « Si le dossier… » |
| Justice procédurale (rejet motivé) | Advance pipeline + motifs normalisés |
| Dotation crédits IA | `AiCreditService` — Free 5/20, Premium ∞ |
| 4 usages IA | Matching Jaccard ; profile-rewrite ; interview-kit ; language-check |
| Contrat Confiance Bilatéral | `ReliabilityService` + `/api/v1/reliability/me` |
| Badge organisation (PLG) | `/api/v1/growth/reliability-badge` + UI équipe |
| Parrainage candidat | `/api/v1/growth/referral-links` + paramètres candidat + `?ref=` |
| Onboarding org | `/api/v1/growth/onboarding-checklist` |
| Petites victoires | Dashboard recruteur `#wins-line` |
| Détecteur formulation genrée | `LanguageCheckService` à la création d’offre |

## Hors MVP académique (spécifié, non industrialisé)

- Flyway complet + OpenAPI springdoc synchronisé
- Digest notifications multicanal (préférences locales déjà présentes)
- RBAC Organisation / Memberships multi-sièges persisté (Premium invite = démo)
- Entités `reliability_events` / `referral_links` persistées (ledger mémoire processus pour démo)
