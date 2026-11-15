# HireLink — Correction CSP pour ports alternatifs

**Date :** 2026-11-14
**Autorisation :** correction CSP explicite (sans commit / push)
**Objectif :** PASS UI sur frontend **5505** + backend **18082** **sans** contournement CSP Playwright
**Démo 8081/5500 :** non redémarrée, base `hirelink` non modifiée (123 offres conservées)

---

## Verdict

# PASS

Les pages HTML autorisent désormais explicitement les API locales `8081`, `18081`, `18082` et `8095` dans `connect-src` / `img-src` (pas de `connect-src *`).
CORS / WebAuthn incluent `5505`. Tests isolés 5505→18082 : candidat, Pour toi, pipeline, admin, checkout OWNER — **aucune violation CSP console**, aucun `Page.setBypassCSP`.

---

## 1. Problème vu sur la capture (5500 / recruteur)

La capture `http://127.0.0.1:5500/app/recruiter.html` montre **« Une erreur interne est survenue »**, pas « Réseau indisponible ».

| | Capture 5500 | Limitation CSP (rapport clone) |
|---|---|---|
| Cause | API démo **500** sur `/organizations/mine`, `/offers/mine`, `/applications/pipeline` | Navigateur **bloque** `connect` vers un port API hors liste CSP |
| Message UI | Erreur interne + référence support | Réseau indisponible / chargement impossible |
| Port | 5500 → 8081 (CSP OK pour 8081) | Ex. 5505 → 18082 |

**Conclusion :** le dashboard recruteur sur la démo actuelle est un **bug API 500** indépendant de la CSP. La présente correction traite uniquement la CSP / CORS ports alternatifs ; la démo n’a pas été redémarrée ni « réparée » ici (contrainte).

---

## 2. Audit CSP (avant)

| Groupe | Pages | `connect-src` / `img-src` |
|---|---|---|
| OK ports alt. | `index.html`, `auth.html`, `candidate.html`, `recruiter-offers.html` | 8081 + 18081 + 18082 |
| Bloquantes | majorité `app/*` (recruteur, admin, settings, pipeline…), `checkout.html`, `help`, `company`, `account`, etc. | **8081 seulement** (checkout : 8081+8095) |

Écart = cause exacte du FAIL UI pipeline/admin/checkout lors du test clone GitHub sur 18082.

---

## 3. Corrections

### Frontend — 26 fichiers HTML

Homogénéisation **explicite** (pas de wildcard) :

- `img-src` : `'self' data: blob: [https:]` +
  `http://localhost|127.0.0.1:8081|18081|18082|8095`
- `connect-src` : `'self'` + mêmes hôtes API + `https://accounts.google.com` (si déjà présent)

Fichiers touchés :
`index.html`, `auth.html`, `checkout.html`, `about.html`, `company.html`, `help.html`, `security.html`, `set-password.html`,
`app/account.html`, `app/admin.html`, `app/candidate*.html`, `app/notifications.html`, `app/recruiter*.html` (hors pages sans meta CSP), `app/security.html`.

### Backend — CORS / WebAuthn

`backend/src/main/resources/application.properties` :

- `hirelink.cors.allowed-origins` : ajout `http://localhost:5505`, `http://127.0.0.1:5505`
- `hirelink.webauthn.origins` : idem

Sélection API frontend inchangée : `frontend/js/config.js` (`?api=` + `sessionStorage`) — déjà compatible ports alt.

### Non modifié

- Secrets, connecteurs externes (restent `enabled=false`)
- Base `hirelink`
- Processus démo 8081 / FE 5500

Fichier local de test uniquement : `backend/application-csp-alt-test.properties` (overlay 18082 + DB `hirelink_github_clone_test`, gitignored).

---

## 4. Tests obligatoires (isolé 5505 / 18082)

| Test | Résultat |
|---|---|
| FE 5505 + BE 18082 UP | PASS |
| CSP servie `recruiter.html` contient 18082 | PASS |
| Login candidat | PASS |
| Recherche / grille offres | PASS (20 cartes) |
| « Pour toi » | PASS (6) |
| Pipeline recruteur | PASS (Kanban seed, sans erreur réseau) |
| Tableau de bord admin | PASS |
| Checkout OWNER + Formules (Premium visible) | PASS |
| Console : violations **Content Security Policy** | **0** |
| Contournement `Page.setBypassCSP` | **Non utilisé** |
| CORS `Origin: http://127.0.0.1:5505` → ACAO | `http://127.0.0.1:5505` |
| Connecteurs | Allowlist 5 boards, **0 actifs** |
| Démo 8081/5500 pendant/après | UP / 200 ; `hirelink.job_offers=123` |

Captures : `docs/evidence/csp-alt-ports/`
(`shot-candidate.png`, `shot-pour-toi.png`, `shot-pipeline.png`, `shot-admin.png`, `shot-checkout.png`, `shot-formules.png`)

### Note non bloquante

Quelques requêtes logo org (`/media/org-logos/...`) peuvent échouer en `net::ERR_BLOCKED_BY_ORB` (Opaque Response Blocking navigateur, **≠ CSP**). Les parcours métier testés restent verts.

---

## 5. Régressions

| Scénario | Statut |
|---|---|
| Ports standards 8081/5500 (CSP + CORS toujours présents) | Préservés |
| Sécurité CSP (pas de `*`) | OK |
| Connecteurs externes | Toujours désactivés |
| Démo live | Intact (les 500 recruteur préexistants non traités ici) |

---

## 6. Suite éventuelle (hors périmètre)

1. Diagnostiquer / corriger les **500** `/organizations/mine` (et dépendances) sur la démo 8081 — cause de la capture utilisateur.
2. Optionnel : en-tête `Cross-Origin-Resource-Policy: cross-origin` sur `/media/**` pour les logos en ports croisés.

---

## Fichiers modifiés (résumé)

- 26 × `frontend/**/*.html` (meta CSP)
- `backend/src/main/resources/application.properties` (CORS + WebAuthn 5505)
- `.gitignore` (overlays / tmp de test CSP)
- Rapport + preuves (ce fichier + `docs/evidence/csp-alt-ports/`)

**Aucun commit / push effectué.**
