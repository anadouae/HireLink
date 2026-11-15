# HireLink — B0 backend : alignement `/external-offers`

**Date :** 2026-11-15
**Statut :** **PASS (isolé)** — code + tests + smoke port `18083`
**Déploiement 8081 :** **NON effectué** (en attente d’autorisation)

---

## 1. Verdict

| Question | Réponse |
|----------|---------|
| `GET /external-offers` respecte `exclude-sources` ? | **Oui** (même politique que le catalogue) |
| Tests auto | **12/12 PASS** (7 unitaires + 5 intégration isolée) |
| Smoke runtime isolé `:18083` / DB `hirelink_b0_runtime` | **PASS** — `MAROC_EMPLOI` absent, `REMOTE_OK` présent |
| Démo `8081` / base `hirelink` | **Intactes en fin de campagne** — offers=**123**, catalog=**107**, health=**UP** |
| JAR démo | **Non écrasé** — SHA-256 inchangé `C5722732…7A8E` |
| Connecteurs | Restés **désactivés** (props isolées + test sync) |

---

## 2. Correctif

### Politique commune

Nouveau composant `ExternalOfferListability` (source unique) :

- source dans `hirelink.catalog.exclude-sources` → non listable ;
- `available` / `quarantined` / `hiddenByAdmin` ;
- `urlQuality` ∈ `{null, DEEP, UNKNOWN}` (aligné catalogue).

### Consommateurs

| Composant | Changement |
|-----------|------------|
| `CatalogListingIndexer` | délègue `isExternalListable` → `listability.isListable` |
| `ExternalJobController` | filtre `GET /external-offers` via `listability.isListable` (plus seulement quarantaine) |

Plus de divergence : une source exclue du catalogue ne peut plus fuir via le legacy `/external-offers`.

---

## 3. Fichiers modifiés / ajoutés

| Fichier | Action |
|---------|--------|
| `backend/src/main/java/.../ExternalOfferListability.java` | **Ajout** — politique commune |
| `backend/src/main/java/.../CatalogListingIndexer.java` | Refactor injection politique |
| `backend/src/main/java/.../ExternalJobController.java` | Fail-closed aligné catalogue |
| `backend/src/test/java/.../ExternalOfferListabilityTest.java` | **Ajout** — 7 cas unitaires |
| `backend/src/test/java/.../ExternalOffersVisibilityB0IsolationTest.java` | **Ajout** — DB `hirelink_b0_backend` |
| `build-iso/b0-backend/application-b0-iso.properties` | Overlay runtime isolé `:18083` |
| `docs/evidence/b0-backend/*` | Logs / preuves |
| `docs/HIRELINK_B0_BACKEND_REPORT.md` | Ce rapport |

**Non touchés :** secrets, connecteurs activés, migrations sur `hirelink`, JAR `docs/deploy-prep/demo-runtime/hirelink-demo-8081.jar`, lots L1+.

---

## 4. Tests automatisés

```text
mvn -Dtest=ExternalOfferListabilityTest,ExternalOffersVisibilityB0IsolationTest \
    -Dspring-boot.repackage.skip=true test
→ BUILD SUCCESS — Tests run: 12, Failures: 0, Errors: 0
```

| Test | Couverture |
|------|------------|
| `ExternalOfferListabilityTest` (7) | exclus, DEEP/UNKNOWN/null, quarantaine, hide, HOME/INVALID |
| `isolationRefuseBaseDemoEtConnecteursDesactives` | URL DB `hirelink_b0_backend`, connecteurs off |
| `externalOffersExclutMarocEmploiMaisGardeSourceAutorisee` | HTTP `/external-offers` |
| `catalogueEtExternalOffersRestentAlignesSurExcludeSources` | rebuild + search + legacy alignés |
| `quarantaineEtMasquageEtHomeRestentHorsVitrine` | cas limites statut/URL |
| `syncDesactiveeRenvoieConflitSansActiverConnecteurs` | `POST /sync` → 409 |

Preuve : `docs/evidence/b0-backend/mvn-test.log` + surefire reports.

---

## 5. Smoke runtime isolé (`18083`)

| Élément | Valeur |
|---------|--------|
| Port | **18083** |
| Base | **hirelink_b0_runtime** (créée pour le smoke) |
| Lancement | `mvn -DskipTests spring-boot:run` + `application-b0-iso.properties` (classes compilées, **pas** le JAR démo) |
| Seed SQL | 1× `MAROC_EMPLOI` + 1× `REMOTE_OK` (available/DEEP) |
| `GET /external-offers` | **1** offre — `Autorise Remote` / `REMOTE_OK` ; **0** Maroc |
| Arrêt | process iso uniquement ; `18083` down |
| Démo pendant/après | `8081` UP, 123 offres, catalog 107 |

---

## 6. Incident opérationnel (restauré)

Pendant le nettoyage d’un Maven surefire bloqué, le JVM démo `8081` a été interrompu par erreur.

**Restauration immédiate** (sans modifier `hirelink`, sans rebuild du JAR démo) :

```text
java (JDK 23) -jar docs/deploy-prep/demo-runtime/hirelink-demo-8081.jar
  --spring.config.additional-location=file:backend/application-cutover-runtime.properties
→ health UP · offers=123 · catalog=107 · SHA JAR inchangé
```

État final de campagne : démo **UP**. Aucun déploiement du code B0 sur 8081.

---

## 7. Risques / reste

1. **8081 tourne encore l’ancien bytecode** (JAR démo pré-B0) : le FE fail-closed B0 protège déjà ; l’API legacy n’est alignée **qu’après** cutover du nouveau backend.
2. Cutover 8081 nécessite : arrêt contrôlé, démarrage d’un **nouveau** fat JAR isolé (pas `backend/target` thin), smoke, sans migration destructive.
3. `POST /external-offers/sync` reste admin + sources disabled — non activé ici.
4. Après insert SQL brut, le catalogue isolé n’était pas réindexé (`total=0`) : hors scope smoke `/external-offers` ; l’alignement catalogue est couvert par les tests d’intégration.

---

## 8. Demande d’autorisation

B0 backend **validé en isolé**.

**Je n’applique pas** le redémarrage / déploiement sur **8081** sans ton accord explicite.
