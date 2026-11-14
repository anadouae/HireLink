# Checkout 3 étapes — rapport d’implémentation

**Date :** 2026-11-12  
**Référence visuelle :** maquette jointe (Abonnement ? Paiement ? Confirmation)  
**Commit / push :** aucun (demande explicite)

---

## Fichiers modifiés (projet Desktop)

| Fichier | Rôle |
|---------|------|
| `frontend/checkout.html` | Structure des 3 étapes + carte démo + confirmation |
| `frontend/css/checkout.css` | Design fidèle (bordeaux, cartes, progress, mobile) |
| `frontend/js/checkout.js` | Logique wizard + catalogue réel + paiement simulé |

**Backend :** non modifié (réutilise `GET /billing/plans`, `GET /billing/quote`, `POST /billing/subscribe`, `POST /billing/payments`, `GET /billing/invoices/{id}/pdf`).

Copies de test isolé (non publiées) : mêmes 3 fichiers sous `C:\HireLink-Recette-Professeur\HireLink\frontend\…`

---

## Adaptation catalogue (pas de copie aveugle de la maquette)

| Audience | Cartes affichées | Plan payable |
|----------|------------------|--------------|
| Recruteur | **Accès** (0) + **Premium** (299 / 2990 MAD) | Premium |
| Candidat | **Essentiel** (0) + **Performance** (39 / 390 MAD) | Performance |

- Avantages et prix : `GET /billing/plans` + devis `GET /billing/quote`
- Remise annuelle affichée : calculée (`monthly*12` vs `yearly`) ? **-17 %** pour Premium
- TVA : « non configurée » + « Total affiché (simulation) » si `vatConfigured=false`

---

## Sécurité paiement

- Un seul moyen : **carte de démonstration** (virement / wallet / solde retirés)
- Champs PAN / exp / CVV / titulaire : **readonly**, valeurs fictives, **jamais** dans le body API
- Body envoyé : `{ invoiceId, scenario, idempotencyKey }` uniquement
- Double clic : flag `paying` + bouton désactivé + idempotence serveur
- Activation uniquement après `status=SUCCEEDED` backend
- PDF : bouton visible seulement si facture + endpoint PDF OK (téléchargement réel)

---

## Tests Playwright (API **8095** / base `hirelink_prof_recette`)

Captures : `C:\HireLink-Recette-Professeur\HireLink\docs\recette-installation-professeur\captures-checkout\`

| ID | Scénario | Résultat |
|----|----------|----------|
| T01 | Premium mensuel 299 | PASS |
| T02 | Premium annuel 2990 + (-17 %) | PASS |
| T03 | Formule Accès ? CTA désactivé | PASS |
| T04 | Étape 2 carte seule, pas de virement | PASS |
| T05 | Retour étape 2 ? 1 | PASS |
| T06 | Paiement simulé refusé | PASS |
| T07 | Paiement simulé réussi + référence | PASS |
| T08 | Bouton PDF visible | PASS |
| T09 | Aucune fuite PAN/CVV dans POST | PASS |
| T10 | Refresh restaure confirmation | PASS |
| T11 | Erreur API (token invalide) | PASS |
| T12 | Mobile 390×844 sans scroll H | PASS |

**12 PASS / 0 FAIL**

---

## Isolation environnement principal

| Contrôle | Résultat |
|----------|----------|
| API 8081 | UP |
| Front 5500 | HTTP 200 |
| Base `hirelink` | 126 / 118 / 220 / 0 (inchangé) |
| Commit / push | aucun |

Les activations d’abonnement de test ont porté uniquement sur `hirelink_prof_recette` via le port 8095.

---

## Écarts volontaires vs maquette

1. Textes d’avantages = catalogue HireLink (sièges / IA), pas les libellés marketing de l’image.  
2. Deuxième carte recruteur = **Accès** (pas « Performance », réservé candidat).  
3. Libellé bouton = « Confirmer le paiement **simulé** » (clarté pédagogique).  
4. Sélecteur « Scénario de test » (succès / refus) conservé pour la démo ENSA.

---

## Suite éventuelle (après ton OK)

- Revue visuelle fine desktop/mobile
- Commit local uniquement quand tu l’autoriseras
- **Pas de push** tant que non demandé
