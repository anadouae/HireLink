# HireLink — Addendum Phase A (refonte UX/UI)

**Date :** 12 novembre 2026  
**Statut :** audit + décisions validées — implémentation Phase B en cours  
**Aucun commit / push** sans autorisation.  
**Base `hirelink` :** lecture seule pour cette vague ; écritures de test sur base isolée uniquement.

## 1. Décisions validées

1. Fusionner **Candidats** dans **Candidatures** sous onglet **Liste** (Kanban conservé).
2. Retirer uniquement l’onglet UI **Grille d’entretien IA** ; conserver API `POST /ai/interview-kit` + tests ; documenter l’écart **EF-73**.

## 2. Inventaire problèmes ? solutions

| Espace | Page | Problème | Cause | Solution | Impact | Risque | Priorité |
|--------|------|----------|-------|----------|--------|--------|----------|
| Recruteur | Candidats | Redondance | Même pipeline API | Onglet Liste + redirect | Nav | Faible | P0 |
| Recruteur | Entretiens | « Nature » ambigu | Regroup ? filtre | Libellés Mode / Offre / Période / Statut | Clarté | Faible | P1 |
| Recruteur | Agenda | Trop chargé | Stats + filtres empilés | Calendrier prioritaire ; Post-it conservé (EF-47) | UX | Faible | P1 |
| Recruteur | Assistant IA | Grille hors volonté ; crédits doublés ; synthèse pauvre | UI kit + carte crédits | Retrait UI grille ; synthèse structurée | Recentrage | Moyen | P0 |
| Candidat | Profil & CV | Hiérarchie faible | Formulaire puis CV | Mon profil \| Mon CV | Clarté | Moyen | P0 |
| Candidat | OCR | Promesse trompeuse | PDF texte seulement ; pas DOCX | Libellés honnêtes | Confiance | Faible | P0 |
| Candidat | Entreprises suivies | Liste « Toutes » | Directory + label | Suivre sur fiche/offre ; page suivies | Cohérence | Moyen | P1 |
| Public | company.html | Look daté ; pas Suivre | UI + écart EF-76 | En-tête + Suivre | Vitrine | Faible | P1 |
| Landing | Hero/meta | Vérifié vs réactivité | Deux badges ; ICE interne | Reformuler | Discours | Faible | P0 |
| Admin | Users/Orgs/Offres/… | Actions limitées | UI minimale | Détails lecture ; pas de delete destructif | Modération | Moyen | P1 |
| Admin | Mon compte | Lenteur | Waterfall API | Paralleliser | Perf | Faible | P2 |
| Billing | Checkout | Look démo | HTML basique | Checkout SaaS simulé | Soutenance | Faible | P1 |
| Auth | Inscription | Pas d’accords | Pas de pages légales ni serveur | CGU + privacy UI **et** validation serveur versionnée | RM12 | Moyen | P0 |

## 3. Accords d’inscription (exigence ajoutée)

### Constat

- Aucune page CGU / Politique de confidentialité dans le frontend.
- `VerifyRegisterOtp` / Google signup : aucun champ d’accord.
- `ConsentRecord` uniquement en dead-code (`docs/dead-code/consent-and-meta/`).
- Aucune finalité e-mail promotionnelle dans `MailService` ? **pas** de case marketing.

### Exigence d’implémentation

- Cases obligatoires CGU + confidentialité à l’inscription (candidat / recruteur).
- Liens + aperçu hover/tap ; pages dédiées sans perdre le formulaire.
- **Validation serveur** des versions courantes ; refus 400 sinon.
- Traçabilité : utilisateur, purpose, version document, date.
- Pas de faux textes juridiques définitifs : brouillons marqués « à valider ».

## 4. Écart CDC EF-73

La grille d’entretien structurée (EF-73) reste disponible via API pour les tests et une éventuelle réactivation, mais **quitte le parcours utilisateur** de l’Assistant IA au profit de la synthèse CV.

## 5. Limites d’observation visuelle

Computer Use bloqué le 12/11 (écran de verrouillage Windows). L’audit UI antérieur (`docs/rapport-audit-ui-exhaustif.md`) et l’inspection code/CDC servent de base Phase A. Les tests Phase C reprendront le navigateur réel.

## 6. Hors scope (validation séparée)

Suppression destructive, suspend org, Remote OK on, changement tarifs, DOCX/OCR réel, case marketing, désactivation API grille IA.
