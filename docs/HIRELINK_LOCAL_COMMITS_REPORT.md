# HireLink — Rapport commits locaux (avant push GitHub)

**Date :** 2026-11-14  
**Branche :** `main`  
**Remote :** `origin/main` (`https://github.com/anadouae/HireLink.git`)  
**État :** **commits locaux ahead of origin** — **aucun push**  
**CDC :** v6.3  
**Services démo :** `8081`/`5500` non redémarrés (UP au moment du commit)

Vérifier le compte exact avec : `git rev-list --count origin/main..HEAD` et `git log --oneline origin/main..HEAD`.

---

## Verdict

Commits locaux **créés et vérifiés**. Prêts pour revue humaine avant `git push` (non autorisé ici).

---

## 1. Commits créés (ordre chronologique)

| # | Hash | Message |
|---|---|---|
| 1 | `3263843d48d603943169eddb366d1782c80e0789` | `chore: harden gitignore for delivery hygiene` |
| 2 | `607be891f83fd873c4432782e5c53155749a8c48` | `feat(db): add Phase 1-3 migrations through V006` |
| 3 | `92615e45282e4cfbc89d12f151a1a978920f2142` | `feat(backend): ship unified catalog Phases 1-3 with safe defaults` |
| 4 | `aab0a074ab914df48a421e9c1a8cdf562270a11e` | `feat(frontend): wire catalog UX and fix landing offers syntax` |
| 5 | `5ef806063f20976ab7130b27769930fd3760862b` | `docs: add delivery guides and Phase 0-3 catalog reports` |
| 6 | `b99d8d60853b8602bd97c8ef4fb6a7bdda690711` | `docs: record local commits report before GitHub push` |
| 7 | `7066715b78950b253509f987eaa760511c1503c3` | `docs: complete local commits report with sixth hash` |

Base : `466ba1f` (origin/main).  
Un éventuel commit supplémentaire peaufinant **ce** fichier peut apparaître au-dessus de la liste ; se fier à `git log`.

---

## 2. Contenu inclus

### Produit (1–4)
- `.gitignore` livraison
- Migrations `V003`–`V006` + runner
- Backend catalogue Phases 1–3, seed bootstrap catalogue, connecteurs **OFF**
- Frontend catalogue + fix `landing-offers.js`
- Scripts `LANCER-*.bat` déjà présents sur `main` (non modifiés)

### Documentation (5–7)
- Guides install Windows, plan professeur, checklists Git
- Rapports Phase 0–3 / conformité / P0
- README (migrations + catalogue auto)
- Ce rapport de commits locaux

---

## 3. Exclusions volontaires

| Chemin | Raison |
|---|---|
| `docs/deploy-prep/` | Dumps SQL / PII / logs (gitignored) |
| `docs/audit-screenshots/` | Captures runtime (gitignored) |
| `backend/data/`, `jwt.key`, `application-local.properties` | Secrets / uploads |
| Props iso / cutover-runtime | Config machine |
| `package.json` (racine) | Stub Node non requis |
| `docs/audit-browser-recette.mjs` | Script local + mots de passe démo en dur |
| `backup-db/`, `tmp/` | Artéfacts locaux |

Pas de `git add .` / `-A` / `--no-verify` / `--amend` / force / **push**.

---

## 4. Anti-fuite

| Contrôle | Résultat |
|---|---|
| Ignore deploy-prep / screenshots / jwt / local | OK |
| Aucun dump/jwt déjà suivi | OK |
| Scan clés API / SMTP réels / e-mails perso | OK (0 fuite) |
| Google Client ID | **Client ID OAuth public** (pas un secret) ; déjà sur `origin/main` ; overridable `HIRELINK_GOOGLE_CLIENT_ID` — **publiable** avec réserve projet auteur |
| Connecteurs dans properties commités | tous `false` + `startup-sync=false` |

---

## 5. Tests

Référence : `HIRELINK_P0_DELIVERY_FIX_REPORT.md` (clone & run isolé PASS).  
Non rejoués pendant les commits (démo `8081`/`5500` intacte).

---

## 6. État Git attendu après revue

```text
## main...origin/main [ahead N]
?? docs/audit-browser-recette.mjs
?? package.json
```

---

## 7. Réserves avant `git push`

1. Relire `git log --oneline origin/main..HEAD`.  
2. Optionnel P1 : neutraliser le Google Client ID par défaut.  
3. Garder `package.json` et `audit-browser-recette.mjs` hors dépôt.  
4. **Push seulement** après autorisation explicite.
