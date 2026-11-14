# HireLink — Rapport smoke frontend final (démo)

**Date :** 2026-11-14  
**Autorisation :** démarrage contrôlé frontend `5500` + validation navigateur avec backend `8081`  
**Mise à jour :** correction ciblée `landing-offers.js` L127 autorisée et **appliquée** (même jour).  
**Hors périmètre respecté :** pas de redémarrage `8081`/`5500`, pas de migration/écriture métier, pas d’activation connecteur, pas de commit/push, **aucun autre fichier produit modifié**.

---

## Verdict

**PASS** — démo HireLink complète accessible : `http://127.0.0.1:5500` + API `8081`. Grille d’offres publiques landing **affichée** (9 cartes catalogue, ex. OCP / Ingénieur procédés junior). Parcours candidat et recruteur **OK**. Compteurs métier **inchangés**. Connecteurs **OFF**.

Captures grille fonctionnelle : `docs/deploy-prep/fe-final-screenshots/18-landing-offers-grid.png` (page) · `18b-landing-offers-grid-focus.png` (focus `#public-offers`)

| Couche | État |
|---|---|
| Backend `8081` (pid 17648) | **UP** (non redémarré) |
| Frontend `5500` (Python `http.server`, pid 26104) | **UP** (non redémarré) |
| Parcours candidat / recruteur (navigateur réel) | **OK** |
| Compteurs métier | **inchangés** |
| Connecteurs externes | **tous OFF** |
| Landing — grille offres publiques | **OK** |

---

## 1. Prérequis & démarrage FE

| Check | Résultat |
|---|---|
| Health `8081` | UP · DB connected |
| Process BE | même JAR + `application-cutover-runtime.properties` |
| Serveur FE historique | `LANCER-FRONTEND.bat` → `python -m http.server 5500` dans `frontend/` |
| Relance | **identique** (Python, port **5500**, cwd `frontend/`) |
| `index.html` / `auth.html` / `candidate.html` | HTTP 200 |

### Config API & CSP

| Élément | Constat |
|---|---|
| `js/config.js` | API = même hostname + `:8081/api/v1` |
| `js/api.js` | repli hostname:8081 si `HIRELINK_API_BASE` absent (pages app) |
| CSP `connect-src` | inclut `http://127.0.0.1:8081` et `http://localhost:8081` sur pages clés |
| Navigateur smoke | Microsoft Edge via Playwright, **login formulaire réel**, **pas** d’injection de session |

---

## 2. Compteurs avant / après

| Entité | Avant | Après | Δ |
|---:|---:|---:|---:|
| `job_offers` | 123 | 123 | 0 |
| `applications` | 221 | 221 | 0 |
| `users` | 126 | 126 | 0 |
| `external_job_offers` | 106 | 106 | 0 |
| `catalog_listings` | 107 | 107 | 0 |
| `billing_invoices` | 8 | 8 | 0 |
| `boards_enabled` | 0 | 0 | 0 |
| `ext_available` | 0 | 0 | 0 |

Aucune candidature de test soumise (UI Postuler ouverte puis fermée).

---

## 3. Connecteurs

`application-cutover-runtime.properties` (toujours chargé par le BE) :

- Jooble / Greenhouse / Lever / Ashby / RemoteOK / Maroc = **`false`**
- `startup-sync=false`

---

## 4. Recette navigateur

**Script :** `docs/deploy-prep/fe-final-smoke.mjs`  
**Log :** `docs/deploy-prep/fe-final-smoke-r2.log`  
**JSON :** `docs/deploy-prep/fe-final-screenshots/recette.json`  
**Captures :** `docs/deploy-prep/fe-final-screenshots/`

| # | Parcours | Résultat | Capture |
|---|---|---|---|
| 1 | Page d’accueil | OK (page charge) ; grille offres KO (JS) | `01-landing.png` |
| 2 | Auth candidat | OK — Sara El Amrani | `02-candidate-login.png` |
| 3 | Dashboard candidat | OK | `03-candidate-dashboard.png` |
| 4 | Offres candidat | OK — 20 cartes | `04-candidate-offers.png` |
| 5 | Recherche `Java` | OK — 13 offres | `05-search-java.png` |
| 6–7 | Filtres pays ALL / MA | OK | `06` / `07` |
| 8 | Pour toi | OK | `08-pour-toi.png` |
| 9 | Détail offre | OK | `09-offer-detail.png` |
| 10 | UI candidature (sans submit) | OK — pas d’écriture DB | `10-apply-wizard.png` |
| 11 | Mes candidatures | OK | `11-candidate-applications.png` |
| 12 | Auth recruteur | OK — Salma Berrada | `12-recruiter-login.png` |
| 13 | Dashboard recruteur | OK | `13-recruiter-dashboard.png` |
| 14 | Gestion offres | OK | `14-recruiter-offers.png` |
| 15 | Pipeline | OK | `15-pipeline.png` |
| 16 | Entretiens | OK | `16-interviews.png` |
| 17 | Facturation / abonnements | OK — `checkout.html?plan=recruiter` | `17-billing-checkout.png` |

**fails fonctionnels (hors landing JS) :** 0  
**warns non bloquants :** SSE notifications abortés à la navigation ; `GET /billing/quote?cycle=month` → 403 (la page plans s’affiche quand même).

---

## 5. Correction L127 (appliquée)

### Avant

```text
SyntaxError: missing ) after argument list
```

Cause : `root.appendChild(HireLinkOffer.card(o, { ... });` — `)` manquante pour `appendChild`.

### Après (seul changement produit autorisé)

```diff
-      });
+      }));
```

| Vérification | Résultat |
|---|---|
| `node --check frontend/js/landing-offers.js` | **exit 0** |
| Edge + cache bypass (`?nocache=` + headers no-cache) | grille **9** cartes catalogue |
| Recherche / filtres / ouverture offre (→ `auth.html?mode=login`) | **OK** |
| Console `pageerror` / SyntaxError | **aucun** |
| Candidat (20 cartes) + pipeline recruteur | **OK** |
| Compteurs | 123 / 221 / 126 / 106 / 107 / 8 — **Δ 0** |

Preuves : `docs/deploy-prep/landing-fix-smoke.log`, `fe-final-screenshots/landing-fix-recette.json`, captures `18`–`23`.

**Warn non bloquant :** un `403` console résiduel (hors SyntaxError ; typiquement quote billing déjà noté) — n’empêche pas l’affichage landing ni les parcours.

Aucun autre problème révélateur hors périmètre.

---

## 6. Accès démo

| URL | Rôle |
|---|---|
| http://127.0.0.1:5500/ | Accueil (grille offres publiques OK) |
| http://127.0.0.1:5500/auth.html | Connexion |
| http://127.0.0.1:5500/app/candidate.html | Offres candidat |
| http://127.0.0.1:5500/app/recruiter.html | Espace recruteur |
| http://127.0.0.1:8081/api/v1/health | API |

Comptes démo : `candidat@hirelink.ma` / `recruteur@hirelink.ma` (mots de passe guide).

---

## 7. Prochaine étape

Démo locale **PASS**. Suite produit : Vague 1–2 offres marocaines externes (Jooble MA / partenariat) selon `HIRELINK_EXTERNAL_JOBS_ACTIVATION_PLAN.md` — sur autorisation séparée.
