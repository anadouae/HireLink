# Anti-fraude employeurs — ce que fait le marché, ce que fait HireLink

## Comment LinkedIn, Indeed, Workable, Greenhouse… évitent les fausses offres

| Acteur | Levier principal |
|--------|------------------|
| **Indeed / Glassdoor** | Carte bancaire + téléphone + parfois SIRET/tax ID ; comptes gelés si signalements |
| **LinkedIn** | Page entreprise + **email de domaine** (`@entreprise.com`, pas Gmail) + vérif Page |
| **France (Apec, etc.)** | **SIRET** officiel (équivalent ICE) + cohérence raison sociale |
| **Workable / Greenhouse / Lever** | Vente B2B + contrat + facturation société ; peu de self-serve anonyme |
| **SmartRecruiters** | Compte entreprise, headcount, process commercial |

**Principe commun :** un numéro seul ne suffit jamais. On croise **identité légale + preuve de contrôle** (paiement, domaine email, contrat, pièce RC).

---

## Ce que HireLink empile (anti faux ICE / fausse boîte)

1. **Format** de l’identifiant (9–15 chiffres)  
2. **Unicité** — 1 ICE = 1 organisation sur la plateforme (sinon `CONFLICT`)  
3. **Raison sociale obligatoire** rapprochée du registre (OMPIC mock → API réelle)  
4. **Email professionnel** — les domaines grand public (gmail, yahoo…) ne donnent **pas** le badge vérifié automatique  
5. **PENDING** tant que non confirmé → **aucune offre publiée**  
6. **Validation admin** manuelle (comme la revue mid-market)  
7. *(Roadmap)* pièce RC/ICE, OTP SMS dirigeant, IBAN société, signalement candidats

### Pourquoi copier l’ICE d’un voisin échoue

- ICE déjà pris → refus  
- Nom qui ne matche pas le registre → pas de vérif, pas de publish  
- Email `@gmail.com` → reste en revue  
- Même si le compte existe → **mur de publication**

Démo registre : ICE `001544289000035` + raison sociale **Atlas Digital**.

---

## Tarification alignée marché ATS (HireLink)

Modèle type **Workable / JazzHR** adapté MAD — candidats gratuits, entreprises paient au volume :

| Plan | Prix | Logique |
|------|------|---------|
| Essai | 14 jours | Time-box, pas “tout gratuit forever” |
| Pro | 349 MAD/mois | Petites équipes, plafond d’offres actives |
| Business | 899 MAD/mois | Plus de sièges, volume, support |
| Entreprise | Sur devis | Headcount / contrat annuel (Greenhouse style) |

Les anciens paliers “0 MAD = pipeline complet” + “299 = presque tout” n’étaient pas progressifs : corrigés.
