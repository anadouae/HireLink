# HireLink — Préparation finale d'intégration Phases 1–3

**Date :** 2026-11-13  
**Référence audit :** `docs/GLOBAL_JOB_AGGREGATION_PRE_DEPLOYMENT_AUDIT.md` (READY WITH CONDITIONS)  
**Autorisations respectées :** aucune migration sur `hirelink`, aucun redémarrage de `8081`/`5500`, aucun déploiement, Jooble/ATS OFF, aucun commit/push, secrets inchangés.

Légende : **DONE** · **READY** · **BLOCKED (attente autorisation)** · **RISQUE**

---

## 1. Synthèse

Les prérequis techniques d'intégration sont **préparés et éprouvés en isolation**. La démo principale n'a pas été modifiée.

| Prérequis | Statut |
|---|---|
| Login candidat/recruteur formulaire réel (sans injection de session) | DONE |
| Sélecteur pays candidat (`#f-country` MA / ALL) | DONE |
| Backup complet vérifiable de `hirelink` | DONE |
| Restauration isolée vérifiée | DONE |
| Migrations V004–V006 sur copie **complète avec données** | DONE |
| Correction compatibilité JPA `run_id` VARCHAR | DONE |
| Plan de bascule ordonné + rollback | READY (doc) |
| Checklist validation finale | READY (doc) |
| Migration réelle sur `hirelink` | BLOCKED (attente autorisation) |
| Redémarrage `8081` / bascule FE démo | BLOCKED (attente autorisation) |

**Verdict préparation :** les conditions préalables techniques sont **satisfaites**.  
**Bascule démo :** **non autorisée** tant que vous n'avez pas donné l'accord final explicite.

---

## 2. Intégrité de la démo (fin de préparation)

| Contrôle | Résultat |
|---|---|
| `8081` | LISTEN (non redémarré) |
| `5500` | LISTEN (non redémarré) |
| `job_offers` | 118 |
| `external_job_offers` | 106 |
| `users` | 126 |
| `applications` | 220 |
| Tables `catalog_%` sur `hirelink` | **absentes** |
| Colonne `url_quality` sur `hirelink` | **absente** |

Ports isolés utilisés puis arrêtés : `18082` + `5502`.

---

## 3. Login navigateur (sans injection de session)

**Environnement :** backend `18082` ? `hirelink_deploy_prep_full` (copie migrée) ; FE `5502` ; Edge via Playwright (`channel: msedge`), **remplissage réel** de `#email-pw` / `#password` après onglet Connexion.

**Preuve :** `docs/deploy-prep/screenshots/recette.json` + captures `01`–`08`.

| Parcours | Résultat |
|---|---|
| Candidat formulaire ? dashboard | OK ? `candidate-dashboard.html`, rôle CANDIDAT |
| Recruteur formulaire ? espace recruteur | OK ? `recruiter.html`, rôle RECRUTEUR |
| Page offres recruteur authentifiée | OK |
| Injection `hirelink.token` | **non utilisée** pour ces tests |

**Éclaircissement technique :** en mode Connexion, le panneau actif est `#form-password` (`#email-pw` + `#password`), pas `#email` (panneau inscription). Les échecs d'audit précédents venaient de mauvais sélecteurs / CSP, pas d'un formulaire cassé.

**CSP :** `auth.html`, `candidate.html`, `recruiter-offers.html`, `index.html` autorisent aussi `18081`/`18082` en `connect-src`/`img-src` pour les recettes isolées. La démo `8081` reste autorisée. À revoir pour la production (hôte API unique).

---

## 4. Sélecteur de pays (UI candidat)

| Point | Détail |
|---|---|
| Élément | `#f-country` / `name="country"` / `data-testid="catalog-country"` |
| Valeurs | `MA` (défaut), `ALL` (« Tous les pays (international inclus) ») |
| Branchement API | `buildCatalogQuery` ? `country=` ; contrôleur mappe `ALL`/`TOUS`/`*` ? pas de filtre |
| Correction UI | libellé ALL clarifié ; résumé filtres affiche « Maroc » / « tous pays » |
| Preuve navigateur | `03-country-all.png`, `04-country-ma.png` — 107 offres indexées sur la copie |

Le faux négatif d'audit (« no country select ») venait de sélecteurs de test incorrects (`#country-filter`), pas d'absence UI.

---

## 5. Sauvegarde `hirelink` (sans modification de l'originale)

| Attribut | Valeur |
|---|---|
| Fichier | `docs/deploy-prep/hirelink-full-20261113-201206.sql` |
| Taille | 2 042 591 octets |
| SHA-256 | `5C7AE8DB58AE6FE59EF6A5B4E4DC462043C86A8056CB6AE21310565C09EB1940` |
| Méthode | `mysqldump --single-transaction --routines --triggers --events --hex-blob` |
| Meta | `docs/deploy-prep/hirelink-full-20261113-201206.meta.txt` (compteurs uniquement) |

**Aucune donnée personnelle ni secret n'est recopié dans ce rapport** (pas d'e-mails, mots de passe, jetons).

Comptes présents dans le dump (compteurs) : 118 offres, 106 externes, 126 users, 220 applications, 42 organisations, 8 factures.

---

## 6. Restauration isolée

| Étape | Résultat |
|---|---|
| Restore ? `hirelink_deploy_prep_full` | Compteurs = source (118/106/126/220/8) |
| Seconde restore ? `hirelink_deploy_restore_check` | Idem (preuve de rejouabilité) |
| `hirelink` après restore | Inchangé (118 offres, pas de catalogue) |

---

## 7. Migrations V004–V006 sur copie complète (données)

Base : `hirelink_deploy_prep_full`.

### Avant

- `external_id` : `varchar(80)`, longueur max observée **21** ? élargissement V005 sûr.
- Pas de `url_quality` / `board_key` / `catalog_%`.

### Après V004?V005?V006

| Contrôle | Résultat |
|---|---|
| Colonnes qualité URL | présentes ; défaut `UNKNOWN` ; 106/106 non null |
| `quarantined` | 106 à 0 |
| `external_id` | `varchar(160)` |
| `external_job_boards` | table créée (0 lignes, flags OFF) |
| `catalog_listings` / `catalog_for_you_run` | créées |
| Index `idx_external_offer_quarantine`, `idx_external_offer_board`, index catalogue | présents |
| Doublons `(source, external_id)` | 0 |
| Applications / offres internes | 220 / 118 conservées |
| Réapplication (idempotence) | exit 0 |

### Défaut corrigé pendant la préparation

| Problème | Impact | Correction |
|---|---|---|
| `catalog_for_you_run.run_id` en `CHAR(36)` | `ddl-auto=validate` / Hibernate attend `VARCHAR(36)` | V006 aligné sur `VARCHAR(36)` ; `ALTER` appliqué sur la copie |

**Conséquence déploiement :** appliquer la V006 **corrigée** (déjà dans le dépôt de travail). Ne pas redémarrer le nouveau JAR sur `hirelink` avec une ancienne V006 `CHAR`.

### Reindex sur copie migrée

`POST /catalog/reindex` (admin) ? **107** listings.  
`GET /catalog/offers?country=MA` et `ALL` ? total 107.

---

## 8. Dépendances FE / BE / migrations

```
V004 (url_quality, quarantaine)
  ?? requis par entités ExternalJobOffer + filtres catalogue
V005 (boards, board_key, external_id 160, country_code)
  ?? requis par connecteurs ATS (même OFF) + entités
V006 (catalog_listings, catalog_for_you_run VARCHAR run_id)
  ?? requis par CatalogOfferService / indexer
Nouveau JAR backend
  ?? refuse de démarrer si colonnes/tables absentes (validate) ou dérive Hibernate
Frontend catalogue (candidate-home + landing)
  ?? appelle /api/v1/catalog/offers ; repli legacy si 404
  ?? CSP doit autoriser l'hôte API
Flags Jooble/ATS
  ?? restent false jusqu'à autorisation + ToS/clé
```

Ordre obligatoire : **backup ? V004 ? V005 ? V006 ? démarrage JAR ? reindex ? smoke FE**.

---

## 9. Plan de déploiement ordonné (à exécuter seulement après autorisation)

### Phase A — Avant bascule (contrôles)

1. Confirmer `8081`/`5500` UP ; noter compteurs (offres, apps, users).  
2. Vérifier SHA-256 du backup encore présent.  
3. Optionnel : freeze écritures métier (maintenance courte).  
4. Copier le JAR actuel `8081` hors ligne (rollback binaire).

### Phase B — Migrations sur `hirelink` (autorisation requise)

1. Appliquer V004 ; contrôler colonnes `url_quality`, `quarantined`.  
2. Appliquer V005 ; contrôler `board_key`, `external_id(160)`, table boards.  
3. Appliquer V006 ; contrôler tables catalogue + `run_id VARCHAR(36)`.  
4. Compteurs métier inchangés (offres/apps).

### Phase C — Backend

1. Arrêter `8081`.  
2. Déployer le nouveau JAR (flags Jooble/ATS **OFF**, `startup-sync=false`).  
3. Démarrer `8081` ; health + login API admin.  
4. `POST /api/v1/catalog/reindex` ; noter `indexed`.  
5. `GET /api/v1/catalog/offers?country=MA&size=5` ? 200 + items.

### Phase D — Frontend

1. Servir le FE catalogue (déjà sur disque pour Live Server `5500` si fichiers à jour).  
2. Vérifier CSP `connect-src` pointe vers l'API réelle.  
3. Smoke navigateur (checklist §11).

### Phase E — Après

1. Comparer compteurs vs Phase A.  
2. Surveiller logs 30–60 min (erreurs sync catalogue, 5xx).  
3. Ne pas activer Jooble/ATS.

---

## 10. Rollback applicable

| Scénario | Action |
|---|---|
| Échec migration avant redémarrage | Ne pas démarrer le nouveau JAR ; restaurer dump sur `hirelink` **seulement si** migration partielle et autorisation ; sinon corriger SQL sur copie d'abord |
| Nouveau JAR instable | Remettre l'**ancien JAR**, redémarrer `8081` ; FE repli legacy (`/offers/public`) si `/catalog/offers` absent |
| Tables catalogue corrompues | `DROP TABLE catalog_for_you_run, catalog_listings;` puis reindex ou rollback JAR |
| Colonnes V004/V005 indésirables | Rollback applicatif d'abord ; DROP colonnes seulement avec plan SQL validé ( irreversible soft ) |
| Données perdues | `mysql hirelink < hirelink-full-….sql` après arrêt API — **dernière option**, autorisation explicite |

Cohérence : les candidatures/offres internes ne dépendent pas de `catalog_listings` (projection). Rollback FE+JAR ancien restaure le comportement pré-Phase 3 sans perdre les listings.

---

## 11. Checklist de validation finale (post-bascule)

- [ ] Connexion candidat (formulaire)  
- [ ] Connexion recruteur (formulaire)  
- [ ] Catalogue MA + pagination  
- [ ] Catalogue ALL (sélecteur pays)  
- [ ] Recherche mots-clés  
- [ ] Mode Pour toi (?50 %, pas d'HTTP 400)  
- [ ] Détail offre + scores / labels  
- [ ] Candidature offre interne  
- [ ] Lien offre externe (pas de candidature HireLink)  
- [ ] Publication / modification / clôture recruteur ? catalogue à jour  
- [ ] Pipeline ATS existant  
- [ ] Facturation / abonnements (smoke)  
- [ ] Compteurs DB vs pré-bascule  
- [ ] Jooble/ATS toujours OFF  

---

## 12. Fichiers / artefacts de préparation

| Artefact | Rôle |
|---|---|
| `docs/deploy-prep/hirelink-full-*.sql` + `.meta.txt` | Backup + empreinte |
| `hirelink_deploy_prep_full` | Copie migrée + données |
| `hirelink_deploy_restore_check` | Seconde restore |
| `backend/application-deploy-prep.properties` | Profil isolé (ne pas utiliser sur démo) |
| `docs/deploy-prep/login-country-recette.mjs` | Recette login + pays |
| `docs/deploy-prep/screenshots/*` | Preuves navigateur |
| `database/migrations/V006__…` | `run_id VARCHAR(36)` corrigé |
| `frontend/app/candidate.html` + `candidate-home.js` | Pays + résumé |
| CSP auth/candidate/recruiter-offers/index | Ports isolés locaux |

Schémas de test antérieurs **non supprimés** (`hirelink_ext_phase03`, `hirelink_ext_perf`, `hirelink_mig_audit_p13`, etc.).

---

## 13. Décisions encore bloquées (votre autorisation)

1. Appliquer V004–V006 sur **`hirelink`**.  
2. Redémarrer **`8081`** avec le nouveau JAR.  
3. Considérer le FE Live Server `5500` comme « déployé » (fichiers déjà sur disque).  
4. Commit / push.  
5. Activation Jooble ou ATS.  
6. Suppression des schémas / dumps d'audit.

---

## 14. Conclusion — conditions préalables

**Oui — toutes les conditions préalables techniques listées dans la mission sont satisfaites** (login réel, pays, backup, restore, migrations sur copie complète, dépendances, plan, rollback, checklist).

**Non — la bascule démo n'est pas exécutée.**  

**Attente de votre autorisation finale explicite avant toute modification de la démo principale (`hirelink` / `8081`).**
