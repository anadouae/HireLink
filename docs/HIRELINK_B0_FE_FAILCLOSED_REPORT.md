# HireLink — B0 frontend fail-closed (catalogue)

**Date :** 2026-11-14
**Statut :** **PASS** (frontend uniquement)
**Périmètre :** supprimer le repli `/offers/public` + `/external-offers` quand `/catalog/offers` échoue.
**Hors périmètre (respecté) :** backend, `/external-offers`, redémarrage 8081/5500, base `hirelink`, connecteurs, secrets, commit/push, lots L1+.

---

## 1. Verdict

| Question | Réponse |
|----------|---------|
| **B0 frontend PASS ?** | **Oui** |
| Message « Catalogue temporairement indisponible » | Affiché (landing + espace candidat) si catalogue KO |
| Appels `/external-offers` en panne catalogue | **0** (prouvé Playwright) |
| Appels `/offers/public` en panne catalogue | **0** (repli interne non conforme aux filtres → refusé) |
| Recherche / filtres / pagination normaux | **OK** (non-régression) |
| Offres DB / health | **123** / **UP** (inchangés) |
| Reste côté API | **Oui** — aligner `/external-offers` sur `exclude-sources` (non fait) |

---

## 2. Fichiers modifiés

| Fichier | Changement |
|---------|------------|
| `frontend/js/landing-offers.js` | Suppression du fallback legacy ; erreur explicite fail-closed |
| `frontend/js/candidate-home.js` | Idem |
| `frontend/index.html` | Cache-bust `landing-offers.js?v=20261114b0` |
| `frontend/app/candidate.html` | Cache-bust `candidate-home.js?v=20261114b0` |
| `docs/evidence/b0-fe-failclosed/*` | Preuves |
| `docs/HIRELINK_B0_FE_FAILCLOSED_REPORT.md` | Ce rapport |

**Décision `/offers/public` :** non utilisé en fallback. Endpoint sûr (internes publiées non suspendues) mais **non conforme** aux filtres catalogue (`country`, `city`, `contract`, `origin`, `sinceDays`, `q`, `forYou`, pagination serveur). Conformément à la consigne : état d’erreur explicite plutôt qu’un affichage trompeur.

---

## 3. Comportement après correctif

1. Succès `/catalog/offers` → comportement inchangé (items, total, pagination).
2. Échec `/catalog/offers` (HTTP non OK, exception réseau, payload invalide) →
   - **aucun** fetch `/external-offers`
   - **aucun** fetch `/offers/public`
   - UI : `Catalogue temporairement indisponible` (`role="alert"`)

---

## 4. Tests & preuves

### 4.1 Non-régression (catalogue UP)

| Test | Résultat |
|------|----------|
| Landing : 107 offres, 9 cartes, 1× `/catalog/offers`, 0 externe | PASS |
| Landing recherche `Java` → 13 offres ; « Afficher plus » allonge la liste ; 0 externe | PASS |
| Candidat connecté (`candidat@hirelink.ma`) : 107 offres, 20 cartes | PASS |
| Filtre ville Casablanca → 47 offres | PASS |
| Pagination candidat « plus » → 40 cartes | PASS |
| `COUNT(job_offers)=123`, `health=UP` | PASS |

### 4.2 Simulation panne catalogue (route Playwright → HTTP 500)

| Surface | Message | `/external-offers` | `/offers/public` |
|---------|---------|--------------------|------------------|
| Landing | OK | 0 | 0 |
| Candidat | OK | 0 | 0 |

### 4.3 Captures

| Fichier | Contenu |
|---------|---------|
| `docs/evidence/b0-fe-failclosed/shot-landing-normal.png` | Landing catalogue OK |
| `docs/evidence/b0-fe-failclosed/shot-landing-catalog-down.png` | Landing fail-closed |
| `docs/evidence/b0-fe-failclosed/shot-landing-search-java.png` | Recherche + pagination |
| `docs/evidence/b0-fe-failclosed/shot-candidate-normal.png` | Candidat catalogue OK |
| `docs/evidence/b0-fe-failclosed/shot-candidate-catalog-down.png` | Candidat fail-closed |
| `docs/evidence/b0-fe-failclosed/browser.log` / `cand.log` / `run.log` | Traces |

---

## 5. Reste à corriger côté API (non appliqué)

Le risque B0 **côté API** demeure tant que `GET /api/v1/external-offers` n’applique pas la même politique `exclude-sources` / listabilité que le catalogue :

- Tout autre client (ancien JS en cache, outil, future feature) peut encore contourner le filtre.
- Correctif recommandé : aligner `ExternalJobController` (ou le service sous-jacent) sur la politique catalogue ; tests d’intégration exclude-sources.
- **Nécessite** autorisation backend + stratégie cutover JAR (sans écraser la démo sous JVM).

B0 FE = **mitigation immédiate**. B0 API = **fermeture complète**.

---

## 6. Suite

Aucun autre lot sans nouvelle autorisation.
