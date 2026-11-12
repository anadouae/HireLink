# Bilan dernier contrôle avant commit / push

**Date :** 2026-11-12 · **Aucun commit / push effectué**

| # | Contrôle | Statut | Preuve |
|---|---|---|---|
| 1 | Notification orpheline offre #158 | **PASS** | Clic / URL `candidate.html?offer=158` ? « Offre indisponible… » (toast + détail). Notif `1519` **conservée** (`link=offer:158`). Pas de suppression. |
| 2 | Checkout `vatConfigured=false` | **PASS** | Libellés « Montant catalogue », « TVA non configurée », « Total affiché (simulation) » + note : pas un TTC fiscal définitif, aucun paiement réel. Badge Simulation. |
| 3 | Migrations V000–V002 + runner | **PASS** | Fresh `hirelink_mig_fresh` ? 31 tables. Historique (drop legal/external puis upgrade) ? tables recréées. Fichiers **dans le dépôt** (`database/migrations/*`, `apply-migrations.ps1`). Client MySQL = outil local (`-Mysql`), pas un artefact manquant du repo. |
| 4 | Git / secrets / captures | **PASS*** | Voir recommandations ci-dessous (*conditions pour publication). |
| 5 | Régression | **PASS** | `BillingQuoteTest`+`LegalConsentServiceTest` OK ; prepub UI 3/3 ; démo `8081` UP + front `5500` 200. |

\*Publication Git **conditionnée** au respect des exclusions ci-dessous.

---

## Détail Git (sans commit)

### À versionner (cœur produit)
- Backend legal + billing quote + tests
- Frontend UX / checkout / `candidate-home` message offre absente
- `database/migrations/**`, `apply-migrations.ps1`, `migrate_legal_acceptances.sql`
- `docs/MIGRATIONS.md`, rapports texte `docs/security-control-20261112/*.md` (sans `shots/`)
- `frontend/legal/**`
- Docs refonte / audit (hors captures PII si présentes)

### À ne pas publier
| Chemin | Motif |
|---|---|
| `backend/target-*/` | Build locaux (désormais ignorés via `.gitignore`) |
| `docs/security-control-*/shots/**` | Captures avec **données démo / PII** (noms, e-mails, dossiers) — ignorées |
| Secrets | `application.properties` : seulement versions légales (OK). Pas de mot de passe ajouté. Conserver `application-local.properties` hors git. |

### Ordre de commits suggéré (quand autorisé)
1. `database/` migrations + docs MIGRATIONS  
2. Backend legal + billing quote + tests  
3. Frontend UX + checkout + offre indisponible  
4. Docs rapports (sans shots)

---

## Correctifs appliqués dans ce contrôle
- `frontend/js/candidate-home.js` : message dédié si offre demandée absente (y compris liste vide).
- `frontend/js/checkout.js` + `checkout.html` : libellés non fiscaux si TVA absente.
- `database/apply-migrations.ps1` : strip BOM ; chemins `migrations/`.
- `.gitignore` : `backend/target-*`, `docs/security-control-*/shots/`.

## Démo
`hirelink` inchangée pour ce contrôle (notif 1519 toujours présente). API démo UP.
