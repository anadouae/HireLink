# Acteurs, fonctionnalités et règles métier — HireLink

Document de référence Séance 1 (complément au cahier des charges).  
**Version 4.0** — CDC comportemental + IA dosée + Confiance Bilatérale + PLG (aligné sur `HireLink_Cahier_des_charges_v4.docx`).

## 1. Acteurs

### 1.1 Candidat
- Créer / maintenir profil (compétences, métiers, lieux, contrats, niveau).
- Importer un CV (OCR / extraction skills locale).
- Consulter offres matchées + alertes de compatibilité.
- Postuler via parcours multi-étapes (détail offre → CV → lettre opt. → photo opt. → envoi).
- Suivre le kanban de candidatures (mêmes états que le recruteur).
- Voir SLA employeur, détails entretien (type, date, lieu), prep IA freemium.
- Consulter son **score de fiabilité** (non bloquant) et générer un **lien de parrainage** (+1 crédit IA).
- Paramètres : thème sombre, notifications, crédits IA, facturation Premium.

### 1.2 Recruteur (organisation RH / entreprise — pas un cabinet juridique)
- Gérer fiche entreprise (ICE, vérification).
- Publier des offres avec compétences + **SLA de réponse**.
- Inbox prioritaire (SLA, scoring Hot/Fit).
- Pipeline sécurisé : vérifications obligatoires avant avancée, scorecard tech/soft/culture.
- Organiser entretiens (PHONE / VISIO / ONSITE / ASYNC) avec créneau.
- Refus **uniquement** avec motif (anti-ghosting).
- Entreprise & équipe : badge de fiabilité embarquable, onboarding checklist, crédits IA, canaux d’attraction, sièges Premium.
- Détecteur de formulation genrée à la rédaction d’offre ; grille d’entretien structurée (crédits).
- Dashboard « petites victoires » + rappels SLA en intentions si-alors.
- Paramètres : billing Premium, notifs, dark mode.
- Aide RH distincte de l’aide candidat.

### 1.3 Administrateur
- Superviser comptes, cabinets/entreprises, plans.
- Indicateurs de respect des délais.
- Conformité données personnelles.

### 1.4 Collaborateur / Hiring manager (cible ATS exhaustif)
- Commenter une candidature **sans** avancer le pipeline.
- Participer à la scorecard panel.

## 2. Fonctionnalités (socle Séance 1 + enrichissement marché)

| ID | Fonctionnalité | Acteurs | Priorité |
|----|----------------|---------|----------|
| F01 | Auth (OTP email, mot de passe + œil, Google démo) | Tous | Haute |
| F02 | Profil candidat + OCR CV | Candidat | Haute |
| F03 | CRUD offres + SLA | Recruteur | Haute |
| F04 | Candidature multi-étapes (CV / lettre / photo) | Candidat | Haute |
| F05 | Pipeline ATS bilatéral (kanban) | Recruteur, Candidat | Haute |
| F06 | Matching local (Jaccard / couverture skills) | Système | Haute |
| F07 | Motif de refus obligatoire | Recruteur | Haute |
| F08 | Inbox SLA + scoring | Recruteur | Haute |
| F09 | Avancée sécurisée (checks + transitions verrouillées) | Recruteur | Haute |
| F10 | Organisation entretien typé | Recruteur | Haute |
| F11 | Assist IA freemium (pitch / prep entretien) | Candidat | Moyenne |
| F12 | Premium / billing (freemium → conversion) | Candidat, Recruteur | Moyenne |
| F13 | Paramètres + mode sombre | Tous | Moyenne |
| F14 | Aide in-app différenciée RH / candidat | Tous | Moyenne |
| F15 | Scorecard structurée (critères, poids, veto, moyenne panel) | Recruteur, Panel | Haute* |
| F16 | Multi-entretiens RH / Métier / Dirigeant + CR | Recruteur | Haute* |
| F17 | Checklist bloquante par étape (templates) | Recruteur | Haute* |
| F18 | Timeline immuable (ActivityLog append-only) | Système | Haute* |
| F19 | Rappels auto J-2 SLA / J-1 entretien | Système | Moyenne* |
| F20 | Feedback 360 (NPS candidat post-entretien) | Candidat | Moyenne* |
| F21 | Duplicate / blacklist soft (signal non bloquant) | Recruteur | Moyenne* |
| F22 | Offre salariale + acceptation électronique tracée | Recruteur, Candidat | Haute* |
| F23 | Commentaires hiring manager | Collaborateur | Moyenne* |
| F25 | Détecteur formulation genrée/excluante (locale) | Recruteur | Haute |
| F26 | Grille d’entretien structurée + crédits IA | Recruteur | Haute |
| F27 | Score / badge de fiabilité organisation | Recruteur, Candidat | Haute |
| F28 | Parrainage + onboarding checklist org (PLG) | Candidat, Recruteur | Moyenne |
| F29 | Dashboard « petites victoires » + rappels si-alors | Recruteur | Moyenne |

\* Modules du socle **HireLink ATS exhaustif** (`hirelink-ats/`) — process + preuves (au-delà d’un kanban).

## 3. Règles métier

| ID | Règle |
|----|-------|
| RM01 | Une seule candidature active par couple (candidat, offre). |
| RM02 | Statuts : SUBMITTED → SCREENING → SHORTLISTED → INTERVIEW → OFFER → ACCEPTED / REJECTED / WITHDRAWN. |
| RM03 | Passage REJECTED : motif obligatoire (non vide). |
| RM04 | Offre publiée : `response_sla_days` ≥ 1. |
| RM05 | Matching **local** — aucune API IA payante pour le livrable académique. |
| RM06 | Seul le propriétaire de l’offre (ou admin) avance / refuse. |
| RM07 | Avancée d’étape : vérifications identité + CV lu + SLA (API). |
| RM08 | Passage INTERVIEW : type + date/heure obligatoires. |
| RM09 | ATS exhaustif : pas d’avance si checklist obligatoire incomplète ; veto scorecard bloque. |
| RM10 | Timeline append-only : pas d’update/delete des ActivityLog. |
| RM11 | Blacklist soft : signal historique, jamais bloquant auto. |
| RM12 | Loi 09-08 / CNDP : consentement, minimisation, export, rétention. |
| RM13 | SGBD projet pedagogical principal : **MySQL** (HireLink app). Module ATS référence : PostgreSQL configurable. |

## 4. Pipeline (états — application HireLink)

```
SUBMITTED → SCREENING → SHORTLISTED → INTERVIEW → OFFER → ACCEPTED
                                              ↘ REJECTED
                         (à tout moment)      → WITHDRAWN (candidat)
```

## 5. Pipeline exhaustif (référence ATS — hirelink-ats)

```
SCREEN → ENTRETIEN_RH → ENTRETIEN_METIER → ENTRETIEN_DIRIGEANT → OFFRE → DECISION
```

Chaque transition exige checklist complétée ; les étapes entretien exigent absence de veto scorecard ; chaque action est tracée dans la timeline.
