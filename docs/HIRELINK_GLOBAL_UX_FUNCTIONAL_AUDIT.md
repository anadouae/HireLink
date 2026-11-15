# HireLink — Audit global UX, métier et fonctionnel

**Date :** 2026-11-14
**Référentiel :** Cahier des charges **v6.3** (`docs/cahier-des-charges/HireLink_Cahier_des_charges_v6_3_finale.docx`)
**Périmètre :** code réel + API live démo 8081/5500 (lecture seule)
**État démo pendant l’audit :** UP · `hirelink.job_offers` = **123** · connecteurs OFF
**Phase :** **AUDIT UNIQUEMENT** — aucune modification produit, aucune migration, aucun commit

---

## Synthèse exécutive

HireLink couvre déjà le cœur CDC v6.3 (comptes, offres, candidatures, pipeline, entretiens, org, admin, matching local, IA à crédits, facturation simulée).
Les frictions manuelles observées se regroupent surtout en :

1. **Cohérence UX / libellés** (score CV vs « à corriger », checklist « vérifiée », plan gratuit au checkout).
2. **Bugs frontend confirmés** (historique dossier cassé, retour page publique + `next=` auth, filtres client vs compteurs serveur).
3. **Décisions produit / config** (filtre Externes = 0 attendu ; TVA non configurée ; coach IA à retirer).
4. **Risques métier pipeline** (refus sans annuler entretien ; notes internes fragiles).
5. **P0 légal / externes** confirmés en consolidation : pages légales en **CP1252** malgré `charset=UTF-8` ; fallback FE `/external-offers` hors politique `exclude-sources`.

**Aucune correction n’a été appliquée.** Autorisation requise avant lots de fix.

Légende sévérité : **P0** bloque confiance / parcours critique · **P1** fort impact UX/métier · **P2** polish / dette.
Types : **Bug** · **UX** · **Évolution** · **Config** (comportement attendu sous réglages actuels).

---

## 1. Inventaire des anomalies (par thème)

### A — Analyse du CV candidat

| ID | Type | P | Anomalie | Preuve / reproduction | Fichiers | Correctif proposé |
|---|---|---|---|---|---|---|
| A1 | Bug | **P0** | Score **100/100 / Niveau A** peut coexister avec boutons **« à corriger »**, y compris sur messages positifs (« Les compétences sont lisibles… ») | Ouvrir profil CV rempli → onglets Compétences/Expériences : score haut + CTA « à corriger » systématique | `frontend/js/candidate-profile.js` (`FINDINGS`, `paintAnalysis`) ; `candidate-profile.html` | N’afficher « à corriger » que si le point est une lacune ; sinon « OK » / rien. Lier le CTA au seuil réel du score. |
| A2 | UX | P1 | Onglet **« Compatibilité »** dans le panneau score CV mélange qualité CV et match offre (le score affiché n’est **pas** le match) | Légende dit « pas la correspondance », mais onglet « Compatibilité » | Idem | Renommer onglet (« Lecture CV ») ; réserver « Compatibilité » aux scores offre (`matchScore`). |
| A3 | UX | P1 | Recommandations non priorisées ; formulations parfois vagues ; pas de coût crédit visible à côté de « Faire la synthèse » avant clic | UI `#cv-coach` | `candidate-profile.html`, `credits.js` | Liste P1→P3 ; afficher coût (`data-ai-cost`) + solde ; états loading/erreur structurés hors log chat. |
| A4 | Évolution | P1 | **Coach CV** (« Interroger le coach ») redondant avec la synthèse ; à supprimer sans toucher `cv-summary` | Formulaire `#coach-form` → `POST /api/v1/ai/coach` | FE : `candidate-profile.html/js` ; BE : `AiAssistController.coach` ; crédits feature `"coach"` | Lot dédié : retirer UI + endpoint + coût catalogue ; conserver `cv-summary`, `profile-rewrite`, assistant recruteur. |
| A5 | Bug / risque | P1 | Prompt coach/synthèse dit « n’invente pas », mais **repli local / LLM cloud** peut encore extrapoler | `AiAssistController` prompts ; `LocalLlmProvider` / `HttpLlmProvider` | Durcir prompt + post-filtre ; badge « interprétation — à relire » ; ne jamais présenter comme fait certifié. |
| A6 | UX | P2 | Score CV **100 % client** (longueur de champs) ≠ extraction serveur ; peut diverger du matching | `scoreBreakdown` | Documenter clairement ; optionnellement aligner seuils sur sections confirmées serveur. |

**Inventaire suppression coach (lot futur) :**
UI `#coach-form`, `#coach-input`, bouton « Interroger le coach », `askCoach()`, `POST /ai/coach`, réservation crédit `"coach"`, textes settings renvoyant au coach si distincts de la synthèse. **À garder :** `#coach-analyze` / `cv-summary`, rewrite profil, assistant recruteur.

---

### B — Recherche, filtres, offres externes

| ID | Type | P | Anomalie | Preuve | Fichiers | Correctif proposé |
|---|---|---|---|---|---|---|
| B1 | Config | — | Filtre **Externes → 0** | Live : `GET /catalog/offers?origin=EXTERNAL` → `totalElements=0` ; DB `catalog_listings` origines = `INTERNAL` seulement ; connecteurs `enabled=false`, `startup-sync=false` | `application.properties` / cutover-runtime ; `CatalogOfferService` | **Attendu**. Expliquer en UI (« Aucune source externe active »). N’activer aucun connecteur sans droits + autorisation. |
| B0 | Bug | **P0** | Fallback legacy si catalogue KO : FE appelle `/offers/public` + `/external-offers`, qui **n’applique pas** `exclude-sources` (ex. fixtures `MAROC_EMPLOI`) → risque d’afficher des externes exclus du catalogue | `landing-offers.js` ~149–163 ; `candidate-home.js` ~489–500 ; `ExternalJobController` ; indexer catalogue | Fail-closed ou aligner `/external-offers` sur la même politique listabilité / exclude-sources. **Ne pas** réactiver de fixtures. |
| B2 | Bug | **P0**/P1 | Autocomplete lieux : `GET /catalog/places` renvoie **tout le catalogue** (y compris villes hors pays, villes vides, FR/US…) ; param `country` **ignoré** | Live `places?q=TA&country=MA` → Paris, New York, `city:""`… | `CatalogSuggestController.allPlaces` ; `PlaceCatalogService` ; FE `places.js` | Endpoint `places?q=&country=` filtrant code pays ; min. 2–3 car. ; pas d’entrées pays sans ville pour filtre ville ; ne jamais créer « TA ». |
| B3 | Bug | P1 | Filtres **compatibilité min** et **candidature** calculés **côté client** alors que le total affiché vient du serveur → compteurs / pagination incohérents | `candidate-home.js` `FILTERS`, filtre local vs `totalElements` | Pousser au serveur ou recalculer le résumé après filtre local ; pagination cohérente. |
| B4 | UX | P1 | Facettes ville dérivées de la page courante (pas du corpus filtré global) | `candidate-home.js` | Facettes serveur ou dataset dédié. |
| B5 | UX | P2 | Reset filtres remet pays à MA ; « Pour toi » en sessionStorage mais autres filtres non dans l’URL | Idem | Persister filtres utiles en querystring. |
| B6 | Config / légal | P1 | Connecteurs : allowlist ATS, URL quality, feeds ; **désactivés** en démo | Logs `0 actif(s)` ; props `external-jobs.*.enabled=false` | Documenter conditions d’activation (droits source, keys, admin) ; **ne pas inventer d’offres**. |

**Pourquoi Externes = 0 (confirmé) :** catalogue peuplé uniquement d’offres internes ; sync externe coupée ; fixtures Maroc Emploi exclues du catalogue ; RemoteOK/autres masqués ou off. Ce n’est pas un bug de filtre SQL isolé.

---

### C — Navigation et affichage

| ID | Type | P | Anomalie | Preuve | Fichiers | Correctif proposé |
|---|---|---|---|---|---|---|
| C1 | Bug | **P0** | « Voir la page publique » sans `returnUrl` ; retour hardcodé landing ; « Mon espace » → home rôle, pas la page d’origine | `recruiter-team.js` ~697 ; `candidate-companies.js` ~181 ; `company.html` `.co-back` ; `public-nav.js` | Passer `from=` / `return=` ; back dynamique ; fallback `referrer` same-origin. |
| C2 | Bug | **P0** | Guest « Suivre » : `auth.html?…&next=company.html?name=…` mais `nextTarget()` n’autorise que `app/{role}*.html` et préfixe `app/` → post-login **dashboard**, pas la fiche entreprise | `company.js` ~101–102 ; `auth.js` `nextTarget` ~339–351 | Autoriser cibles publiques sûres (`company.html?…`) sans forcer `app/` ; ou enchaîner login → company → app. |
| C2b | UX | P1 | Nav publique : `.nav { display:none }` ≤720px **sans** menu burger sur `company.html` | `landing.css` / pages publiques | Menu mobile public (pattern landing) ou liens essentiels + Mon espace. |
| C3 | UX | P1 | Filtres recherche non conservés à la navigation aller-retour | `candidate-home.js` | Querystring + restore. |
| C4 | UX | P1 | Mobile : master/detail offres — risque de perdre le contexte liste ; toolbars/chips overflow | `app.css` / `shell.css` media queries | Audit viewport 375/768/1280 ; scroll horizontal chips ; empty states. |
| C5 | UX | P2 | Modales / z-index / menus `rc-more` : risque de coupe selon conteneur `overflow:hidden` | `shell.css`, `ui.js` | Checklist visuelle systématique. |

---

### D — Dossier recruteur, matching, pipeline

| ID | Type | P | Anomalie | Preuve | Fichiers | Correctif proposé |
|---|---|---|---|---|---|---|
| D1 | Bug | **P0** | Bouton historique appelle `showTimeline(c.id)` **mais la fonction n’existe pas** → `ReferenceError` | `recruiter-pipeline.js:817` ; aucune `function showTimeline` ; backup dans `docs/dead-code/…bak-lotA` | Restaurer modal timeline via `GET /applications/{id}/timeline`. |
| D2 | Bug | **P0** | Passage à **REJECTED** n’annule pas l’entretien actif → candidat peut encore voir convocation / .ics | `ApplicationService.advance` | Sur refus : annuler entretien + notifier clairement. |
| D3 | UX / Bug | P1 | Checklist pré-coche « **Identité vérifiée** » si nom+email présents — libellé **surpromesse** (pas une vérification d’identité) | `recruiter-pipeline.js` ~521 | Renommer (« Identité renseignée ») ; ne cocher auto que des faits techniques. |
| D4 | Bug | P1 | Note d’avance / notes internes : risque d’écrasement / exposition selon champs `advanceNote` vs notes org | `ApplicationService.advance` ; pipeline UI | Clarifier permissions ; historique append-only. |
| D5 | Bug | P1 | `firstDecisionAt` surtout posé sur **REJECTED** — délais / SLA « 1ʳᵉ réponse » potentiellement faussés pour autres décisions | `ApplicationService` | Poser à la première transition hors SUBMITTED. |
| D6 | UX | P1 | Score au dépôt vs score actuel : présents en DTO/export, explication dense dans le dossier | Pipeline + matching docs | Bloc court : « Au dépôt X % · Actuel Y % · pourquoi ». |
| D7 | UX | P2 | « Avancer » = transition d’étape contrôlée (`ALLOWED`) + checklist 3 cases + motif si refus | OK métier ; manque aide contextuelle | Microcopie : conséquences (notif candidat, motif obligatoire, entretien). |

**« Avancer » (comportement confirmé) :**
Valide transition `ApplicationStatus` autorisée → exige `confirmIdentity` / `confirmCvReviewed` / `confirmSlaAware` → refus ⇒ motif catalogue + `firstDecisionAt` → timeline + notif statut (+ e-mail si SMTP et opt-in).

---

### E — Notifications

| ID | Type | P | Anomalie | Preuve | Correctif |
|---|---|---|---|---|---|
| E1 | UX | P1 | Confusion « créé en base » ≠ « reçu e-mail » ≠ « reçu SSE » | `NotificationService.push` / `pushAndMail` ; `MailService` ; SSE after-commit | UI admin/help : trois états distincts. |
| E2 | Config | — | Sans SMTP : e-mails `LOGGED` / non envoyés (comportement démo) | Cutover runtime mail vide | Afficher dans Administration › E-mails. |
| E3 | Bug | P2 | Filtres type / deep-link notif → ressource : à valider bout-en-bout sur tous les kinds | `notifications.html` / stream | Matrice de tests E2E. |

#### Matrice événement → destinataire → canal → résultat

| Événement | Destinataire | In-app DB | SSE | E-mail |
|---|---|---|---|---|
| Changement d’étape pipeline | Candidat (+ HM si assigné) | Oui | Si stream ouvert | Si `pushAndMail` / notifier + SMTP + opt-in |
| Refus motivé | Candidat | Oui | Idem | Idem (message refus) |
| Entretien planifié | Candidat | Oui | Idem | Si prévu + SMTP |
| Candidature retirée | Cibles offre (org) | Oui | Idem | Non (push seul typique) |
| Bienvenue / vérif entreprise | User concerné | Oui | Idem | Souvent mail |
| Alerte SLA / admin | Recruteurs / admin | Oui | Idem | Selon service |
| Nouvelle offre entreprise suivie | Abonnés | Oui | Idem | Selon follow service |

**Règle :** ligne `notifications` ≠ livraison e-mail. Vérité mail = `sent_emails` (`SENT` / `FAILED` / `LOGGED`).

---

### F — Abonnements et paiement

| ID | Type | P | Anomalie | Preuve | Correctif |
|---|---|---|---|---|---|
| F1 | UX | P1 | Plan **gratuit Accès** affiché sur checkout : normal (catalogue audience) mais **ressemble à un vrai paiement** | Live `/billing/plans` : ACCES 0 MAD + PREMIUM 299 ; `checkout.html/js` | Bannière « Paiement simulé — aucun débit réel » ; hiérarchie visuelle Accès (actuel) vs Premium (upgrade). |
| F2 | Config | P1 | `vatConfigured=false`, `tva=0`, TTC = prix catalogue | Live quote OWNER ; `BillingCatalogService` | Ne **pas inventer** de taux. Si facturation MA : configurer `hirelink.billing.vat-rate` **après** validation juridique ; sinon garder « TVA non configurée ». |
| F3 | UX | P1 | Avantages Premium vs Accès : sièges/crédits OK ; wording « Couronne Premium » peu professionnel | Plans API `features` | Reformuler avantages réels uniquement (sièges, crédits IA) — pas de features inventées. |
| F4 | Bug / UX | P1 | Parcours carte → succès simulé (`SUCCESS`) sans disclaimer assez visible | `checkout.js` payments | Disclaimer permanent étapes 1–3 ; libellé « Simulation ». |
| F5 | Bug | P1 | Crédits vs sièges : catalogue crédits / enum `PlanTier.seatLimit` ; admin usage peut afficher **crédits × sièges** alors que l’enforcement IA est **per-user** | `PlanTier` ; `BillingCatalogService` ; `AdminController.usageRow` ; `AiCreditService` | Stocker `seatLimit` au catalogue ; aligner reporting admin et plafond réel ; renommer `premiumUnlimited` → `paidPlan`. |
| F6 | UX | P1 | Claim confidentialité « notice » pour offres d’entreprises suivies vs code **in-app only** (pas d’e-mail) | `confidentialite.html` vs `CompanyFollowService.announceNewOffer` | Soit `pushAndMail`, soit reformuler la notice (in-app seulement). |

**Pourquoi le gratuit est là :** le checkout charge **tous** les plans de l’audience recruteur ; Accès (0 MAD) = formule de base active. Ce n’est pas un bug catalogue — c’est un choix de présentation à clarifier.

---

### G — Confidentialité et export

| ID | Type | P | Anomalie | Preuve | Correctif |
|---|---|---|---|---|---|
| G1 | Bug | **P0** | CGU + confidentialité déclarent `charset=UTF-8` mais fichiers en **Windows-1252** (`UnicodeDecodeError` UTF-8 sur `0xE9`) ; lien « `? Retour` » (flèche perdue) | Python : decode UTF-8 FAIL / CP1252 OK sur `frontend/legal/cgu.html` et `confidentialite.html` | Réenregistrer en UTF-8 ; restaurer `← Retour` ; contrôle encodage CI. |
| G2 | UX / conformité | P1 | Export JSON (`GET …/me/export`) : profil + candidatures (statut, scores) — peu pédagogique | `PrivacyService.exportFor` | Export structuré (sections lisibles) + horodatage ; exclure secrets ; pas de données inter-org. |
| G3 | OK | — | Export candidat limité à **ses** candidatures | `findByCandidateId` | Conserver ; ajouter tests non-régression fuite. |
| G4 | Évolution | P2 | Export recruteur/OWNER incomplet vs CDC (données org) | PrivacyService centré user | Étendre selon rôle **sans** exposer hors périmètre. |

---

## 2. Proposition UX cohérente (cible)

### Candidat
1. **Découvrir** : filtres pays→ville liés, compteurs justes, Externes explicite si vide.
2. **Postuler** : score offre distinct du score CV.
3. **Profil / CV** : score qualité + actions « à améliorer » uniquement si lacune ; synthèse IA claire (coût, loading) ; **sans coach**.
4. **Candidatures / entretiens** : statuts et convocations alignés pipeline.
5. **Compte** : export compréhensible ; légal lisible.

### Recruteur
1. **Pipeline** : Kanban + dossier (essentiel en haut : candidat, offre, scores, délai, actions).
2. **Avancer / Refuser** : conséquences annoncées ; checklist honnête ; historique fonctionnel.
3. **Entretiens** : cohérence avec refus.
4. **Page publique entreprise** : aller-retour sans perdre le contexte.

### OWNER
1. Équipe / sièges / **facturation simulée** clairement labellisée.
2. Checkout : Accès (inclus) vs Premium (payer en démo).

### Administrateur
1. Modération, sources externes (état réel OFF), plans, journaux.
2. Pas d’activation connecteur « pour faire joli ».

---

## 3. Matrice de conformité CDC v6.3 (extrait)

| Exigence CDC v6.3 | Existe | Écart / note |
|---|---|---|
| Comptes, rôles candidat / recruteur / admin, OWNER | Oui | — |
| Profil + CV multi-format ≤ 8 Mo | Oui | Score CV UI à clarifier (A1) |
| Export JSON données perso | Oui | UX export (G2) |
| Découverte offres + filtres + recherche | Oui | Filtres client (B3), places (B2) |
| Offres externes / connecteurs | Partiel | Désactivés volontairement (B1) — hors « faux positifs » |
| Pipeline + refus motivé + historique | Oui | Historique UI cassé (D1) ; refus/entretien (D2) |
| Entretiens + notif ; pas d’accept/refus candidat obligatoire | Oui | Aligné légal §3 |
| Matching local pondéré, sans crédit IA | Oui | — |
| Notifications in-app + temps réel | Oui | E-mail optionnel SMTP |
| SMS / WhatsApp / push | **Non (hors scope CDC explicite « pas »)** | Ne pas inventer |
| Paiement en ligne réel | Non — **simulé** | Conforme démo ; clarifier UI (F1/F4) |
| TVA / factures | Partiel | `vatConfigured=false` tant que non configuré — correct |
| Assistant IA + crédits | Oui | Coach à retirer (A4) |
| Admin plans, motifs, audit | Oui | — |

---

## 4. Plan de correction par lots (après autorisation)

| Lot | Contenu | Indépendance | Tests non-régression | Critères d’acceptation |
|---|---|---|---|---|
| **L1** | D1 historique + D2 refus/entretien + D3 libellés checklist | Pipeline seul | Avancer, refuser, timeline, entretien candidat | Plus d’erreur JS ; refus coupe entretien ; wording honnête |
| **L2** | C1–C2 retour page publique | Nav publique | Depuis équipe & entreprises suivies | Retour vers origine |
| **L3** | A1–A4 score CV + retrait coach | Profil candidat | Synthèse OK ; coach disparu ; crédits | 100/100 sans faux « à corriger » |
| **L4** | B2–B5 places + compteurs filtres | Catalogue | Combinaisons filtres, pagination | Villes ⊆ pays ; totaux cohérents |
| **L5** | F1–F4 checkout simulé + copy plans | Billing | Quote, paiement simulé, PDF | Bannière simulation ; Accès expliqué |
| **L6** | E1/E2 notif + F6 claim follow + G2 export | Compte | Matrice E ; export JSON | Doc + UI états ; pas de double notif HM |
| **L7** | Externes empty-state + **B0** fallback `/external-offers` | Catalogue | Filtre Externes ; simuler échec catalogue | Message config ; fallback fail-closed / même politique exclude |
| **L0** | **G1** réencodage UTF-8 pages légales | Légal | Ouvrir CGU/privacy navigateur | Accents OK ; `← Retour` restauré |

**Ordre suggéré :** **L0** → L1 → L2 → L3 → L4 → L5 → L6 → L7.

### Sources d’audit détaillées (sous-agents)

Compléments spécialisés intégrés ci-dessus : [CV / coach](df4fa60f-7be5-4728-9292-8356c854fcc4) · [filtres / externes](ae4425a0-511e-4552-a70d-e47eac9d72af) · [pipeline / dossier](4a186818-1e6c-457d-be5b-61eb1bde853b) · [notif / billing / légal](1776d8dd-164f-46ba-a1b3-0dc311293881) · [navigation / responsive](96a0fd2e-857e-41a7-b92f-8bf3822d5946).

---

## 5. Risques

| Risque | Domaine | Mitigation |
|---|---|---|
| Activer connecteurs sans droits | Légal / livraison | Interdit sans autorisation + revue sources |
| Inventer taux TVA | Fiscal | Config explicite ou message « non configurée » |
| Hallucinations IA présentées comme faits | Confiance | Badges + garde-fous + conserver revue humaine |
| Casser démo 8081 en rebuild JAR `target/` | Livraison | Toujours démarrer depuis `docs/deploy-prep/demo-runtime/` |
| Fuite données export | Sécurité | Tests rôle + pas d’IDs hors périmètre |
| Lot coach trop large | Scope | Endpoint + UI seulement ; ne pas toucher synthèse |

---

## 6. Preuves live (lecture seule)

| Contrôle | Résultat |
|---|---|
| Health 8081 / FE 5500 | UP / 200 |
| Offres `hirelink` | **123** |
| Catalogue INTERNAL / EXTERNAL | 107 / **0** |
| Places `q=TA&country=MA` | Fuite multi-pays + villes vides |
| Quote OWNER | TTC 299, `vatConfigured=false` |
| `showTimeline` | Appel sans définition |
| Connecteurs | 0 actifs |

---

## 7. Décision demandée

Cet audit **n’applique aucune correction**.

Merci d’autoriser explicitement le ou les lots (ex. « autorise L1+L2 ») avant toute modification du produit.

---

*Fin de l’audit — démo préservée, base intacte, connecteurs non activés.*
