# HireLink — Lot L0 : encodage pages légales (UTF-8)

**Date :** 2026-11-14
**Statut :** **PASS**
**Périmètre :** conversion CP1252 → UTF-8 de `frontend/legal/*.html` + garde anti-régression + vérif navigateur.
**Hors périmètre (respecté) :** base `hirelink`, backend 8081 (non redémarré), secrets/connecteurs, build Maven/JAR démo, commit/push, lots L1+.

---

## 1. Résultat

| Objectif | Résultat |
|----------|----------|
| Convertir `cgu.html` + `confidentialite.html` en UTF-8 sans perte | **OK** |
| Restaurer accents / spéciaux / liens « ← Retour » | **OK** (vérifié bytes + navigateur) |
| Vérification visuelle navigateur | **OK** (Edge/Playwright, FE `http://127.0.0.1:5500`) |
| Contrôle anti-régression | **OK** — `scripts/check-legal-utf8.py` |
| Non-régression démo | **OK** — offers=123, health=UP |

---

## 2. Fichiers modifiés / ajoutés

| Fichier | Action |
|---------|--------|
| `frontend/legal/cgu.html` | Re-encodé UTF-8 (était Windows-1252 / CP1252) ; contenu + `← Retour à l’inscription` |
| `frontend/legal/confidentialite.html` | Idem |
| `scripts/check-legal-utf8.py` | **Ajout** — contrôle UTF-8 / accents / anti-mojibake / lien Retour |
| `docs/evidence/l0-legal-utf8/*` | Preuves (captures + logs) |
| `docs/HIRELINK_L0_LEGAL_UTF8_REPORT.md` | Ce rapport |

**SHA-256 (après conversion) :**

- `cgu.html` → `83350C5E5FD32FC54820FA5DCB1410E775DBE6D9E863E8E6B9BD2369433ADD48`
- `confidentialite.html` → `FC33CF308532CCF68E72A33E6CBBE68DA13DC3306104301254EA874F7C7FCF51`

**Non touchés :** `hirelink` DB, `8081`, JAR démo, secrets, connecteurs, autres lots.

---

## 3. Méthode de conversion

1. Lecture des octets bruts ; détection décodage CP1252 valide vs UTF-8 invalide / mojibake.
2. Décodage `cp1252` → réécriture `utf-8` (sans BOM).
3. Contrôle présence de `← Retour`, accents FR, absence de marqueurs `Ã©` / `â€™`.

---

## 4. Contrôle anti-régression

```bash
python scripts/check-legal-utf8.py
# → LEGAL UTF-8 CHECK OK: cgu.html, confidentialite.html
```

Le script échoue (exit 1) si :

- fichier manquant ou non UTF-8 valide ;
- absence de `charset=UTF-8` ;
- absence de `← Retour` ;
- absence d’accents FR attendus ;
- présence de mojibake classique (`Ã©`, `Ã¨`, `â€™`, …).

À lancer avant toute livraison / revue des pages légales (local ou CI future — **non branché** ici pour rester dans L0 uniquement).

---

## 5. Tests & preuves

### 5.1 Automatisés

| Test | Résultat |
|------|----------|
| `python scripts/check-legal-utf8.py` | PASS |
| HTTP FE `5500` — titres avec accents | PASS (`Conditions générales…`, `Politique de confidentialité…`) |
| Corps : flèche Retour + accents, mojibake=false | PASS |
| `GET /api/v1/health` (8081) | UP (processus non redémarré) |
| `COUNT(*) job_offers` | **123** |

### 5.2 Visuel navigateur (Playwright / Edge headless)

| Capture | Chemin |
|---------|--------|
| CGU full page | `docs/evidence/l0-legal-utf8/shot-cgu.png` |
| Confidentialité full page | `docs/evidence/l0-legal-utf8/shot-confidentialite.png` |
| Lien « ← Retour » (détail) | `docs/evidence/l0-legal-utf8/shot-cgu-back.png`, `shot-confidentialite-back.png` |
| Log browser | `docs/evidence/l0-legal-utf8/browser.log` |

**Constat visuel :** accents (`général`, `académique`, `données`, `confidentialité`, …) corrects ; liens bleus `← Retour à l’inscription` + navigation croisée CGU ↔ confidentialité ; bannière brouillon pédagogique lisible.

---

## 6. Proposition suivante — **B0** (non appliquée)

### Problème (audit)

Si le catalogue échoue, le FE tombe en fallback legacy :

- `GET /offers/public` + `GET /external-offers`
- `/external-offers` **n’applique pas** `exclude-sources` → risque d’afficher des sources exclus (ex. fixtures `MAROC_EMPLOI`) alors que le catalogue filtré ne les montre pas.

Fichiers cités audit : `landing-offers.js` (~149–163), `candidate-home.js` (~489–500), `ExternalJobController`, indexer catalogue.

### Proposition de correctif (à autoriser avant exécution)

1. **Fail-closed côté FE (priorité courte)**
   Sur erreur catalogue : **ne pas** appeler `/external-offers` / ne pas merger d’externes ; empty-state « Catalogue temporairement indisponible » (ou offres internes seules si `/offers/public` est sûr et aligné).
   Impact : 2 fichiers JS ; pas de DB ; pas de connecteurs.

2. **Alignement API (renfort)**
   Faire appliquer à `ExternalJobController` / `/external-offers` la **même politique** `exclude-sources` / listabilité que le catalogue.
   Impact : backend ; redémarrage 8081 probable → **nécessite autorisation explicite** + stratégie JAR (pas d’écrasement thin sous JVM).

3. **Tests**
   - Simuler échec catalogue → 0 externe non autorisé.
   - Avec `exclude-sources` actif → `/external-offers` ne renvoie pas les sources exclues.
   - Compteur offres démo inchangé (123) ; connecteurs restent `enabled=false`.

4. **Hors scope B0**
   Réactivation connecteurs, fixtures, L1/L7 empty-state UI polish (message « Aucune source externe active ») sauf si demandé.

**Recommandation :** démarrer par **(1) fail-closed FE** seul si contrainte « pas de redémarrage 8081 » ; ajouter **(2)** dès qu’un cutover backend est autorisé.

---

## 7. Suite

L0 **terminé**. Aucun autre lot (L1+, B0 appliqué) sans nouvelle autorisation.
