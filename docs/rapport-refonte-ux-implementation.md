# HireLink — Rapport d’implémentation refonte UX/UI (Phase B–D)

**Date :** 12 novembre 2026  
**Branche de travail :** locale uniquement — **aucun commit / push** sans autorisation.  
**Addendum audit :** [`rapport-refonte-ux-phase-a.md`](rapport-refonte-ux-phase-a.md)

## 1. Décisions appliquées

1. Candidats fusionnés dans Candidatures (onglet **Liste** + redirect).
2. Grille d’entretien retirée de l’UI ; API + tests conservés ([`ecart-ef73-grille-entretien.md`](ecart-ef73-grille-entretien.md)).
3. Accords d’inscription : UI + **validation serveur** + table `legal_acceptances` versionnée.
4. Pas de case marketing (finalité absente).

## 2. Modifications réalisées (par espace)

### Auth / légal
- Pages brouillon `frontend/legal/cgu.html`, `frontend/legal/confidentialite.html`
- Cases obligatoires inscription (email + profil + Google signup)
- Backend : `LegalAcceptance`, `LegalConsentService`, DTO étendus, `GET /auth/legal`
- Affichage des accords dans Sécurité

### Recruteur
- Pipeline : Kanban | Liste ; nav sans « Candidats »
- Assistant : synthèse CV structurée ; crédits page retirés
- Entretiens : Mode / Offre / Période / Regrouper par Type
- Agenda : stats secondaires retirées ; filtres compactés ; Notes (ex-Post-it)

### Candidat / public
- Profil & CV : sélecteur Mon profil | Mon CV ; libellés extraction honnêtes
- Entreprises suivies : défaut « Suivies » ; follow sur `company.html`
- Landing : discours vérifié / réactivité clarifié

### Admin / paiement
- Offres & signalements : « Voir les détails »
- Sources externes : états clairs ; sync grisée si désactivée
- Compte : chargement profile + logins en parallèle
- Checkout : présentation SaaS, simulation explicite

## 3. Tests

| Zone | Résultat |
|------|----------|
| Maven | **147 tests, 0 failure — BUILD SUCCESS** (incl. `LegalConsentServiceTest`) |
| `GET /api/v1/auth/legal` | PASS après redémarrage démo (`cgu:2026-11-12` / `privacy:2026-11-12`) |
| Pages `legal/cgu.html`, `auth.html` | HTTP 200 sur 5500 |
| Refus accords | Couvert unitaire (400 si versions absentes/obsolètes) ; OTP invalide renvoie 401 avant création |
| Démo 8081/5500 | **UP** après redémarrage code UX |
| Computer Use | Bloqué (écran verrouillé) — non rejoué |

## 4. État données

`hirelink` après redémarrage : **126 users · 118 offers · 220 apps** ; table `legal_acceptances` créée (DDL update). Aucune inscription de test écrite sur la base principale.

## 5. Fichiers pivots

Backend : `LegalAcceptance.java`, `LegalConsentService.java`, `AuthDtos.java`, `AuthService.java`, `AuthController.java`, `application.properties`  
Frontend : `auth.*`, `legal/*`, `recruiter-pipeline.*`, `recruiter-assistant.*`, `candidate-profile.*`, `recruiter-interviews.*`, `recruiter-agenda.*`, `company.*`, `candidate-companies.*`, `admin.*`, `checkout.*`, `account.js`, `index.html`  
Docs : Phase A, EF-73, modèle économique, ce rapport

## 6. Risques / limites restantes

- Textes CGU / privacy = brouillons à valider juridiquement
- Comptes historiques sans ligne `legal_acceptances` (grandfathered)
- Synthèse CV dépend encore de la clé LLM pour un rendu riche
- Suppression destructive / suspend org / Remote OK / tarifs : hors scope
- Offres 118 vs 119 précédemment : à confirmer (pas d’écriture volontaire dans ce chantier)

## 7. Prochaine étape

Attendre votre autorisation pour commit / push éventuel des artefacts et du code.
