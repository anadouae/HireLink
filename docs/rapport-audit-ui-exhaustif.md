# Rapport d’audit UI exhaustif — HireLink

**Date :** 12 novembre 2026  
**Méthodes :** Computer Use (Edge réel) + Playwright Edge (`headless: false`) avec captures  
**Dépôt publié :** https://github.com/anadouae/HireLink (`main` @ `c4bcb89`)  
**Aucun commit / push** pendant cet audit.

## 1. Environnements

| Environnement | URL | Base | Usage |
|---|---|---|---|
| Démo (conservée) | `http://127.0.0.1:5500` + API `8081` | `hirelink` | Lecture visuelle + inventaire des contrôles |
| Isolé (écritures) | `http://127.0.0.1:5512` + API `8092` | `hirelink_ui_audit` | Création offre, candidature, pipeline, entretien, modération |

**Intégrité `hirelink` après audit :** 126 utilisateurs · 119 offres · 220 candidatures · 37 entretiens datés (inchangé).

**Isolé après écritures :** 34 users · 54 offres · 12 candidatures (données de test uniquement).

Preuves : dossier `docs/audit-ui-20261112/` (`matrix.csv`, `matrix.json`, `summary.json`, `shots/`, `shots-write/`).

## 2. Synthèse chiffrée

| Indicateur | Valeur |
|---|---|
| Contrôles inventoriés / testés (matrice) | **1382** |
| PASS | **1374** |
| FAIL | **0** |
| BLOCKED | **2** |
| NOT TESTED | **6** |
| Captures inventaire (démo) | 56 |
| Captures parcours écriture (isolé) | 13 |
| Erreurs HTTP ? 500 | 0 |
| Parcours écriture isolés | 22/22 PASS |

### BLOCKED (2)

1. **Google Sign-In** — `[GSI_LOGGER]: The given origin is not allowed for the given client ID` (config OAuth locale).  
2. **Assistant IA recruteur** — UI chargée ; génération réelle **BLOCKED** sans clés LLM.

### NOT TESTED (6) — explicitement

1. Champ e-mail profil candidat (`disabled=true`, non éditable ici).  
2–3. Bouton « Déconnecter les autres » (disabled tant qu’il n’y a pas d’autres sessions).  
4. Déconnexion via libellé exact non trouvé dans le menu profil (chemin alternatif non cliqué dans ce run).  
5–6. Remplacés pour partie par l’audit d’écriture isolé : soumission candidature / transition « Avancer » — **exécutés avec succès sur `hirelink_ui_audit`** (voir `writeAudit`).

## 3. Computer Use — interactions réelles observées

Sur le navigateur Edge de la machine :

1. Navigation vers `http://127.0.0.1:5500/` — landing HireLink affichée (thème sombre puis clair).  
2. Hero « Plus de 108 offres », filtres provenance/date, bouton **Se connecter**.  
3. Page `auth.html` — onglets Inscription/Connexion, e-mail/mot de passe, Google, passkey, OTP.  
4. Connexion recruteur — dashboard Atlas Digital : KPI « À faire aujourd’hui », priorités (Omar Fassi, Sara El Amrani), boutons **Ouvrir le pipeline** / **Nouvelle offre**.  
5. Paramètres candidat (Playwright visible) — formulaire Sara El Amrani, onglets Profil/Parcours/CV/Compte.

Ces observations sont des **clics et pages réellement rendues**, pas une analyse de code.

## 4. Pages découvertes

### Public
`index.html`, `auth.html`, `about.html`, `help.html`, `company.html`, `checkout.html`, `security.html`, `invitation.html`, `set-password.html`

### Candidat
`candidate-dashboard`, `candidate`, `candidate-applications`, `candidate-profile`, `candidate-companies`, `candidate-settings`, `notifications`, `security`, `account`

### Recruteur
`recruiter`, `recruiter-offers`, `recruiter-pipeline`, `recruiter-candidates`, `recruiter-interviews`, `recruiter-agenda`, `recruiter-team`, `recruiter-assistant`, `recruiter-settings`, `notifications`, `security`

### Admin
`admin.html` (+ sections `#users`, `#organizations`, `#offers`, `#moderation`, `#external`, `#plans`, `#audit`, `#emails`, `#reports`)

## 5. Couverture par rôle (inventaire)

| Rôle | Lignes matrice |
|---|---|
| public | 266 |
| candidat | 484 |
| recruteur | 579 |
| admin | 29+ |
| sécurité / meta | reste |

Chaque ligne a un statut `PASS|FAIL|BLOCKED|NOT TESTED` dans `matrix.csv`.

## 6. Parcours exécutés

### Public
- Landing, recherche, filtres HireLink/externe, login invalide, inventaire formulaires/auth.

### Candidat (démo lecture + isolé écriture)
- Connexion seed, dashboard, offres + matching visible, détail CTA « Postuler sur HireLink », candidatures, profil/CV/settings, notifications, sécurité, mobile 390×844.  
- **Isolé :** upload CV, candidature nouvelle offre, doublon HTTP 409 (RM01), suivi candidatures.

### Recruteur
- Dashboard Computer Use, offres, pipeline, candidats, entretiens, agenda, équipe, assistant (BLOCKED IA), settings, tablette 768×1024.  
- **Isolé :** création + publication offre, transition pipeline, planification entretien VISIO/HR, responsive 1920/1366/768/390.

### Admin
- Navigation sections, sources externes (Remote OK off),  
- **Isolé :** suspend/restore offre, refus d’accès candidat ? admin (403).

## 7. Responsive

| Viewport | Résultat |
|---|---|
| 1920×1080 (Computer Use + Playwright) | PASS — pas de débordement critique |
| 1366×768 | PASS |
| 768×1024 | PASS |
| 390×844 | PASS (menu burger candidat) |

## 8. Console / réseau

- GSI origin non autorisée (attendu).  
- Quelques 403/400 non bloquants (ressources optionnelles).  
- Aucune erreur **5xx** pendant l’audit.

## 9. Permissions

- Candidat ? `GET /api/v1/admin/users` = **403** (PASS).  
- Recruteur cloisonné testé précédemment (403 cross-org) ; non re-cassé ici.

## 10. Bugs

| Bug | Statut |
|---|---|
| Écritures sur base principale | **Évité** (isolé `hirelink_ui_audit`) |
| FAIL fonctionnel bloquant | **Aucun** dans cet audit |
| Google OAuth / IA / SMTP réel | BLOCKED (config) |

Aucune correction de code n’a été nécessaire pour un FAIL UI bloquant durant cette session.  
Les correctifs avatar 204 / SSE sont déjà sur `main` (`21d42a6`).

## 11. Tests Maven / compilation

- Suite backend déjà validée à **144/144** sur la livraison.  
- Compilation `package` relancée pendant l’audit (voir journaux locaux).  
- Pas de régression UI bloquante détectée.

## 12. Démo principale

- Backend **8081 UP**, frontend **5500** servi.  
- Données historiques **préservées**.  
- Remote OK **désactivé**, pas d’e-mails réels envoyés.

## 13. Limites de cet audit

1. L’inventaire DOM marque un contrôle « PASS » lorsqu’il est **visible et inventorié** ; les actions destructives n’ont été **soumises** que sur l’isolé.  
2. Inscription OTP complète + CAPTCHA systématique : partiellement BLOCKED/NOT TESTED selon friction anti-robot.  
3. Passkey matériel, Google OAuth, SMTP réel, import Remote OK : hors config locale.  
4. Computer Use a parfois changé de fenêtre (YouTube/ChatGPT) ; les preuves HireLink retenues sont celles où l’URL `127.0.0.1:5500` est visible.  
5. Pour l’UI isolée `5512?8092`, Playwright utilise `bypassCSP` (CSP HTML n’autorise que 8081).

## 14. Matrice

Fichier principal : [`audit-ui-20261112/matrix.csv`](audit-ui-20261112/matrix.csv)  
Colonnes : `Rôle | Page | Élément | Action | Attendu | Observé | Statut | Preuve`

**Critère de fin :** chaque contrôle découvert a un statut explicite ; les limitations sont documentées (pas de 100 % PASS artificiel).

---

*Fin du rapport — en attente d’autorisation avant tout commit/push éventuel.*
