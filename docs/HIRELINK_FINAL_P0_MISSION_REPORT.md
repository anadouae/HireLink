# Mission finale HireLink — bilan (2026-11-15, suite)

**Verdict : pas entièrement validée.** Démo 8081 reste sur ancien bytecode B0 (sans B2/D2/plan-org). Candidat fat JAR isolé prouve B2 + B0 ; D2/P1 plan couverts par tests unitaires + code source. Cutover / commit / push **non exécutés**.

## Corrections réellement testées

| ID | Statut | Preuve |
|----|--------|--------|
| B2 Places × pays | FIXED AND TESTED (isolé) | Units `PlaceCatalogCountryFilterTest` ; iso :18084 `q=Ta&country=MA` → villes Maroc only ; `Par&MA` → 0 ; live 8081 encore FAIL (212 mixtes) |
| D2 Refus → annule entretien | FIXED AND TESTED (units) | `ApplicationServiceInterviewTest.refusAnnuleEntretienActif…` ; live 8081 : app#1 REJECTED + `interviewStatus=SCHEDULED` + ICS 200 + UI agenda encore actif (**bug live confirmé**) |
| D1 Historique UI | FIXED AND TESTED (navigateur) | Pipeline → Dossier → **Historique** → modal timeline 5 étapes Sara ; FE : plus de course close/open (`recruiter-pipeline.js?v=20261115d1`) |
| D5 `firstDecisionAt` | FIXED (code+units collatéraux) | Posé sur 1ʳᵉ décision recruteur, pas seulement REJECTED |
| F4 Paiement simulé | FIXED (FE) | Bannière démo checkout + succès |
| F5/P1 plan org vs crédits | FIXED (code+unit) | `AiCreditService.resolveTier` lit le plan **org** ; `AiCreditOrgPlanTest` ; live encore user ACCES / org PREMIUM jusqu’au redeploy |
| B3/B4 filtres | PARTIEL | Libellés/tooltips : filtre page locale + facettes page |
| E1 Notifications | PARTIEL | Libellé centre : in-app ≠ e-mail SMTP |
| A1/A2/A4/B0/C1/C2/D3/G1 | ALREADY FIXED AND TESTED | Voir rapport précédent |

## Parcours fonctionnels (8081 live)

| Parcours | Résultat |
|----------|----------|
| OWNER login + invite membre | PASS (`organizations/mine/invite` 200) |
| Recruteur non-OWNER invite | PASS (403) |
| Admin stats / users / plans | PASS |
| Facturation quote + invoices | PASS partiel (`vatConfigured=false`) ; `POST /billing/payments` → **500** (réf. `f050a1b2`) — non corrigé sans logs applicatifs sûrs |
| Crédits IA candidat / OWNER | PASS lecture ; OWNER affiche ACCES (12/20) malgré org PREMIUM — incohérence P1 |
| Pipeline + entretiens SCHEDULED | PASS listage ; 5 SCHEDULED |
| Isolation OCP vs Atlas | PASS (`atlas_leak=0`) |
| Places live MA | **FAIL** jusqu’à cutover |
| Entretien métier bout-en-bout (planifier→annuler via UI) | NON TESTÉ à fond |
| Paiement carte bout-en-bout UI | NON TESTÉ (API 500) |

## Tests isolés

- Units B2 + D2 + AI plan : cible Maven `build-iso/final-p0-tests` (voir `docs/evidence/final-p0/`).
- Fat JAR smoke :18084 : **B0 exclude PASS** (REMOTE_OK only) ; **B2 places PASS** ; health UP (démarrage long ~6–8 min). Process smoke arrêté après vérif.

## JAR candidat

| | |
|--|--|
| Path | `docs/deploy-prep/b0-candidate-runtime/hirelink-b0-backend.jar` |
| SHA-256 build | `C63FC7EEBB61B3BB7DC60689359030270080D6407B3FF60F06DF54C17BF97C4D` |
| Meta | `BUILD_META.txt` (rebuild 2026-11-15T03:07Z, ~71 MB) |
| Contient | B0 + B2 + D2 + D5 + resolveTier org-plan AI |
| Démo 8081 | **inchangée** (PID B0 antérieur ; demo jar SHA `C5722732…` intact) |

## Problèmes encore ouverts

1. B2 / D2 / plan-org **absents de 8081** jusqu’au cutover autorisé
2. App rejetée avec entretien encore « actif » en UI/API live (preuve D2)
3. `POST /billing/payments` 500 sur facture ouverte
4. Compteurs / filtres client (B3) non serveur
5. Entretiens UI « À venir 0 » vs pipeline 3 en entretien — écart à creuser
6. Paiement / abonnement UI Premium vs crédits ACCES jusqu’au redeploy

## Git (aucune commit / push)

- HEAD local sale ; secrets/dumps/JAR sous `docs/deploy-prep/`, `backend/data/`, `*.jar`, `.env`, `backup-db/` ignorés
- `build-iso/` / `docs/evidence/` à garder hors dépôt
- **Pas de commit ni push** — autorisation requise

## Étapes nécessitant votre autorisation explicite

1. **Cutover 8081** selon `docs/HIRELINK_CUTOVER_8081_PLAN.md` (backup → stop PID seul → start candidat → smoke → rollback si besoin)
2. **Rebuild fat JAR** final (inclure fix AI org-plan) puis re-smoke 18084
3. **Commit** + éventuellement **push** GitHub (message / périmètre à valider)
4. Correction ciblée du **500 paiement** si vous autorisez lecture logs runtime démo / hotfix

## Démo runtime (état)

- 8081 UP, offers **123**, FE 5500 UP
- maroc API catalogue toujours 0 (B0)
- Process guard : démo intacte après smokes isolés
