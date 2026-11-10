# Offres externes — sources autorisées

État au 10 novembre 2026 : **aucune intégration n’est branchée**. HireLink n’affiche que les offres publiées dans `job_offers`. Il n’y a pas de champ d’URL source, pas d’importateur, et aucun scraping de sites carrière.

Une source n’est utilisable que si son contrat autorise l’affichage, impose la citation, et donne un lien pour postuler chez l’émetteur. Les offres ne doivent pas être inventées, ni transformées en candidatures HireLink.

## Sources consultées

| Source | Accès | Ce que le contrat permet | Blocage pour HireLink |
| --- | --- | --- | --- |
| France Travail, API Offres d’emploi | Restreint. La fiche data.gouv.fr indique que l’API n’est plus exposée sur francetravail.io ; une demande passe par le formulaire de contact. | Recherche paginée et détail d’offres dont les partenaires ont consenti à la diffusion. Les réutilisations observées (licence citée par des agrégateurs) exigent le texte intégral, la date de mise à jour, le lien d’origine, une interrogation au moins quotidienne, et l’interdiction de vendre les offres aux chercheurs. | Pas de clé. Pas de couverture Maroc. Demande d’accès non déposée. |
| Adzuna | Clé gratuite (`app_id`, `app_key`). | Recherche d’annonces et estimations de salaire, avec logo « Adzuna » lié vers adzuna.co.uk. L’usage par une organisation est décrit comme un essai de 14 jours ; au-delà, un accord écrit est exigé. | Pas de clé. Essai commercial trop court pour une démo durable. |
| Remote OK | Sans clé. JSON `https://remoteok.com/api`, RSS `https://remoteok.com/remote-jobs.rss`. | L’aide publique autorise un agrégateur si Remote OK est cité et si chaque offre renvoie vers l’annonce d’origine. | Offres remote internationales, pas le marché marocain de la démo. Conditions à relire sur le site avant tout appel. |
| Remotive, Himalayas, Jobicy, The Muse, USAJobs | Documentés comme API ou flux publics, avec attribution et des plafonds. | Affichage possible seulement dans les limites publiées par chaque éditeur. | Aucune clé demandée. Aucun de ces flux n’est le référentiel des entreprises déjà présentes dans HireLink. |

Les sites carrière d’entreprises (Greenhouse, Lever, pages HTML) ne sont pas une source autorisée tant que l’entreprise n’a pas donné un flux ou une API. Les lire automatiquement serait du scraping.

## Architecture proposée, non réalisée

1. Table séparée `external_job_offers` : source, identifiant externe, URL d’origine, titre, employeur, lieu, contrat, compétences seulement si la source les fournit, date de fraîcheur, date de retrait. Pas de mélange avec `job_offers`.
2. Service serveur `ExternalJobImportService`, clé uniquement dans la configuration locale, jamais dans le navigateur. Mise à jour planifiée, suppression des offres absentes du flux.
3. Affichage public avec badge de la source, citation exigée par le contrat, et bouton « Postuler sur le site de l’entreprise » vers l’URL d’origine. Pas de dossier HireLink, pas de score si les compétences ne sont pas fournies par la source.
4. Aucune offre n’est créée à la main pour « remplir » le flux.

## Ce qui reste bloqué

- Aucune clé ni accord écrit n’est en place.
- Aucune source marocaine officielle équivalente n’a été identifiée.
- Le code d’import, la table et l’écran ne sont pas écrits.
