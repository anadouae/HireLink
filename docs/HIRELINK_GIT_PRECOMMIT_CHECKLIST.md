# HireLink — Checklist Git avant commit (livraison GitHub)

À cocher **dans l’ordre** avant tout `git commit` / `git push`. Aucun commit n’est effectué par l’audit.

---

## A. Hygiène anti-fuite (bloquant)

- [ ] `git status` lu en entier (pas de surprise)
- [ ] **`.gitignore` working tree déjà mis à jour** est **stagé en premier**
- [ ] Vérifier `git check-ignore -v` sur :
  - [ ] `backend/data/jwt.key`
  - [ ] `backend/application-local.properties`
  - [ ] `docs/deploy-prep/` (répertoire entier)
  - [ ] `docs/audit-screenshots/`
  - [ ] `backend/application-cutover-runtime.properties`
  - [ ] `backend/application-p0-iso.properties`
- [ ] **Ne jamais** `git add docs/deploy-prep` ni `docs/audit-screenshots`
- [ ] Suivre `docs/HIRELINK_GIT_SAFE_INCLUDE_LIST.md`
- [ ] **Ne jamais** `git add backend/data`
- [ ] `git diff --cached` ne contient **aucun** : mot de passe SMTP, clé API, dump SQL, `BEGIN RSA`, `$2a$` bcrypt massif

Commandes utiles :

```bat
git check-ignore -v backend\data\jwt.key docs\deploy-prep\hirelink-vague0-20261114-015822.sql
git diff --cached | findstr /i "password api-key gsk- AIza BEGIN sql Dump"
```

---

## B. Contenu métier à versionner

- [ ] Backend Phase 1–3 (`Catalog*`, `external/*`, UrlQuality, boards, tests isolation)
- [ ] `frontend/js/landing-offers.js` (fix L127 `}));`)
- [ ] Autres FE/BE déjà modifiés (CSP, candidate-home catalogue, checkout, etc.)
- [ ] `database/migrations/V003` … `V006`
- [ ] `application.properties` (flags externes OFF)
- [ ] Docs : GUIDE, install Windows, audit livraison, plans (sans dumps)

---

## C. Contenu à laisser hors commit

- [ ] `docs/deploy-prep/**` (SQL, logs, screenshots, JAR rollback)
- [ ] `docs/audit-screenshots/**`
- [ ] `package.json` racine **seulement si** inutile (optionnel ; stub Node non requis)
- [ ] Rapports locaux trop liés à une machine (chemins absolus sensibles) — relire avant add

---

## D. Cohérence technique avant push

- [ ] P0 livrés (voir `HIRELINK_P0_DELIVERY_FIX_REPORT.md`) — READY TO COMMIT
- [ ] `node --check frontend/js/landing-offers.js` → OK
- [ ] Aucun flag Jooble/ATS/RemoteOK/Maroc/`startup-sync` à `true` dans les fichiers commités
- [ ] Message de commit clair (ex. phases catalogue + P0 livraison + fix landing)

---

## E. Ordre de commit suggéré (quand autorisé)

1. `.gitignore` (+ ignore élargi deploy-prep/screenshots si autorisé)
2. `database/migrations` V003–V006 (+ fix `apply-migrations.ps1` si autorisé)
3. Backend Phase 1–3 + tests
4. Frontend + fix landing
5. `application.properties` + docs livraison

Puis `git status` final propre ; push **uniquement** sur demande explicite.

---

## F. Interdit

- [ ] Pas de `--no-verify` / amend non demandé
- [ ] Pas de force-push `main`
- [ ] Pas de secrets « pour que ça marche chez moi » dans le dépôt
