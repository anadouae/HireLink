# Tarification HireLink — Pay-per-Value

Modèle adossé à la **signalling theory** (prix transparent = signal de confiance) et aux études de transparence des coûts de recrutement (réduction du coût d’acquisition / friction).

## Grille

| Plan | Cible | Structure | Signal |
|------|--------|-----------|--------|
| **Talent Actif** | Candidats | **0 MAD** à vie | Liquidité du marché (effet réseau) |
| **Engaged Slot** | Recruteurs PME / startups | **249 MAD** / offre active | Zéro CPC, zéro frais message — paiement de la place |
| **Verified Match** | Recruteurs à la performance | **99 MAD** / entretien mutuel validé | Paiement de l’intention d’échange confirmée |

## Malus de réciprocité (killer feature)

Si le recruteur dépasse le délai promis (ex. 7 jours) :
1. l’offre est **suspendue** ;
2. le crédit **Engaged Slot** est **perdu**.

→ HireLink vend du respect du process, pas seulement de la mise en relation.

## Méthode de calibration (Van Westendorp)

Interroger le panel entreprises (4 questions) :
1. Trop cher ?
2. Trop bon marché (doute qualité) ?
3. Cher mais envisageable ?
4. Excellente affaire ?

Fourchette mid-market typique : ~150–400 € / mois par slot → ajuster 249 MAD selon le marché local.

## Positionnement vs concurrents

- **LinkedIn** : abonnements / packs opaques
- **Indeed** : pay-per-click imprévisible
- **HireLink** : prix affiché, paiement à la valeur

## Produit — règles à brancher (backend)

- [ ] Compteur `active_slots` facturable
- [ ] Job gelé (`published=false` + flag `sla_breached`) si dépassement SLA
- [ ] Facturation `Verified Match` à la transition vers entretien mutuel accepté
- [ ] Affichage prix transparent sur landing + dans l’espace recruteur
