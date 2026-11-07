# Run lecture seule — HireLink v5 (newman)

Date : 2026-11-06. Collection : `HireLink_v5_lecture_seule.postman_collection.json` — 49 requêtes, uniquement des GET et les trois connexions qui capturent les jetons. Aucune écriture en base.

Commande : `newman run HireLink_v5_lecture_seule.postman_collection.json`
Backend : Spring Boot sur `http://localhost:8081/api/v1`, base MariaDB `hirelink` de démonstration.

| Indicateur | Valeur |
|---|---|
| Requêtes exécutées | 49 (0 échouées) |
| Assertions exécutées | 101 (0 échouées) |
| Statut obtenu = statut attendu | 49 / 49 |
| Temps moyen de réponse | 193 ms (min 7, max 1733) |
| Somme des temps serveur | 9.4 s |

## Détail, requête par requête

| # | Route | Méthode | Attendu | Obtenu | Temps | Taille |
|---|---|---|---|---|---|---|
| 1 | `/api/v1/health` | GET | 200 | 200 | 143 ms | 0.1 Ko |
| 2 | `/api/v1/auth/config` | GET | 200 | 200 | 12 ms | 0.2 Ko |
| 3 | `/api/v1/auth/login` | POST | 200 | 200 | 919 ms | 0.7 Ko |
| 4 | `/api/v1/auth/login` | POST | 200 | 200 | 944 ms | 0.7 Ko |
| 5 | `/api/v1/auth/login` | POST | 200 | 200 | 961 ms | 0.7 Ko |
| 6 | `/api/v1/me` | GET | 200 | 200 | 26 ms | 1.8 Ko |
| 7 | `/api/v1/me/export` | GET | 200 | 200 | 52 ms | 2.1 Ko |
| 8 | `/api/v1/auth/me` | GET | 404 | 404 | 19 ms | 0.1 Ko |
| 9 | `/api/v1/ai/status` | GET | 404 | 404 | 21 ms | 0.1 Ko |
| 10 | `/api/v1/me` | GET | 401 | 401 | 7 ms | 0.1 Ko |
| 11 | `/api/v1/offers/public` | GET | 200 | 200 | 1503 ms | 165.7 Ko |
| 12 | `/api/v1/organizations/public` | GET | 200 | 200 | 296 ms | 17.5 Ko |
| 13 | `/api/v1/organizations/public/company?name=Atlas%20Digital` | GET | 200 | 200 | 1733 ms | 7.9 Ko |
| 14 | `/api/v1/catalog/suggest?kind=contract&q=CD` | GET | 200 | 200 | 9 ms | 0.1 Ko |
| 15 | `/api/v1/catalog/suggest?kind=city&q=Tet` | GET | 200 | 200 | 12 ms | 0.1 Ko |
| 16 | `/api/v1/catalog/suggest?kind=level&q=Bac` | GET | 200 | 200 | 1198 ms | 0.7 Ko |
| 17 | `/api/v1/skills/suggest?q=Spring` | GET | 401 | 401 | 7 ms | 0.1 Ko |
| 18 | `/api/v1/catalog/places` | GET | 200 | 200 | 7 ms | 7.3 Ko |
| 19 | `/api/v1/skills/suggest?q=Spring` | GET | 200 | 200 | 69 ms | 0.4 Ko |
| 20 | `/api/v1/offers/mine` | GET | 200 | 200 | 174 ms | 8.4 Ko |
| 21 | `/api/v1/applications/pipeline` | GET | 200 | 200 | 68 ms | 98.7 Ko |
| 22 | `/api/v1/applications/pace` | GET | 200 | 200 | 16 ms | 0.0 Ko |
| 23 | `/api/v1/applications/mine` | GET | 200 | 200 | 84 ms | 46.3 Ko |
| 24 | `/api/v1/applications/11/timeline` | GET | 200 | 200 | 16 ms | 0.2 Ko |
| 25 | `/api/v1/refusal-reasons` | GET | 200 | 200 | 16 ms | 0.7 Ko |
| 26 | `/api/v1/offers/1` | GET | 200 | 200 | 30 ms | 2.8 Ko |
| 27 | `/api/v1/applications/11/interview.ics` | GET | 404 | 404 | 18 ms | 0.1 Ko |
| 28 | `/api/v1/applications/999999/interview.ics` | GET | 404 | 404 | 13 ms | 0.1 Ko |
| 29 | `/api/v1/organizations/mine` | GET | 200 | 200 | 27 ms | 1.0 Ko |
| 30 | `/api/v1/organizations/mine/members` | GET | 200 | 200 | 21 ms | 0.7 Ko |
| 31 | `/api/v1/organizations/mine/events` | GET | 200 | 200 | 24 ms | 0.0 Ko |
| 32 | `/api/v1/organizations/mine/notes` | GET | 200 | 200 | 22 ms | 0.6 Ko |
| 33 | `/api/v1/notifications` | GET | 200 | 200 | 23 ms | 5.2 Ko |
| 34 | `/api/v1/ai/credits` | GET | 200 | 200 | 22 ms | 0.2 Ko |
| 35 | `/api/v1/billing/plans` | GET | 200 | 200 | 16 ms | 0.6 Ko |
| 36 | `/api/v1/admin/stats` | GET | 200 | 200 | 27 ms | 0.1 Ko |
| 37 | `/api/v1/admin/charts` | GET | 200 | 200 | 38 ms | 0.1 Ko |
| 38 | `/api/v1/admin/organizations` | GET | 200 | 200 | 328 ms | 20.8 Ko |
| 39 | `/api/v1/admin/offers` | GET | 200 | 200 | 54 ms | 45.5 Ko |
| 40 | `/api/v1/admin/reports` | GET | 200 | 200 | 59 ms | 1.5 Ko |
| 41 | `/api/v1/admin/users` | GET | 200 | 200 | 44 ms | 26.4 Ko |
| 42 | `/api/v1/admin/audit` | GET | 200 | 200 | 24 ms | 41.1 Ko |
| 43 | `/api/v1/admin/search?q=Sara` | GET | 200 | 200 | 77 ms | 2.5 Ko |
| 44 | `/api/v1/admin/ai-usage` | GET | 200 | 200 | 99 ms | 0.6 Ko |
| 45 | `/api/v1/admin/stats` | GET | 403 | 403 | 41 ms | 0.0 Ko |
| 46 | `/api/v1/account/security` | GET | 200 | 200 | 17 ms | 0.3 Ko |
| 47 | `/api/v1/account/sessions` | GET | 200 | 200 | 22 ms | 2.7 Ko |
| 48 | `/api/v1/applications/by-offer/15` | GET | 403 | 403 | 37 ms | 0.1 Ko |
| 49 | `/api/v1/applications/by-offer/1` | GET | 200 | 200 | 54 ms | 24.8 Ko |

## Ce que ce run prouve

- **Routes mortes retirées** : `GET /auth/me` et `GET /ai/status` répondent 404.
- **Garde-fous serveur** : `GET /skills/suggest` sans jeton → 401, avec jeton candidat → 200.
- **Entretiens** : `GET /applications/11/interview.ics` → 404 « Aucun entretien planifié » — la base de démo ne contient aucun entretien à cette date ; la voie positive (200) appartient au playbook avec écritures.
- **Isolation inter-organisations** : `GET /applications/by-offer/15` (Maroc Telecom) → 403 pour le recruteur d'Atlas Digital, alors que `GET /applications/by-offer/1` (même organisation) → 200.
- **Séparation des rôles** : `GET /admin/stats` avec un jeton candidat → 403.
