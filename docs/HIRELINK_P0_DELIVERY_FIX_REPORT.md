# HireLink — Rapport corrections P0 (livraison GitHub)

**Date :** 2026-11-14  
**Référence CDC :** v6.3  
**Autorisation :** P0-1 / P0-2 / P0-3  
**Contraintes respectées :** `hirelink` intact · `8081`/`5500` non redémarrés · pas de commit/push · connecteurs OFF · secrets non modifiés · fichiers physiques non supprimés.

---

## Verdict

# READY TO COMMIT

Installation propre **réellement validée** sur environnement isolé (`hirelink_p0_clone` + API `18081`). Les trois P0 sont implémentés et prouvés. **Aucun commit/push effectué** — en attente de votre autorisation.

| P0 | Résultat |
|---|---|
| P0-1 Git sécurité | `.gitignore` élargi · ignores vérifiés · liste d’inclusion sûre |
| P0-2 Migrations | Runner V000–V006 · fresh + partial + rejeu OK |
| P0-3 Catalogue | Bootstrap post-seed · 52 listings · 2ᵉ boot sans rebuild |

---

## 1. Fichiers modifiés et pourquoi

| Fichier | Pourquoi |
|---|---|
| `.gitignore` | Exclure `docs/deploy-prep/`, `docs/audit-screenshots/`, `data/`, props iso, secrets |
| `database/apply-migrations.ps1` | Appliquer V001–**V006** ; `-Fresh` = V000+DROP ; codes sortie ; preuves tables |
| `database/migrations/README.md` | Doc V003–V006 |
| `docs/MIGRATIONS.md` | Alignement stratégie |
| `backend/.../DataSeeder.java` | `ensureCatalogBootstrap` idempotent si catalogue vide |
| `docs/HIRELINK_GIT_SAFE_INCLUDE_LIST.md` | Liste d’inclusion future (nouveau) |
| Docs audit / install / checklist / plan prof | Retirer le reindex manuel obligatoire |
| `docs/HIRELINK_P0_DELIVERY_FIX_REPORT.md` | Ce rapport |

**Non versionné (gitignored) :** `backend/application-p0-iso.properties`, logs/captures sous `tmp/`.

---

## 2. P0-1 — Preuves anti-fuite

`git check-ignore` OK pour :

- `docs/deploy-prep/**`
- `docs/audit-screenshots/`
- `backend/data/jwt.key`
- `backend/application-local.properties`
- `backend/application-p0-iso.properties`

`git ls-files` : **aucun** dump deploy-prep / jwt / application-local déjà suivi.  
SQL suivis = uniquement `database/migrations/*` et scripts schéma (intentionnels).

Liste d’inclusion : `docs/HIRELINK_GIT_SAFE_INCLUDE_LIST.md` (pas de `git add .`).

---

## 3. P0-2 — Migrations isolées

| Scénario | Base | Résultat |
|---|---|---|
| A Fresh | `hirelink_p0_fresh` | V000→V006 · 34 tables · catalog/boards/url_quality OK · exit 0 |
| B Partial (déjà V000–V002) | `hirelink_p0_partial` | V001–V006 réappliqués · 34 tables · exit 0 |
| Rejeu non destructif | `hirelink_p0_fresh` sans `-Fresh` | exit 0 (idempotent) |
| Clone & run | `hirelink_p0_clone` `-Fresh` | OK |

**Jamais exécuté sur `hirelink`.** Compteurs démo inchangés : 123 offres · 107 catalogue.

---

## 4. P0-3 — Catalogue après seed

Log 1er boot isolé :

```text
Catalogue initialisé après seed : 52 listings (à partir de 53 offres sources).
```

| Métrique | Valeur |
|---:|---:|
| users | 34 |
| job_offers | 53 |
| catalog_listings | 52 |
| external_job_offers | **0** |

API : `GET /catalog/offers` → `totalElements=52`.

2ᵉ boot :

```text
Catalogue déjà peuplé (52 lignes) — pas de reconstruction automatique.
```

Compteurs **identiques** (34/53/52/0) — pas de doublons, pas d’import MAROC/RemoteOK/ATS.

---

## 5. Clone & run isolé (preuves)

| Étape | Résultat |
|---|---|
| Migrations V000–V006 | OK |
| Backend isolé `:18081` | UP (demo `:8081` intact) |
| Seed + catalogue auto | 52 listings |
| Login candidat / recherche Java / Pour toi | OK (API + navigateur) |
| Recruteur + pipeline | 200 |
| Quote non-OWNER | 403 attendu |
| Quote OWNER `n.bennani@…` | 200 |
| Admin login | OK |
| Navigateur Edge `5500?api=18081` | landing **9** cartes · candidat **20** · Java **5** |
| 2ᵉ démarrage | idempotent |
| Externes fictifs | **0** |
| Démo `8081`/`5500` | UP / 200 |

Captures : `tmp/p0-iso-landing.png`, `tmp/p0-iso-landing-grid.png`, `tmp/p0-iso-candidate.png` (hors Git).

Note : un FE sur port ≠ 5500 nécessite d’étendre `hirelink.cors.allowed-origins` (comportement existant). Test navigateur via `5500` + `?api=18081` (CORS/CSP déjà prévus pour 18081).

---

## 6. Régressions / problèmes restants

| Item | Sévérité | Note |
|---|---|---|
| Rebuild fat JAR `target/` tant que 8081 tient le JAR | LOW | Contournement : `spring-boot:run` / build machine propre |
| Google Client ID par défaut dans properties | MED (P1) | Overridable via env |
| Paiement simulé / Jooble OFF | attendu | Pas un défaut P0 |
| Aucune régression démo | — | 123/107 + services UP |

---

## 7. Prochaine étape

1. Relire `docs/HIRELINK_GIT_SAFE_INCLUDE_LIST.md` + checklist.  
2. **Autoriser** commit (puis push) — non effectué ici.
