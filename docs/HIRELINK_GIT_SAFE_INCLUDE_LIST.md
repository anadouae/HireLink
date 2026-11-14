# Liste d’inclusion sûre — futur commit HireLink

**Ne pas exécuter `git add .` / `git add -A`.** Ajouter par chemins explicites.

## 1. Toujours en premier
- `.gitignore`

## 2. Migrations & runner
- `database/apply-migrations.ps1`
- `database/migrations/V003__billing_invoice_org_nullable.sql`
- `database/migrations/V004__external_offer_url_quality.sql`
- `database/migrations/V005__external_job_boards_ats.sql`
- `database/migrations/V006__catalog_listings.sql`
- `database/migrations/README.md`
- `docs/MIGRATIONS.md`

## 3. Backend Phase 1–3 + P0-3
- `backend/src/main/java/ma/ensat/hirelink/config/DataSeeder.java`
- `backend/src/main/java/ma/ensat/hirelink/controller/CatalogOfferController.java`
- `backend/src/main/java/ma/ensat/hirelink/model/Catalog*.java`
- `backend/src/main/java/ma/ensat/hirelink/model/ExternalJobBoard.java`
- `backend/src/main/java/ma/ensat/hirelink/repository/Catalog*.java`
- `backend/src/main/java/ma/ensat/hirelink/repository/ExternalJobBoardRepository.java`
- `backend/src/main/java/ma/ensat/hirelink/service/Catalog*.java`
- `backend/src/main/java/ma/ensat/hirelink/service/ExternalJob*.java` (modifs + nouveaux)
- `backend/src/main/java/ma/ensat/hirelink/service/UrlQuality*.java`
- `backend/src/main/java/ma/ensat/hirelink/service/MarocPartner*.java`
- `backend/src/main/java/ma/ensat/hirelink/service/ParsedExternalJob.java`
- `backend/src/main/java/ma/ensat/hirelink/service/external/`
- Autres fichiers déjà modifiés listés par `git status` sous `backend/src/` (SecurityConfig, JobOfferService, etc.)
- `backend/src/main/resources/application.properties`
- `backend/src/main/resources/feeds/` · `branding/` (si présents)
- `backend/src/test/java/...` (tests isolation catalogue / ATS / UrlQuality)
- `backend/src/test/resources/feeds/` · `fixtures/` · `application.properties` (test)

## 4. Frontend
- `frontend/js/landing-offers.js` (fix L127)
- Autres FE modifiés (candidate-home, offer-card, checkout, CSP HTML, etc.) selon `git status`

## 5. Documentation livraison
- `docs/HIRELINK_GITHUB_DELIVERY_AUDIT.md`
- `docs/HIRELINK_INSTALL_WINDOWS.md`
- `docs/HIRELINK_GIT_PRECOMMIT_CHECKLIST.md`
- `docs/HIRELINK_PROFESSOR_TEST_PLAN.md`
- `docs/HIRELINK_P0_DELIVERY_FIX_REPORT.md`
- `docs/HIRELINK_GIT_SAFE_INCLUDE_LIST.md`
- Rapports Phase 0–3 / conformité **sans** dumps (fichiers `docs/HIRELINK_*.md`, `docs/GLOBAL_JOB_*.md`)
- `README.md` (si mis à jour)

## 6. Exclusions absolues
- `docs/deploy-prep/` (tout)
- `docs/audit-screenshots/`
- `backend/data/`, `backend/application-local.properties`, `*-iso.properties`, cutover-runtime
- `backup-db/`, `*.sql` de dump, JWT, clés API
- `package.json` racine (optionnel — non requis pour la démo Java)

## 7. Commande type (après autorisation commit)

```bat
git add .gitignore
git add database/apply-migrations.ps1 database/migrations docs/MIGRATIONS.md
git add backend/src
git add frontend
git add docs/HIRELINK_*.md docs/GLOBAL_JOB_*.md docs/HIRELINK_GIT_SAFE_INCLUDE_LIST.md
git add README.md
git status
git diff --cached --stat
```
