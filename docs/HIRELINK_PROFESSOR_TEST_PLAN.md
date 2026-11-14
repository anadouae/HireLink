# HireLink — Plan de test professeur (après clone)

**Durée estimée :** 45–75 minutes  
**Prérequis :** installation selon `HIRELINK_INSTALL_WINDOWS.md` (API `8081` + FE `5500` UP).

Comptes :

| Rôle | E-mail | Mot de passe |
|---|---|---|
| Candidat | `candidat@hirelink.ma` | `Candidat123!` |
| Recruteur | `recruteur@hirelink.ma` | `Recruteur123!` |
| Owner (facturation) | `n.bennani@atlasdigital.ma` | `Recruteur123!` |
| Admin | `admin@hirelink.ma` | `Admin123!` |

---

## 0. Santé

| # | Action | Attendu |
|---|---|---|
| 0.1 | [http://127.0.0.1:8081/api/v1/health](http://127.0.0.1:8081/api/v1/health) | `UP` |
| 0.2 | [http://127.0.0.1:5500/](http://127.0.0.1:5500/) | Page HireLink, pas d’erreur JS bloquante |
| 0.3 | Grille d’offres accueil | Cartes « Offre HireLink » après seed (bootstrap catalogue auto) |

---

## 1. Parcours candidat

| # | Action | Attendu |
|---|---|---|
| 1.1 | Connexion `candidat@…` via formulaire | Redirection espace candidat |
| 1.2 | Dashboard | KPIs / liens sans toast « Réseau indisponible » |
| 1.3 | Offres : recherche « Java », filtre pays | Liste filtrée |
| 1.4 | « Offres qui correspondent » (Pour toi) | Liste scorée, pas d’erreur 400 |
| 1.5 | Ouvrir une offre interne → Postuler (jusqu’à l’UI) | Assistant candidature ; **soumettre optionnel** |
| 1.6 | Mes candidatures | Historique / statuts visibles |

---

## 2. Parcours recruteur

| # | Action | Attendu |
|---|---|---|
| 2.1 | Connexion `recruteur@…` | Espace recruteur |
| 2.2 | Mes offres | Liste org Atlas |
| 2.3 | Pipeline | Kanban / colonnes chargées (HTTP 200) |
| 2.4 | Entretiens | Page accessible |
| 2.5 | Checkout / formules avec **ce** compte | Plans visibles ; devis payant peut répondre **403** (normal : non-OWNER) |

---

## 3. Facturation (Owner)

| # | Action | Attendu |
|---|---|---|
| 3.1 | Connexion `n.bennani@…` | Owner Atlas |
| 3.2 | [http://127.0.0.1:5500/checkout.html?plan=recruiter](http://127.0.0.1:5500/checkout.html?plan=recruiter) | Devis / paliers ; paiement **simulé** (pas de vraie CB) |

---

## 4. Parcours administrateur

| # | Action | Attendu |
|---|---|---|
| 4.1 | Connexion `admin@…` | Espace admin |
| 4.2 | Entreprises / utilisateurs / plans | Listes lisibles |
| 4.3 | Vue admin entreprises / plans | Données seed visibles |

---

## 5. Contrôles négatifs (qualité produit)

| # | Action | Attendu |
|---|---|---|
| 5.1 | Offre affichée « externe » | **Aucune** si connecteurs OFF (défaut) |
| 5.2 | Console navigateur | Pas de `SyntaxError` sur `landing-offers.js` |
| 5.3 | Fichier `application.properties` clôné | `startup-sync=false`, Jooble/ATS/Maroc/RemoteOK `false` |

---

## 6. Critères de réussite

**PASS professeur** si : 0.x OK, 1.1–1.4 OK, 2.1–2.4 OK, 4.1 OK, et au moins un flux candidature **ou** pipeline consulté.

**FAIL** si health DOWN, login cassé, grille catalogue vide après seed terminé, ou erreur serveur systématique.

---

## 7. Hors périmètre (ne pas exiger)

- Activation Jooble / Greenhouse / Lever / Ashby / RemoteOK
- Envoi SMTP réel sans configuration locale
- Paiement bancaire réel
- Import d’un dump SQL de l’auteur
