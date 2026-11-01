# Score de compatibilité HireLink

Le score (0 à 100 %) estime l'adéquation entre un profil candidat et une offre. Il sert **uniquement à trier**
(offres côté candidat, dossiers côté recruteur). Il ne refuse, n'avance et ne masque aucun dossier : chaque décision
reste humaine. Le calcul est local (aucune API externe, aucun crédit consommé) et se trouve dans
`backend/.../service/SkillMatchingService.java` (méthode `evaluate`).

## Critères et poids

| Critère | Poids | Mesure (ratio de 0 à 1) |
|---|---|---|
| Compétences obligatoires | 45 | part des compétences demandées (hors langues) présentes dans le profil |
| Compétences appréciées | 10 | même calcul sur le champ « Compétences appréciées » de l'offre |
| Langues | 10 | par langue : niveau ≥ demandé = 1 ; un niveau CECRL en dessous = 0,5 ; niveau non précisé dans le profil = 0,75 ; plus bas ou absente = 0 |
| Expérience | 15 | même niveau = 1 ; un au-dessus = 0,9 ; deux ou plus au-dessus = 0,6 ; un en dessous = 0,5 ; deux ou plus en dessous = 0,1 |
| Type de contrat | 10 | contrat dans les préférences = 1 ; famille proche (Stage/PFE/PFA, « Premier emploi » pour CDI/CDD) = 0,8 ; sinon 0 |
| Lieu et mode de travail | 10 | télétravail : 1 (0,8 si non demandé) ; ville dans les préférences = 1 (0,8 si 100 % sur site non souhaité) ; profil « télétravail uniquement » = 0,3 ; autre ville = 0,2 |

Normalisation : les compétences passent par le référentiel (`skills-catalog.json`) : synonymes (JS = JavaScript,
Spring = Spring Boot), accents, casse et fautes de frappe légères. Niveaux d'expérience du profil : Stagiaire 0, Junior 1,
Confirmé 2, Senior 3, Lead/Manager 4 ; de l'offre : Aucune 0, Débutant 1, Confirmé 2, Senior 3, Expert 4.

## Règles

1. **Renormalisation** : un critère que l'offre ne précise pas (pas de compétences appréciées, pas de langue…) est retiré,
   et les poids restants sont ramenés à 100. L'offre n'est pas pénalisée pour ce qu'elle ne demande pas.
2. **Information absente du profil** (niveau, contrats ou lieux non remplis) : ratio neutre de 0,5. Un profil incomplet
   n'est ni favorisé ni écrasé, et le détail invite à le compléter.
3. **Garde-fou compétences** : si moins de 50 % des compétences obligatoires sont couvertes, le total est plafonné à
   `min(60, couverture % + 20)`. Le lieu ou le contrat ne peuvent pas compenser l'absence du cœur du métier.
4. Score = Σ (poids × ratio) / Σ poids applicables × 100, arrondi au dixième.
5. Le filtre « Offres qui correspondent à mon profil » retient les offres dont le score est ≥ 50 %.
6. Le score affiché est recalculé à chaque lecture sur le profil et l'offre actuels. L'instantané au moment de la
   candidature reste dans `matching_analyses`.

## Exemples réels (données de démo)

**Sara El Amrani × « Développeur Full-Stack Java » (Atlas Digital)**, tous les critères sont applicables (total des poids : 100) :

| Critère | Ratio | Points |
|---|---|---|
| Obligatoires (Java, Spring Boot, MySQL, REST, Git : 5/5) | 1 | 45 |
| Appréciées (React, Docker : 2/2) | 1 | 10 |
| Langues (Anglais B1 demandé, B2 au profil) | 1 | 10 |
| Expérience (Junior pour un poste Confirmé : un niveau en dessous) | 0,5 | 7,5 |
| Contrat (CDI dans les préférences) | 1 | 10 |
| Lieu (Tétouan · Hybride, dans les préférences) | 1 | 10 |
| **Total** | | **92,5 %** |

**Sara × « Consultant Java » (Capgemini Maroc)**. L'offre ne précise ni compétences appréciées ni langue, donc le total des poids applicables est 80 :
obligatoires 2/3 → 45 × 0,667 = 30 ; expérience même niveau → 15 ; CDI → 10 ; Casablanca hors préférences → 10 × 0,2 = 2.
Score = (30 + 15 + 10 + 2) / 80 × 100 = **71,3 %**. L'interface affiche les poids renormalisés (56 %, 19 %, 13 %, 13 %).

**Garde-fou** : Sara × « Data Analyst » ne couvre aucune compétence obligatoire. Les autres critères donneraient 45 %,
mais le score est plafonné à min(60, 0 + 20) = **20 %**.

Sur les 11 candidatures de démo, les scores vont de 19 % à 94 % : les dossiers alignés sont au-dessus de 90 %,
les dossiers hors cible sous 40 %.

## Où le voir

- Candidat : détail d'une offre, bloc « Pourquoi ce score ? » ; Mes candidatures, lien « Voir le détail du score ».
- Recruteur : Pipeline, bouton « Dossier », section Candidature (critères, poids, compétences présentes et manquantes).
- Recruteur, création ou modification d'une offre : champ « Compétences appréciées (facultatif) ».
