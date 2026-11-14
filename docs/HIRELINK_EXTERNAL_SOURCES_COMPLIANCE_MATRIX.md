# HireLink — Matrice de conformité des sources d'offres externes

**Date :** 2026-11-14  
**Périmètre :** recherche documentaire + sondes HTTP publiques de recherche (hors écriture `hirelink`)  
**Principe :** une API technique publiquement accessible **n'équivaut pas** à une autorisation juridique de republication multi-employeur.

Légende statut : **AUTORISÉ** · **À CONFIRMER** · **NON AUTORISÉ** · **INDISPONIBLE**

---

## Synthèse exécutive

| Source | Couverture MA | Clé | Republication agrégateur | Statut |
|---|---|---|---|---|
| Jooble (`ma.jooble.org`) | Prioritaire (domaine MA) | Oui (par pays) | API destinée aux portails ; ToS site limitent copie sans accord écrit | **À CONFIRMER** (clé + ToS API) |
| Greenhouse Job Board API | Faible (boards employeurs mondiaux) | Non (GET) | Docs = site carrière employeur ; agrégation multi-boards | **À CONFIRMER** |
| Lever Postings API | Partielle (ex. Yassir MENA) | Non (GET) | Docs = job site employeur ; usage tiers non clarifié | **À CONFIRMER** |
| Ashby Job Postings API | Faible | Non (GET) | Docs = peupler le site carrière de **l'organisation** | **À CONFIRMER** |
| RemoteOK | Non (remote international) | Non | Usage historique HireLink ; offres actuellement masquées | **À CONFIRMER** / dégradé |
| MAROC_EMPLOI (classpath) | Démo locale | N/A | **Données d'exemple** — exclus du catalogue public | **NON AUTORISÉ** (pas une vraie source) |
| Rekrute / Emploi.ma / ANAPEC | Oui | Pas d'API officielle HireLink | Scraping / APIs tierces non officielles | **NON AUTORISÉ** (sans partenariat) |
| Writ / JobDataFeeds / scrapers | Variable | Commercial | Tiers, pas partenaires HireLink | **INDISPONIBLE** / hors scope |

**Constat Maroc :** aucune source marocaine **légalement activable immédiatement** sans (a) clé Jooble MA + validation ToS, ou (b) partenariat écrit avec un portail local. Ne pas inventer d'annonces pour « remplir » le catalogue.

---

## 1. Jooble (priorité Maroc)

| Critère | Détail |
|---|---|
| Site / docs | https://ma.jooble.org/api/about · https://help.jooble.org (REST API) |
| Pays | Clé **par domaine régional** ; MA = `ma.jooble.org` |
| Auth | Clé API dans l'URL `POST /api/{key}` |
| Quotas | Plan gratuit documenté : **100 req/jour/clé** (reset UTC) ; HTTP 429 |
| Republication | Page API : destinée aux webmasters/portails pour republier réponses. ToS FR : contenu non copiable sans **autorisation écrite** ; interdiction robots/scraping site |
| Attribution | Liens Jooble (`link`) + mention source recommandée |
| Dates | Champ `updated` (ISO) — ne pas inventer si absent |
| Sync | Possible (pages, keywords, location) |
| Coût | Gratuit limité ; custom limit sur demande |
| Connecteur HireLink | `JoobleJobConnector` — **enabled=false**, clé vide, SSRF allowlist |
| Sonde 2026-11-14 | `GET ma.jooble.org/api/about` → **HTTP 403** depuis cet environnement (WAF) |
| **Statut** | **À CONFIRMER** — activer seulement après clé officielle MA + relecture ToS API / accord |

---

## 2. Greenhouse Job Board API

| Critère | Détail |
|---|---|
| Docs | https://docs.greenhouse.io/job-board |
| Auth GET | Aucune |
| Quotas | Pas de limite dure documentée ; cache fort ; prudence rate |
| Objet juridique | Construire le **site carrière de l'employeur** |
| Agrégation | Non explicitement autorisée pour un agrégateur tiers |
| Liens | `absolute_url` vers l'annonce |
| Dates | Pas toujours de « first published » fiable |
| Connecteur | `GreenhouseJobConnector` + allowlist boards (tous `enabled=false`) |
| Sonde | `GET boards-api.greenhouse.io/v1/boards/stripe/jobs` → **200**, **786** jobs |
| **Statut** | **À CONFIRMER** — canary technique possible ; go juridique requis |

---

## 3. Lever Postings API

| Critère | Détail |
|---|---|
| Docs | https://github.com/lever/postings-api |
| Auth GET | Aucune |
| Hosts | `api.lever.co` / `api.eu.lever.co` |
| Objet | Job site public de l'employeur |
| Maroc / MENA | Board ex. `yassir` (allowlist, OFF) |
| Sonde | `GET api.lever.co/v0/postings/leverdemo?mode=json` → **200**, **10** jobs |
| **Statut** | **À CONFIRMER** |

---

## 4. Ashby Job Postings API

| Critère | Détail |
|---|---|
| Docs | https://developers.ashbyhq.com/docs/public-job-posting-api |
| Auth GET | Aucune (par `JOB_BOARD_NAME`) |
| Objet | Peupler le site carrière **de l'organisation** |
| Agrégation cross-org | Pas de discovery API ; feed partenaire dédié = autre contrat |
| Sonde | `GET api.ashbyhq.com/posting-api/job-board/Ashby` → **200**, **76** jobs |
| **Statut** | **À CONFIRMER** |

---

## 5. RemoteOK

| Critère | Détail |
|---|---|
| URL | `https://remoteok.com/api` (config HireLink) |
| Flag démo | `remoteok.enabled=false` dans properties ; **98** lignes historiques `hidden_by_admin=1` |
| Catalogue | Non listées (masquées) |
| **Statut** | **À CONFIRMER** / gelé — ne pas réactiver sans revue qualité liens |

---

## 6. MAROC_EMPLOI (fixture classpath)

| Critère | Détail |
|---|---|
| Fichier | `feeds/maroc-emploi-partenaires.v1.json` |
| Nature | Exemples locaux ; URLs souvent génériques (ex. page d'accueil Rekrute) |
| Catalogue public | Exclu via `hirelink.catalog.exclude-sources=MAROC_EMPLOI` |
| **Statut** | **NON AUTORISÉ** comme « vraie » source marché |

---

## 7. Portails marocains (Rekrute, Emploi.ma, ANAPEC)

| Critère | Détail |
|---|---|
| API officielle HireLink | **Non documentée** pour republication |
| Alternatives observées | Scrapers GitHub/Apify, API Wrappers commerciaux |
| Position HireLink | **Pas de scraping** ; partenariat écrit requis |
| **Statut** | **NON AUTORISÉ** sans accord |

---

## Décision opérationnelle (sans activation)

1. **Court terme (international canary)** : Greenhouse/Lever/Ashby sur allowlist restreinte, flags OFF jusqu'à OK juridique.  
2. **Court terme (Maroc)** : Jooble MA après clé + ToS ; sinon catalogue = offres **internes** HireLink uniquement.  
3. **Interdit** : scraper Rekrute/Emploi.ma/ANAPEC ; inventer des offres ; présenter MAROC_EMPLOI comme réel.
