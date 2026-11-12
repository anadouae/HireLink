# Diagrammes HireLink v5.1 (7 novembre 2026)

Vingt et un fichiers livrables — dix sources `.puml`, dix PNG et l'export BPMN 2.0 —, tous
**alignés sur le code réellement présent dans le dépôt**
(25 entités JPA et 11 énumérations, 26 tables, 21 contrôleurs et 154 endpoints, 52 services,
25 repositories Spring Data, 27 pages front).

| Fichier | Type | Ce qu'il montre |
|---|---|---|
| `01_cas_utilisation.png` | UML cas d'utilisation | **une seule vue pour tous les acteurs** : les 6 acteurs (visiteur, candidat, recruteur, responsable de poste, administrateur, système de planification) et les 46 cas, regroupés dans cinq espaces |
| `02_classes.png` | UML classes | les 25 entités, leurs champs réels et 34 associations nommées avec leurs cardinalités JPA |
| `03_sequence_candidature.png` | UML séquence | `POST /applications` : filtre JWT, anti-doublon 409, score, notification |
| `11_sequence_offre_externe.puml` | UML séquence | source autorisée, consultation, redirection. PNG `11_sequence_offre_externe.png` généré depuis ce fichier. |
| `04_sequence_entretien.png` | UML séquence | planification, réponse du candidat, rappel J-1, export `.ics` |
| `05_sequence_authentification.png` | UML séquence | mot de passe, CAPTCHA après 2 échecs, `POST /auth/mfa/verify`, Google, passkey, sessions |
| `06_activite_pipeline.png` | UML activité | `applications.status` : SUBMITTED → SCREENING → SHORTLISTED → INTERVIEW, REJECTED, WITHDRAWN |
| `07_paquets.png` | UML paquets | frontend → controllers → services → repositories → modèle, plus les sorties réseau |
| `08_deploiement.png` | UML déploiement | navigateur :5500, JVM :8081, MariaDB :3306, disque `backend/data`, API LLM |
| `09_bpmn_processus_recrutement.png` | BPMN (3 couloirs) | le processus métier complet, du clic du candidat à la décision |
| `processus_recrutement_hirelink.bpmn` | BPMN 2.0 XML | le même processus, importable dans Camunda Modeler ou Bizagi |
| `10_stack_technologique.png` | couches techniques | présentation, service, données, capacités externes, outillage |

Chaque `.puml` est la source éditable du PNG correspondant.

Le diagramme de cas d'utilisation est **unique** : il contient les six acteurs et les
quarante-six cas. Les anciennes versions par rôle (`01a_cas_utilisation_candidat`,
`01b_cas_utilisation_recruteur`, `01c_cas_utilisation_admin`) ne sont plus générées ni
citées nulle part ; elles sont conservées dans `_details-par-role/` comme annexe de
travail, pas comme livrable.

## Régénérer

```bash
python docs/diagrammes/_gen_v5_plantuml.py            # les 10 sources -> PNG (serveur public)
python docs/diagrammes/_gen_v5_plantuml.py --svg      # même chose en vectoriel
python docs/diagrammes/_gen_v5_plantuml.py --only 04  # un seul diagramme
python docs/diagrammes/_gen_v5_plantuml.py --local --jar C:/chemin/plantuml.jar   # sans serveur
python docs/diagrammes/_gen_v5_bpmn_xml.py            # l'export BPMN 2.0
```

Le rendu passe par le serveur public `https://www.plantuml.com/plantuml`. Le script essaie
un GET, puis un POST, puis attend et recommence, parce que ce serveur renvoie parfois une
page HTML de consentement anti-robot ou un `HTTP 400` sur une source trop sollicitée.
Quand il est bouché, `--local` (Java 17 est installé sur ce poste, le jar se télécharge sur
Maven Central, ~22 Mo) rend exactement les mêmes sources, sans réseau et sans rendu muet.

## Quatre pièges relevés pendant la génération

- Un libellé `usecase "…" as ID` contenant `\n` fait répondre `HTTP 400` au serveur public ;
  la forme `usecase ID as "…"` passe, et elle accepte même une vraie fin de ligne dans son
  libellé. Un libellé `actor "…"` en revanche refuse la fin de ligne réelle : dans le source
  du diagramme unique, les étiquettes d'acteurs écrivent `\\n` (backslash-backslash-n).
- Le mot-clé `cloud` n'est pas admis dans un diagramme de séquence ; les participants
  externes (Google OAuth, agenda du candidat) sont déclarés comme `participant`.
- Le serveur public renvoie parfois, **sans code d'erreur**, un PNG où toutes les étiquettes
  ont disparu : le même `.puml` rend alors 21 000 octets au lieu de 74 000. Le script rend
  donc chaque diagramme trois fois et garde la plus grande image (une image sans texte est
  toujours plus petite), et signale par `ATTENTION` tout rendu sous 20 000 octets. Après une
  régénération, comparer les tailles au tableau ci-dessus : un écart d'un facteur trois
  signifie un rendu muet, pas un changement de contenu.
- Dans un diagramme de cas d'utilisation, un lien cache **avec direction**
  (`A -[hidden]right-> B`) est ignoré ; la forme sans direction (`A -[hidden]-> B`) place
  bien `B` dans la colonne suivante. C'est ce qui fait tenir la vue unique : sans ces cinq
  liens, les quarante-six cas s'empilent en une seule colonne de 1156 × 4096 px, soit 63 cm
  de haut à la largeur d'une page — illisible. Avec, l'image mesure 2097 × 3057 px et tient
  sur une page.

## Dimensions de référence (16,6 cm de large = une page)

| PNG | pixels | haut à 16,6 cm |
|---|---|---|
| `01_cas_utilisation` | 2097 × 3057 | 24,2 cm |
| `02_classes` | 4096 × 2421 | 9,8 cm |
| `03_sequence_candidature` | 1869 × 896 | 8,0 cm |
| `04_sequence_entretien` | 1508 × 1203 | 13,2 cm |
| `05_sequence_authentification` | 1729 × 951 | 9,1 cm |
| `06_activite_pipeline` | 1618 × 1009 | 10,4 cm |
| `07_paquets` | 2883 × 1178 | 6,8 cm |
| `08_deploiement` | 2048 × 662 | 5,4 cm |
| `09_bpmn_processus_recrutement` | 1954 × 1190 | 10,1 cm |
| `10_stack_technologique` | 2264 × 1289 | 9,5 cm |

## Import dans un autre outil

- **PlantUML en local** : coller le `.puml` sur <https://www.plantuml.com/plantuml/uml>.
- **Lucidchart / draw.io** : importer le PNG.
- **Camunda Modeler / Bizagi** : ouvrir `processus_recrutement_hirelink.bpmn`.
