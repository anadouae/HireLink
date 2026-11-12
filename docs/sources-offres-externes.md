# Offres externes — sources autorisées

État au 10 novembre 2026 : **Remote OK est la seule source branchée**. Les offres vivent dans `external_job_offers`, séparées de `job_offers`. L'import est désactivé par défaut (`hirelink.external-jobs.remoteok.enabled=false`) : aucune requête ne part tant que ce réglage n'est pas vrai. Le bouton mène au lien d'origine et une candidature HireLink n'est pas créée.

Une source n’est utilisable que si son contrat autorise l’affichage, impose la citation, et donne un lien pour postuler chez l’émetteur. Les offres ne doivent pas être inventées, ni transformées en candidatures HireLink.

## Sources consultées

| Source | Accès | Ce que le contrat permet | Blocage pour HireLink |
| --- | --- | --- | --- |
| France Travail, API Offres d’emploi | Restreint. La fiche data.gouv.fr indique que l’API n’est plus exposée sur francetravail.io ; une demande passe par le formulaire de contact. | Recherche paginée et détail d’offres dont les partenaires ont consenti à la diffusion. Les réutilisations observées (licence citée par des agrégateurs) exigent le texte intégral, la date de mise à jour, le lien d’origine, une interrogation au moins quotidienne, et l’interdiction de vendre les offres aux chercheurs. | Pas de clé. Pas de couverture Maroc. Demande d’accès non déposée. |
| Adzuna | Clé gratuite (`app_id`, `app_key`). | Recherche d’annonces et estimations de salaire, avec logo « Adzuna » lié vers adzuna.co.uk. L’usage par une organisation est décrit comme un essai de 14 jours ; au-delà, un accord écrit est exigé. | Pas de clé. Essai commercial trop court pour une démo durable. |
| Remote OK | Sans clé. JSON `https://remoteok.com/api`, RSS `https://remoteok.com/remote-jobs.rss`. | L’aide publique autorise un agrégateur si Remote OK est cité et si chaque offre renvoie vers l’annonce d’origine. | Offres remote internationales, pas le marché marocain de la démo. Conditions à relire sur le site avant tout appel. |
| Remotive, Himalayas, Jobicy, The Muse, USAJobs | Documentés comme API ou flux publics, avec attribution et des plafonds. | Affichage possible seulement dans les limites publiées par chaque éditeur. | Aucune clé demandée. Aucun de ces flux n’est le référentiel des entreprises déjà présentes dans HireLink. |

Les sites carrière d’entreprises (Greenhouse, Lever, pages HTML) ne sont pas une source autorisée tant que l’entreprise n’a pas donné un flux ou une API. Les lire automatiquement serait du scraping.

## Remote OK, source retenue

Le flux public `https://remoteok.com/api` ne demande pas de clé. Son premier objet et la page d'aide demandent de citer Remote OK et de lier chaque annonce d'origine, sans `nofollow`. Le logo n'est pas utilisé. Les conditions générales du 26 octobre 2026 demandent aussi un lien vers le site là où les données sont affichées. HireLink montre « Source : Remote OK » vers `https://remoteok.com` et le bouton « Postuler sur le site d'origine ».

Ce flux couvre des emplois à distance internationaux, pas le marché marocain. France Travail et Adzuna restent inutilisables ici : pas de clé, pas d'accord. Aucune offre n'est ajoutée à la main.

## Ce qui est en place

1. Table `external_job_offers`, distincte de `job_offers`.
2. `RemoteOkParser`, `RemoteOkClient` et `ExternalJobImportService`. Un flux vide, illisible ou en erreur réseau ne retire pas les offres déjà acceptées. Une annonce absente d'un flux valide reste visible sept jours. Un second appel pendant qu'une synchronisation tient le verrou est refusé, sans retirer d'annonces. Le HTML et les scripts de la source sont retirés avant l'enregistrement.
3. Liste publique `GET /api/v1/external-offers`. La synchronisation `POST /api/v1/external-offers/sync` est réservée à l'administrateur et ne part que si l'import est activé. L'écran administrateur est `GET /api/v1/admin/external-offers/status` et le masquage `POST /api/v1/admin/external-offers/{id}/hide`.
4. Badge « Offre externe », lien « Source : Remote OK » et bouton « Postuler sur le site d'origine ». L'offre HireLink affiche « Postuler sur HireLink ». Le détail externe indique que HireLink ne gère pas cette candidature. Pas de dossier HireLink. La correspondance n'est calculée que si l'annonce publie des compétences et si le candidat en a confirmé.

## Règles

- RG-EXT-01 — Une offre externe a une source identifiable.
- RG-EXT-02 — Elle n'appartient pas à une organisation HireLink.
- RG-EXT-03 — La candidature se fait sur le site d'origine.
- RG-EXT-04 — Aucune candidature HireLink n'est créée.
- RG-EXT-05 — Le pipeline ne concerne que les candidatures internes.
- RG-EXT-06 — L'entreprise annoncée n'est pas marquée vérifiée.
- RG-EXT-07 — Seule une source autorisée est branchée. Remote OK l'est. France Travail, Adzuna, LinkedIn, Indeed et les sites d'entreprises, dont OCP, ne le sont pas.
- RG-EXT-08 — Un flux vide, illisible ou en erreur ne retire rien. Une annonce absente d'un flux valide reste visible sept jours, parce que l'API ne renvoie qu'une fenêtre récente.
- RG-EXT-09 — Le chiffre affiché, lorsqu'il existe, ne reprend que les compétences citées. Ce n'est pas le score à six critères, et l'entreprise externe ne le valide pas.
- RG-EXT-10 — HireLink ne sait pas si le candidat a réellement postulé dehors.

L'identifiant public est `ext-` suivi du numéro interne, pour qu'il ne se confonde pas avec une offre HireLink. L'API de candidature refuse cet identifiant.

## Ce qui reste bloqué

- Aucune source marocaine officielle n'est disponible.
- France Travail et Adzuna ne sont pas connectés.
- L'import Remote OK reste éteint sur la démonstration tant que `hirelink.external-jobs.remoteok.enabled` n'est pas passé à `true`.
