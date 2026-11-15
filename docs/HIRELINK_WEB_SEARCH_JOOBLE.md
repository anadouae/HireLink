# Recherche Web HireLink — Jooble Maroc

## Fournisseur retenu

| Critère | Décision |
|--------|----------|
| Fournisseur | **Jooble REST API** — domaine Maroc `https://ma.jooble.org` |
| Pourquoi | API officielle conçue pour publier des résultats d’emploi sur un site partenaire ; déjà intégrée dans HireLink ; couverture MA |
| Coût | **Gratuit** pour le plan de base (documenté : **100 requêtes / jour / clé**, reset minuit UTC). Pas de carte bancaire pour obtenir la clé. Volumes plus élevés : contact Jooble. |
| Clé API | **Obligatoire**. Demande via formulaire : https://ma.jooble.org/api/about |
| Alternatives écartées | Adzuna (couverture MA incertaine + restrictions commerciales 14 j + attribution stricte) ; scraping générique (non autorisé) |

## Ce qu’il faut configurer (avant activation)

1. Remplir le formulaire sur https://ma.jooble.org/api/about (site HireLink, e-mail, etc.).
2. Recevoir la clé par e-mail (vérifier les spams).
3. **Sans redémarrer 8081 tant que non autorisé**, préparer localement :

```properties
hirelink.web-search.enabled=true
hirelink.web-search.provider=jooble
hirelink.web-search.max-results=20
hirelink.external-jobs.jooble.api-key=<VOTRE_CLE>
```

Ou variable d’environnement (recommandé, jamais committer la clé) :

```text
HIRELINK_JOOBLE_API_KEY=<VOTRE_CLE>
```

(`application.properties` lit déjà `${HIRELINK_JOOBLE_API_KEY:}`)

4. Autoriser un cutover / redémarrage du backend pour charger le nouveau JAR + ces propriétés.

## Comportement produit

- Résultats Web **éphémères** : titre, entreprise, lieu, URL Jooble deep-link (`https://ma.jooble.org/desc/...` → annonce d’origine).
- **Jamais** enregistrés dans `job_offers` / `catalog_listings`.
- Fusionnés avec les offres indexées, classés par pertinence (fuzzy + synonymes).
- Clic candidat → ouverture de l’URL exacte fournie par Jooble (nouvel onglet).
- Sans clé / erreur / quota → offres indexées + message de dégradation.

## Tests isolation

`JoobleWebJobDiscoveryIsolationTest` + `CatalogSmartSearchIsolationTest` (mock HTTP / toggle).
Aucun appel Jooble production sans votre clé.
