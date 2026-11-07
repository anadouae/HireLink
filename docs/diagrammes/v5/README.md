# Diagrammes HireLink v5 (6 novembre 2026)

Dix-neuf fichiers, tous **alignés sur le code réellement présent dans le dépôt**
(24 entités JPA, 20 contrôleurs, 51 services, 24 repositories, 25 tables, 27 pages front).

| Fichier | Type | Ce qu'il montre |
|---|---|---|
| `01a_cas_utilisation_candidat.png` | UML cas d'utilisation | les 15 cas du candidat, avec `include` vers l'OCR et le profil |
| `01b_cas_utilisation_recruteur.png` | UML cas d'utilisation | les 13 cas du recruteur + l'acteur « système de planification » |
| `01c_cas_utilisation_admin.png` | UML cas d'utilisation | les 10 cas de l'administrateur |
| `02_classes.png` | UML classes | les 24 entités, leurs champs réels et les associations JPA |
| `03_sequence_candidature.png` | UML séquence | `POST /applications` : filtre JWT, anti-doublon 409, score, notification |
| `04_sequence_entretien.png` | UML séquence | planification, réponse du candidat, rappel J-1, export `.ics` |
| `05_sequence_authentification.png` | UML séquence | mot de passe, CAPTCHA après 2 échecs, MFA, Google, sessions |
| `06_activite_pipeline.png` | UML activité | `applications.status` : SUBMITTED → SCREENING → SHORTLISTED → INTERVIEW, REJECTED, WITHDRAWN |
| `07_paquets.png` | UML paquets | frontend → controllers → services → repositories → modèle, plus les sorties réseau |
| `08_deploiement.png` | UML déploiement | navigateur :5500, JVM :8081, MariaDB :3306, disque `backend/data`, API LLM |
| `09_bpmn_processus_recrutement.png` | BPMN (3 couloirs) | le processus métier complet, du clic du candidat à la décision |
| `processus_recrutement_hirelink.bpmn` | BPMN 2.0 XML | le même processus, importable dans Camunda Modeler ou Bizagi |
| `10_stack_technologique.png` | couches techniques | présentation, service, données, capacités externes, outillage |

Chaque `.puml` est la source éditable du PNG correspondant.

## Régénérer

```bash
python docs/diagrammes/_gen_v5_plantuml.py            # les 12 sources -> PNG
python docs/diagrammes/_gen_v5_plantuml.py --svg      # même chose en vectoriel
python docs/diagrammes/_gen_v5_plantuml.py --only 04  # un seul diagramme
python docs/diagrammes/_gen_v5_bpmn_xml.py            # l'export BPMN 2.0
```

Le rendu passe par le serveur public `https://www.plantuml.com/plantuml` : ni Java ni
Graphviz n'est installé sur ce poste. Le script essaie un GET, puis un POST, puis attend
et recommence, parce que ce serveur renvoie parfois une page HTML de consentement
anti-robot ou un `HTTP 400` sur une source trop sollicitée.

## Deux pièges relevés pendant la génération

- Un libellé `usecase "…" as ID` contenant `\n` fait répondre `HTTP 400` au serveur public ;
  la forme `usecase ID as "…"` passe. C'est pourquoi les trois diagrammes de cas d'utilisation
  utilisent la forme courte.
- Le mot-clé `cloud` n'est pas admis dans un diagramme de séquence ; les participants
  externes (Google OAuth, agenda du candidat) sont déclarés comme `participant`.

## Import dans un autre outil

- **PlantUML en local** : coller le `.puml` sur <https://www.plantuml.com/plantuml/uml>.
- **Lucidchart / draw.io** : importer le PNG.
- **Camunda Modeler / Bizagi** : ouvrir `processus_recrutement_hirelink.bpmn`.
