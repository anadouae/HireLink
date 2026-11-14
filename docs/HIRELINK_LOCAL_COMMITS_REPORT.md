# HireLink — Rapport commits locaux (avant push GitHub)

**Date :** 2026-11-14  
**Branche :** `main`  
**Remote :** `origin/main` (`https://github.com/anadouae/HireLink.git`)  
**État :** **6 commits locaux** — **aucun push**  
**CDC :** v6.3  
**Services démo :** `8081`/`5500` non redémarrés (UP au moment du commit)

---

## Verdict

Commits locaux **créés et vérifiés**. Prêts pour revue humaine avant `git push` (non autorisé ici).

---

## 1. Commits créés

| # | Hash | Message |
|---|---|---|
| 1 | `3263843d48d603943169eddb366d1782c80e0789` | `chore: harden gitignore for delivery hygiene` |
| 2 | `607be891f83fd873c4432782e5c53155749a8c48` | `feat(db): add Phase 1-3 migrations through V006` |
| 3 | `92615e45282e4cfbc89d12f151a1a978920f2142` | `feat(backend): ship unified catalog Phases 1-3 with safe defaults` |
| 4 | `aab0a074ab914df48a421e9c1a8cdf562270a11e` | `feat(frontend): wire catalog UX and fix landing offers syntax` |
| 5 | `5ef806063f20976ab7130b27769930fd3760862b` | `docs: add delivery guides and Phase 0-3 catalog reports` |
| 6 | `b99d8d60853b8602bd97c8ef4fb6a7bdda690711` | `docs: record local commits report before GitHub push` |

`git rev-list --count origin/main..HEAD` = **6**  
Base : `466ba1f` (origin/main)

---

## 2. Contenu inclus (par commit)

### 1 — `.gitignore`
Exclusion `docs/deploy-prep/`, `docs/audit-screenshots/`, props iso/cutover, `data/`, backups.

### 2 — Migrations
`apply-migrations.ps1`, `V003`–`V006`, `database/migrations/README.md`, `docs/MIGRATIONS.md`.

### 3 — Backend Phases 1–3 + P0-3
Catalogue, UrlQuality, ATS (OFF), feeds/branding, `DataSeeder.ensureCatalogBootstrap`, `application.properties` (connecteurs OFF), tests isolation.

### 4 — Frontend
`landing-offers.js` (fix `}));`), `candidate-home` catalogue, CSP, checkout, pipeline/agenda, etc.

### 5 — Docs livraison
Guides install/professeur/checklist, audits Phase 0–3, conformité, README (migrations + catalogue auto).

**Déjà sur `main` (inchangés) :** `LANCER-HIRELINK.bat`, `LANCER-HIRELINK-DEMO.bat`, `LANCER-FRONTEND.bat`.

---

## 3. Exclusions volontaires

| Chemin | Raison |
|---|---|
| `docs/deploy-prep/` | Dumps SQL / PII / logs (gitignored) |
| `docs/audit-screenshots/` | Captures runtime (gitignored) |
| `backend/data/`, `jwt.key`, `application-local.properties` | Secrets / uploads |
| `backend/application-*-iso.properties`, cutover-runtime | Config machine |
| `package.json` (racine) | Stub Node non requis |
| `docs/audit-browser-recette.mjs` | Script local avec mots de passe démo en dur |
| `backup-db/`, `tmp/` | Artéfacts locaux |

Aucune utilisation de `git add .` / `git add -A` / `--no-verify` / `--amend` / force.

---

## 4. Vérifications anti-fuite

| Contrôle | Résultat |
|---|---|
| `git check-ignore` deploy-prep / screenshots / jwt / local props | OK |
| `git ls-files` sensibles déjà suivis | Aucun dump/jwt/local |
| Scan staged `gsk_` / `AIzaSy` / clés privées / e-mails perso | **0 hit** |
| Google Client ID dans `application.properties` | **Public OAuth Client ID** (pas un client secret) ; déjà présent sur `origin/main` ; surchargeable via `HIRELINK_GOOGLE_CLIENT_ID` — **publication acceptable** avec réserve « projet auteur » |
| Mots de passe SMTP / LLM | Uniquement placeholders `${HIRELINK_*}` ou vides |
| Connecteurs | `startup-sync` / Jooble / ATS / Maroc / RemoteOK = **false** |

---

## 5. Contrôle fonctionnel (référence P0)

Validé précédemment (isolé) : migrations V000–V006, seed + catalogue auto, landing fix, parcours candidat/recruteur/admin, quote OWNER — voir `HIRELINK_P0_DELIVERY_FIX_REPORT.md`.  
Non rejoué pendant les commits (services démo non redémarrés).

---

## 6. État Git final

```text
## main...origin/main [ahead 6]
?? docs/audit-browser-recette.mjs
?? package.json
```

(Working tree propre hors 2 untracked exclus.)

---

## 7. Réserves avant publication (`git push`)

1. Relire les 6 messages/hashs ci-dessus.  
2. Optionnel : retirer le Google Client ID par défaut au profit d’un placeholder env-only (P1).  
3. Confirmer que `docs/audit-browser-recette.mjs` et `package.json` restent hors dépôt.  
4. **Push uniquement** après autorisation explicite séparée.
