# Modèle économique HireLink (état implémenté)

Document pédagogique pour la soutenance. **Aucun changement de tarifs** dans cette vague.

## Qui paie ?

| Acteur | Formule | Prix catalogue (TTC MAD) | Ce que cela change vraiment |
|--------|---------|--------------------------|-----------------------------|
| Candidat | Essentiel | 0 | Crédits IA de base |
| Candidat | Performance | 39 / mois · 390 / an | Plus de crédits IA |
| Recruteur (OWNER) | Accès | 0 | Sièges d’équipe limités + crédits IA |
| Recruteur (OWNER) | Premium | 299 / mois · 2990 / an | Plus de sièges + plus de crédits IA |

Seul le **créateur de l’espace** (OWNER) règle la formule recruteur. Les sièges comptent les membres actifs.

## Ce qui n’est pas du revenu HireLink

- La **TVA** (si configurée) est une taxe collectée, pas une marge.
- Par défaut `hirelink.billing.vat-rate` est vide ? HT = TTC, mention « TVA non configurée ».
- Le paiement est **simulé** : aucun débit bancaire réel.

## Mécanismes absents (non inventés)

Classement payant, offres sponsorisées, commission à l’embauche : non implémentés. Le matching ne lit pas `plan_tier`.

## Plateformes comparables (repères)

Indeed / LinkedIn Recruiter : abonnements SaaS + options de mise en avant. HireLink se limite aujourd’hui à l’abonnement + crédits d’usage IA.
