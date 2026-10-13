# Diagramme de classes FINAL — import Lucid / autres

## Fichiers générés
| Fichier | Usage |
|---------|--------|
| `diagramme_classes_hirelink_FINAL.png` | Image haute qualité (soutenance / Word) |
| `diagramme_classes_hirelink_FINAL.puml` | Source PlantUML (éditable) |
| `diagramme_classes_hirelink_FINAL.mmd` | Mermaid (GitHub / Notion / certains imports) |

## Lucidchart
1. Ouvre [Lucidchart](https://lucid.app) → **Import**
2. Options :
   - **Importer l’image** `diagramme_classes_hirelink_FINAL.png` (le plus simple)
   - Ou coller le contenu `.puml` via une intégration PlantUML / shape library
   - Ou coller le Mermaid `.mmd` si ton espace Lucid / outil le supporte

## Alternatives gratuites
- **PlantUML online** : https://www.plantuml.com/plantuml/uml → coller le `.puml`
- **draw.io** : File → Import → PNG ou éditer manuellement depuis le PNG
- Régénérer localement : `python docs/diagrammes/_gen_classes_final.py`

## Contenu aligné code
Inclut notamment : `verificationStatus`, `OfferReport`, `JobOffer.suspended`, SLA sur `Application`, scorecard, matching, IA logs.
