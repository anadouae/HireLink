# HireLink — Plan Phase 3 : catalogue unifie Maroc + international

**Mode :** etude et planification uniquement (lecture seule) — **aucune implementation**  
**Date :** 2026-11-13  
**Autorisation :** creation de ce document uniquement  
**Orientation produit validee :** s'inspirer du *fonctionnement* d'agregation / recherche type PFE Daba, **sans** reproduire son design ; conserver l'identite HireLink, l'ATS bilaterial, les abonnements et le matching existants.

**Entrees verifiees :** code frontend (`candidate-home.js`, `landing-offers.js`, `offer-card.js`), APIs `GET /offers/public` et `GET /external-offers`, Phase 1 UrlQuality, Phase 2 connecteurs ATS, `PlaceCatalogService`, docs Jooble (`ma.jooble.org/api`), etat juridique Phase 2.

**Legende :** **FAIT** · **DOC PUBLIQUE** · **HYPOTHESE** · **A CONFIRMER** · **HORS SCOPE** (scrape / sans droit)

---

## 1. Resume executif

Aujourd'hui, le candidat charge **tout** le catalogue en JavaScript (`/offers/public` + `/external-offers`), puis filtre cote navigateur. Cela ne tiendra pas un catalogue mondial/marocain a volume. Les offres externes Phase 1–2 existent, mais les connecteurs ATS restent **off** et le sample Maroc est souvent en quarantaine (homepages).

**Phase 3 proposee = fusion progressive de :**
1. **Couverture Maroc** (sources autorisees, geo MA, deep links).
2. **Catalogue unifie** avec pagination serveur et filtres (ex-Phase 4 du plan global).
3. **Section « Pour toi »** basee uniquement sur le matching deja fiable — jamais de score invente.

**Inspiration PFE Daba (comportement, pas UI) :** recherche multi-criteres, volume, provenance visible, lien vers l'annonce.  
**Non-objectifs :** clone graphique, scrape Rekrute/Emploi.ma/Stagiaires.ma, partenariats inventes, modification du pipeline ATS interne.

---

## 2. Audit de l'existant (FAIT)

### 2.1 Frontend

| Surface | Comportement actuel | Limite |
|---|---|---|
| `candidate-home.js` | Fusion client de offres internes + externes ; filtres ville/contrat/mode/origine/depuis/match ; tri `matchScore` | Tout en memoire ; pas de pagination serveur |
| Toggle « match » | `matchesProfile` = externe avec `matchAvailable` et score ≥ 50 ; internes via score API | Seuil 50 hardcode FE ; externe sans `requiredSkills` → pas de score |
| `landing-offers.js` | Charge public + externes ; filtres q/ville/contrat/origine/depuis ; `PAGE_SIZE = 9` « Afficher plus » | Pagination **UI** sur liste deja complete |
| `offer-card.js` | Distinction externe ; CTA Phase 1 (`linkReliable` / DEEP) | OK a conserver |
| Design | Identite HireLink (cards, chips, shells) | Ne pas importer le look PFE Daba |

### 2.2 APIs

| Endpoint | Pagination | Filtres serveur | Matching |
|---|---|---|---|
| `GET /api/v1/offers/public` | Non (liste complete) | Non | Score si candidat + competences |
| `GET /api/v1/external-offers` | Non | Non (hors quarantaine seulement) | Score **uniquement** si `skills` source non vide + competences confirmees |
| Admin / pipeline / applications | Inchanges | — | Hors catalogue |

### 2.3 Donnees et geo

- Tables separees : `job_offers` vs `external_job_offers` (**FAIT**, a conserver).
- `country_code` / `board_key` existent cote modele Phase 2 mais **pas** sur la base demo `hirelink` tant que V004/V005 non appliquees.
- `PlaceCatalogService` connait deja Casablanca, Rabat, Tanger, Tetouan, Marrakech, Fes, Agadir, etc. (**FAIT**).

### 2.4 Ecart vs objectif produit

| Attendu Phase 3 | Etat |
|---|---|
| Recherche metier / mots-cles / competences | Partiel (client) |
| Filtres pays, ville, date, contrat, mode, fournisseur | Partiel (client) ; pays/fournisseur incomplets |
| Distinction interne / externe | **FAIT** |
| Entreprise + source | **FAIT** |
| Date seulement si verifiee | Partiel (`publishedAt` peut manquer ; UI affiche parfois `createdAt`) |
| Lien annonce exacte | **FAIT** (Phase 1) si DEEP |
| « Pour toi » | Toggle match existant, a formaliser |
| Pagination serveur | **Absent** |
| Expires / doublons / indisponibles | Phase 1 quarantaine + absence 7 j ; dedupe soft cross-source **absent** |

---

## 3. Sources Maroc — exploitabilite reelle

### 3.1 Matrice

| Source | Methode | Villes MA | API / droit | Deep link | Confiance | Decision Phase 3 |
|---|---|---|---|---|---|---|
| **Offres HireLink natives** | BDD `job_offers` | Selon recruteurs inscrits | Interne | Parcours ATS | **FAIT** | **Priorite #1** (toujours) |
| **Jooble MA** | `POST https://ma.jooble.org/api/{key}` | Oui (location = ville/pays) | Cle regionale + ToS API ; 100 req/j free (**DOC PUBLIQUE**) | `link` Jooble (souvent redirect) | **DOC PUBLIQUE** | **A CONFIRMER** (inscription cle + ToS API completes) — meilleur levier volume MA legal |
| **Greenhouse / Lever / Ashby** | Connecteurs Phase 2 | Oui **si** board publie MA/Casa/Rabat… | Allowlist ; ToS multi-board **non tranche** (Lever #86) | Oui (souvent) | **FAIT** tech / **BLOCKED** juridique large | Canary boards MA **apres** go juridique ; sinon rester off |
| **Feed partenaire Maroc** | `MarocPartnerFeedParser` | Oui si contrat | Contrat | Exige deep URLs | Parser **FAIT** ; contrat **absent** | Pipeline commercial **hors code** |
| **Sample `MAROC_EMPLOI`** | Classpath JSON | Fictionnel | N/A | Souvent homepage → quarantaine | **FAIT** | Retirer de la vitrine prod ; eventuel mode `is_demo` |
| **Stagiaires.ma** | Site HTML | Fort (stages/PFE) | **Pas d'API publique prouvee** | — | **HORS SCOPE** sans accord | Contacter partenariat **ou** ignorer |
| **Rekrute / Emploi.ma** | HTML / scrapers tiers | Fort | Pas d'API officielle HireLink ; scrape courant chez tiers | — | **HORS SCOPE** | Interdit sans contrat |
| **Indeed.ma / LinkedIn** | — | — | Pas d'API agregation autorisee ici | — | **HORS SCOPE** | — |
| **Pages carrieres employeurs** | ATS publics (Phase 2) | Variable | Via board token | Oui | **FAIT** tech | Preferer a tout scrape local |
| **ANAPEC / France Travail-like** | — | — | Non prouve dans le depot | — | **A CONFIRMER** | Veille seulement |

### 3.2 Strategie Maroc recommandee (sans inventer)

1. **Court terme (tech seule) :** catalogue unifie + filtre `country=MA` / villes catalogue ; mettre en avant offres HireLink MA ; activer **0 ou 1** board ATS MA si juridique OK.  
2. **Court terme (ops) :** demander cle **Jooble MA** + lire ToS API ; prototyper en schema isole.  
3. **Moyen terme :** 1–2 partenariats feed (Stagiaires.ma / board local) avec deep links contractuels.  
4. **Jamais :** scrape Rekrute, Emploi.ma, Stagiaires.ma, Indeed, LinkedIn.

### 3.3 Villes cibles

Utiliser le catalogue existant : Tanger, Tetouan, Casablanca, Rabat, Marrakech (+ Fes, Agadir, …).  
Normalisation : fold accents (`Tetouan`/`Tétouan`), `country_code=MA`, champ `city` dedie (migration si besoin).

---

## 4. Restrictions juridiques des connecteurs Phase 2

| Connecteur | Usage prevu editeur | Agregation multi-boards HireLink | Recommendation |
|---|---|---|---|
| Greenhouse Job Board API | Site carrieres **de l'employeur** (**DOC PUBLIQUE**) | Non explicitement un « partenariat Greenhouse » | Garder allowlist courte ; pas de claim ; **A CONFIRMER** counsel avant activation demo |
| Lever Postings API | Job site de l'entreprise ; issue **#86** usage tiers **ouverte** | **BLOCKED** / prudent | Rester `enabled=false` jusqu'a reponse / avis juridique |
| Ashby Job Postings API | « Your organization » careers (**DOC PUBLIQUE**) | Prudence multi-board | Idem — canary 1 board max apres go |
| Jooble REST MA | Explicitement pour portails / moteurs affichant resultats Jooble | Aligne produit (**DOC PUBLIQUE**) | **Meilleur candidat legal volume MA** sous cle + quotas + attribution liens Jooble |

**Principe :** « API publique » ≠ « droit illimite de republier ». Attribution + lien sortant + pas de POST apply HireLink vers ATS tiers.

---

## 5. Architecture cible — recherche et pagination

### 5.1 Endpoint unifie (propose)

`GET /api/v1/catalog/offers`

Parametres (exemple) :

| Param | Role |
|---|---|
| `q` | Titre, entreprise, competences, description (full-text simple puis evolutif) |
| `country` | Ex. `MA` (defaut produit possible) |
| `city` | Ville normalisee |
| `contract` | CDI, CDD, Stage… |
| `workMode` | ONSITE / HYBRID / REMOTE (interne) ; mappe si fourni en externe |
| `source` | `HIRELINK` \| `GREENHOUSE` \| `LEVER` \| `ASHBY` \| `JOOBLE` \| `MAROC_EMPLOI`… |
| `origin` | `internal` \| `external` \| `all` |
| `sinceDays` | Fraicheur **uniquement** si `publishedAt` non null |
| `forYou` | `true` → applique regles « Pour toi » |
| `page`, `size` | Pagination serveur (size max 50) |

Reponse :

```json
{
  "page": 0,
  "size": 20,
  "totalElements": 1234,
  "totalPages": 62,
  "items": [ /* DTO unifie */ ]
}
```

DTO unifie (champs communs) : `id`, `external`, `title`, `companyName`, `location`, `countryCode`, `city`, `contractType`, `workMode`, `source`, `sourceLabel`, `publishedAt` (nullable), `publishedAtTrusted` (bool), `originUrl`/`applyUrl` ou parcours interne, `urlQuality`, `linkReliable`, `matchScore` (nullable), `matchAvailable`.

### 5.2 Couche service

```
CatalogQueryService
  |- queryInternal(JobOfferSpec)
  |- queryExternal(ExternalJobOfferSpec)   // available, !quarantined, UrlQuality listable
  |- mergeSortPage(...)                   // tri : forYou > publishedAt > id
  |- attachMatch(user)                    // SkillMatchingService existant seulement
```

**HYPOTHESE technique :** merge-pagination de deux sources (interne + externe) via fetch page elargie + coupe, acceptable jusqu'a ~10k lignes ; au-dela, vue SQL / table de projection (`catalog_entries`) en Phase 5.

Conserver `GET /offers/public` et `GET /external-offers` en **legacy** (flag ou deprecation douce) pour ne pas casser la demo.

### 5.3 Frontend (identite HireLink)

- Remplacer le double fetch massif par appels pages `catalog/offers`.
- Filtres → query params (pas seulement state local).
- « Afficher plus » / pages numerotees = vraie page serveur.
- Badges : `Offre HireLink` vs `Offre externe` + `sourceLabel` (deja proche).
- Date : afficher **seulement** si `publishedAtTrusted` ; sinon omettre (pas de faux « il y a X jours » sur `createdAt` sync).
- **Ne pas** reprendre la charte PFE Daba.

### 5.4 Qualite / expires / doublons

Reutiliser Phase 1 : quarantaine, pas de suppression.  
Phase 3 ajoute :

- Filtre catalogue : exclure `GENERIC_HOME` / `INVALID` / `quarantined` / `available=false`.
- Soft-dedupe **affichage** (HYPOTHESE) : meme `originUrl` normalisee ou hash titre+entreprise+ville → garder HireLink puis meilleur `urlQuality`.
- Jooble : traiter `updated` comme fraicheur Jooble, pas comme preuve d'existence employeur ; `link` peut etre page Jooble (DEEP Jooble ≠ annonce employeur) — appliquer UrlQuality + copy honnete « via Jooble ».

---

## 6. « Pour toi » — fonctionnement precis

### 6.1 Objectif UX

Section ou mode qui ne montre que des offres **pertinentes pour le profil**, sans inventer de compatibilite.

### 6.2 Regles (proposees)

| Cas | Inclusion « Pour toi » | Score affiche |
|---|---|---|
| Candidat sans competences confirmees | Section vide + CTA profil | Aucun |
| Offre **interne** avec evaluation `SkillMatchingService` | Si score ≥ seuil configurable (defaut **50**, aligne FE actuel) | Score reel |
| Offre **externe** avec `skills` source non vides + matchAvailable | Si score ≥ seuil **et** label « indicatif » | Score indicatif existant |
| Offre externe **sans** skills fiables | **Exclue** de « Pour toi » | Pas de score fabrique |
| Offre sans `linkReliable` (externe) | Exclue ou rangee « a verifier » hors Pour toi | — |

Interdit : generer un score a partir du seul titre, de la ville, ou d'une IA non deja branchee sur ce parcours.

### 6.3 Implementation

- Param `forYou=true` sur `catalog/offers` (calcul serveur pour pagination correcte).
- FE : onglet / toggle « Pour toi » (evolution du toggle match actuel), copy claire.
- Ne pas modifier les scores deja deposes sur `applications`.

---

## 7. Modifications prevues (quand implementation autorisee)

| Domaine | Travaux | Complexite |
|---|---|---|
| API | `CatalogQueryService` + controller + DTOs + specs JPA | **M** |
| DB | Index `(available, country_code, published_at)` ; eventuel `city` ; **pas** sur `hirelink` sans OK | **S–M** |
| Jooble | Connecteur + secrets via config locale ; flags off ; quotas | **M** |
| ATS MA | Allowlist boards MA ; filtre location/country a l'import | **S** (si juridique OK) |
| Sample Maroc | Flag demo / retrait vitrine | **S** |
| FE candidat + landing | Consommation catalogue page ; filtres ; Pour toi ; dates honnetes | **M** |
| Tests | Voir §8 | **M** |
| Docs / admin | Sources, compteurs Jooble, message « peu d'offres MA » | **S** |

**Non modifies :** pipeline ATS, candidatures, billing, secrets en clair, design system HireLink (hors composants catalogue).

---

## 8. Tests et criteres d'acceptation

### 8.1 Tests obligatoires (implementation future)

- Pagination : `size` respecte ; page hors borne → vide / 400.
- Filtres combines `country=MA` + `city=Casablanca` + `origin=external`.
- `q` trouve titre / competence sans fuite cross-tenant.
- `forYou=true` : 0 offre si pas de skills ; pas de score invente.
- Interne : apply HireLink OK ; externe : pas d'application `ext-*`.
- Date absente → pas d'affichage « il y a X jours » trompeur.
- Homepage / quarantaine absentes du catalogue.
- Soft-dedupe : pas deux cartes identiques URL.
- Non-regression matching + pipeline.
- Jooble (si active) : mock 200/403/429 ; cle absente → connecteur off.

### 8.2 Acceptation produit

1. Catalogue unifie utilisable avec **≥ 1 page** serveur stable.  
2. Filtre Maroc / villes du catalogue fonctionnel.  
3. Message honnete si volume externe MA faible.  
4. Distinction visuelle HireLink vs externe + nom source.  
5. CTA externe = deep link fiable uniquement.  
6. « Pour toi » sans scores fantomes.  
7. Identite visuelle HireLink preserve (pas de clone PFE Daba).  
8. Demo / `hirelink` non casses (isolation comme Phases 0–2).

---

## 9. Plan de realisation progressif

| Etape | Contenu | Complexite | Dependances / gates |
|---|---|---|---|
| **3.0** | Gate juridique : Jooble ToS+cle ; avis ATS multi-board ; decision sample | **S** (0,5 j + delai externe) | **Bloquant** |
| **3.1** | API `catalog/offers` + pagination + filtres sur donnees deja en base (HireLink + externes Phase 1/2) | **M** (2–3 j) | Schema isole d'abord |
| **3.2** | FE candidat/landing branches sur catalogue ; Pour toi serveur ; dates honnetes | **M** (2–3 j) | 3.1 |
| **3.3** | Normalisation geo MA (`country`/`city`) + message volume faible + retrait sample vitrine | **S–M** (1–2 j) | 3.1 |
| **3.4** | Connecteur Jooble MA (flags off, tests mock, quota) | **M** (2–3 j) | Cle + ToS OK |
| **3.5** | Canary 1–N boards ATS avec offres MA verifiees | **S–M** (1–2 j) | Juridique ATS OK |
| **3.6** | Soft-dedupe + index perf + doc admin | **S** (1 j) | Volume reel |

**Effort indicatif total implementation :** **M–L ≈ 9–14 j-ingénieur** hors delais juridiques/commerciaux.  
**Hors scope Phase 3 :** scrape boards locaux ; redesign PFE Daba ; Adzuna sans licence ; Phase 5 projection catalogue massive.

---

## 10. Decisions bloquantes (attendre autorisation explicite)

Avant toute implementation, trancher :

1. **Jooble MA :** autoriser la demande de cle API et l'integration sous flags off ?  
2. **ATS Phase 2 :** rester off jusqu'a counsel, ou canary 1 board MA documente ?  
3. **Defaut geo UI :** `country=MA` par defaut pour candidats localises Maroc ?  
4. **Seuil « Pour toi » :** confirmer 50 ou autre valeur produit ?  
5. **Sample Maroc :** retrait vitrine vs badge DEMO explicite ?  
6. **Schema d'implementation :** continuer sur base isolee (`hirelink_ext_phase03`) avant tout contact `hirelink` / demo 8081 ?  
7. **Fusion Phase 3+4 :** confirmer que pagination/FE font bien partie de **cette** Phase 3 (recommandé : **oui**).

---

## 11. Risques

| Risque | Mitigation |
|---|---|
| Volume MA externe faible | Message UX + priorite offres HireLink ; Jooble si legal |
| Pression scrape Rekrute/Emploi.ma | Interdit dans ce plan ; partenariats seulement |
| ToS Jooble / attribution | Lire ToS API completes avant code prod ; lien Jooble conserve |
| Perf merge deux tables | Index + size cap ; projection plus tard |
| Confusion date sync / publication | `publishedAtTrusted` |
| Derive design | Contrainte explicite : composants HireLink only |

---

## 12. Gate

Ce document est le livrable Phase 3 **planification**.  
**Aucune** modification code/DB/demo/secrets. **Aucun** commit/push.

**Stop** — attendre autorisation explicite pour demarrer l'etape d'implementation (idealement 3.0 decisions puis 3.1 sur schema isole).