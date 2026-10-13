# HireLink — Sécurité anti-fraude (modèle LinkedIn hybride)

## Principe
**Inscription libre ≠ Publication libre.**

Comme LinkedIn :
1. Création de compte / page entreprise **immédiate** (raison sociale + ICE + e-mail, **sans** documents).
2. Badge / droits avancés (**publier des offres**) **a posteriori**.

## Niveau 1 — Inscription + e-mail pro
- Champs : raison sociale, ICE, e-mail (+ OTP).
- Domaines gratuits (Gmail, Yahoo, Outlook…) → `verificationStatus = NEED_CORPORATE_EMAIL`, `companyVerified = false`.
- E-mail pro + match registre mock OMPIC → `REGISTRY_MATCH`, `companyVerified = true`.
- E-mail pro sans match → `WAITING_REGISTRY` (toujours non publié).

Code : `CompanyVerificationService`, `AuthService.applyCompanyIfRecruiter`.

## Niveau 1b — Gate publication (middleware métier)
Sur `POST/PUT /api/v1/offers` avec `published=true` :
- si `!companyVerified` → **HTTP 403** + message selon `verificationStatus`.
- Brouillons (`published=false`) autorisés.

Code : `JobOfferService.assertCanPublish`.

## Niveau 2 — Scan fake jobs
`FraudOfferScanService` :
- mots durs (argent à l’avance, WhatsApp obligatoire, formation payante…) → **blocage publication** ;
- mots soft → **quarantaine douce** (forcé en brouillon).

## Niveau 2b — Signalement communautaire
`POST /api/v1/offers/{id}/report` (candidat) :
- 1 signalement / utilisateur / offre ;
- à **3** signalements → `suspended=true`, `published=false`, notif recruteur.

UI : bouton **Signaler** sur `candidate-offers.js`.

## Admin
`PATCH /api/v1/admin/users/{id}/verify-company` → force `companyVerified=true` + `REGISTRY_MATCH`.

## Phrase orale
> « On s’inspire de LinkedIn : inscription libre sans documents ; la publication d’annonces est un droit vérifié a posteriori (e-mail pro, registre, scan contenu, signalements). »
