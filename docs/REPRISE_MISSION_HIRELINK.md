# Reprise de mission — HireLink

Document de passation. Aucun mot de passe, jeton ou secret n’y figure.
Dernière mise à jour : 9 novembre 2026, après la mission finale (facturation).

## État au 9 novembre 2026, fin de mission finale

- Branche de travail : `finalisation/livraison`. Point de restauration local : `restauration/avant-mission-finale` au commit `9a49171`. Aucun push.
- La démonstration sur les ports 8081 et 5500 n’a pas été redémarrée. Son binaire Java date d’avant ces changements. Ne pas le redémarrer pour « appliquer » le schéma : Hibernate `ddl-auto=update` modifierait alors la base `hirelink`.
- Base de démonstration `hirelink` : 118 offres, à préserver. Clé `backend/data/jwt.key` : ne pas la régénérer ni la lire dans un rapport.
- Facturation : statuts `NON_PAYEE`, `PAYEE`, `ANNULEE`. Un avoir historique reste lisible, sans création nouvelle. Pas de TVA inventée (`hirelink.billing.vat-rate` vide). Adresse et ICE de l’émetteur non renseignés.
- Le paiement est simulé (`MockPaymentProvider`). Aucun essai gratuit. Les formules gratuites permanentes Accès et Essentiel restent. Une formule payante n’est activée qu’après une transaction réussie.
- La résiliation coupe l’accès à la fin de la période déjà réglée. Si aucune date de fin n’est enregistrée, l’accès reste.
- Motifs de refus figés : REF01 à REF06. Le catalogue administrateur n’est plus modifiable.
- Les e-mails sans SMTP sont `LOGGED` (en attente), pas envoyés.
- Le cahier des charges v6.1 est toujours absent. Les listes administrateur d’entreprises et d’utilisateurs restent filtrées côté navigateur. Le journal d’audit est paginé.
- Les sections plus bas décrivent des états antérieurs. En cas de contradiction, cette section prévaut.

## Objectif global

Rendre HireLink complet, sécurisé, performant, conforme au cahier des charges, et installable depuis un clone Git propre. Le professeur clone le dépôt, configure une base neuve, lance backend et frontend, et teste avec les comptes de démonstration déjà documentés.

Le dépôt contient déjà une application fonctionnelle. Ne pas repartir de zéro, ne pas écraser les modifications non commitées.

Le cahier des charges présent dans le dépôt est la **v5** (`docs/cahier-des-charges/`). Aucun fichier v6.1 n’a été trouvé dans le dépôt ni sur le Bureau. Ne pas inventer d’exigences v6.1 absentes du dépôt.

## État Git

- Racine : `C:\Users\daurinia\Desktop\HireLink`
- Branche : `main`, alignée sur `origin/main` (`83b5bcf`, remote `https://github.com/anadouae/HireLink.git`)
- **Tout le travail de finalisation est non commité.** `git status` montre des dizaines de fichiers modifiés et des fichiers nouveaux (tests d’intégration, migration A1, scripts de lancement). Ne pas `reset`, `clean`, ni checkout destructeur.
- Ne pas pousser sur GitHub sans autorisation explicite. Ne pas réécrire l’historique.

## Environnement constaté le 9 novembre 2026

- Backend de démonstration **déjà lancé** : Java pid sur le port **8081**, démarré à 10 h 09 avec `mvn spring-boot:run` et `-Dhirelink.otp.log-to-console=false`. Ce processus **ne contient pas** les modifications Java postérieures à 10 h 09 (isolation A1, notifications, OTP, factures, etc., écrites entre 11 h 17 et 12 h 49).
- Frontend statique : Python sur **http://localhost:5500** (pid 28108). Il sert les fichiers du disque, donc le JavaScript modifié est bien celui qui s’affiche.
- MariaDB/MySQL écoute sur **3306**. La base de démo s’appelle `hirelink`. Les tests doivent utiliser `hirelink_test` (`backend/src/test/resources/application.properties`, `ddl-auto=create-drop`, port HTTP aléatoire).

## Corrections déjà dans l’arbre de travail (non commitées)

Vérifiées par lecture du code, pas encore toutes rejouées par la campagne de tests de cette reprise.

- **A1** — `organization_id` gravé à la création de l’offre (`JobOfferService.create`), droits via l’appartenance active (`OrganizationService.canManageOffer` / `assertSameOrg`), migration idempotente `OfferOwnershipMigration` + `JobOfferRepository.backfillMissingOrganization`. Test HTTP : `OfferIsolationIntegrationTest`.
- **A2** — consulter un espace ne crée pas d’organisation (`OrganizationService`, commentaire A2).
- **A6** — champs de modération masqués hors gestionnaire (`JobOfferService.toDto`).
- **B2** — réservation de crédit avant l’appel IA (`AiCreditService`, `AiAssistController`).
- **B3** — numérotation de facture par entreprise et par mois, verrou de ligne (`BillingCatalogService.nextNumber`).
- **B6** — compteur OTP atomique (`EmailOtpRepository`) et verrou de sièges à l’invitation.
- **C4** — OTP non journalisé par défaut (`hirelink.otp.log-to-console=false`). Test : `MailServiceTest`.
- **C7** — cookie de rafraîchissement HttpOnly + SameSite=Strict. Test : `RefreshCookiesTest`.
- **D1 / D2** — les listes d’offres et `findAllWithRecruiter` (administration) chargent recruteur et organisation dans la même requête. Le compteur d’offres suspendues est `countBySuspendedTrue`. Le gain n’est pas mesuré en nombre de requêtes SQL. `GET /admin/offers` sur l’instance isolée a renvoyé 53 lignes.
- **Entretiens L1** — convocation sans réponse candidat. Tests unitaires présents (`InterviewServiceTest`, `EntretienConvocationTest`, `InterviewReminderServiceTest`, etc.).
- **Notifications** — `kindSel` est déclaré avant usage (`frontend/js/notifications.js` ligne 12). Filtres serveur `kind` / `since` dans `NotificationController` / `NotificationService`. Test : `NotificationFilterIntegrationTest`.

## Vérification navigateur de cette reprise (frontend réel, API 8081 = binaire de 10 h 09)

Compte recruteur de démo Atlas Digital (Salma). Aucune donnée métier modifiée : consultation et filtres seulement.

| Page | Résultat |
|---|---|
| Tableau de bord | « Bonjour, Salma », 11 candidatures à trier, 1 entretien aujourd’hui, 17 réponses à risque. Pas d’erreur visible. |
| Pipeline | 24 dossiers actifs, colonnes Reçues 11, Présélection 5, Shortlist 1, Entretien 7. |
| Entretiens | À venir 2, À replanifier 1, Passés 6. Filtre « Sur site » : URL `?type=ONSITE`, il ne reste que Youssef Idrissi. |
| Agenda | Semaine du 9 au 15 novembre 2026. Prochain : Meryem Chakir lun. 9 14:30. Bloc 14:30–15:15 visible le lundi. |
| Notifications | Page chargée (le bug `kindSel` ne coupe plus le script). Toutes 14, Non lues 2. Filtre « Entretien » : état vide. Retour à « Tous les types » : les 14 lignes reviennent. |

Le décompte « 49 notifications » du journal précédent ne correspond pas à ce compte (14 lignes). Ce n’est pas une régression du chargement de page.

Le backend de 10 h 09 **n’applique pas** les correctifs Java de l’après-midi. La vérification navigateur prouve le frontend, pas l’isolation A1 en production locale.

## Tests

`mvn test` depuis `backend/`, JDK 23, base `hirelink_test` uniquement (create-drop). La base `hirelink` n’a pas été touchée.

- 9 novembre 2026, 13 h 42–13 h 45 UTC : **109 tests, 0 échec, 0 erreur, BUILD SUCCESS**. Cette campagne a compilé le code d’avant le `JOIN FETCH` (D1).
- 9 novembre 2026, 13 h 45–13 h 47 UTC : **même résultat, 109 tests, 0 échec, BUILD SUCCESS**, après recompilation de `JobOfferRepository` (`JOIN FETCH`) et `AdminController.countBySuspendedTrue`.
- 9 novembre 2026, après arrêt de l’instance 8091 : **109 tests, 0 échec, 0 erreur, BUILD SUCCESS**. Cette campagne a compilé `findAllWithRecruiter`. La base `hirelink` comptait encore 118 offres ensuite. `hirelink_test` est détruite en fin de test (create-drop).

Détail de la première campagne : NotificationFilter 7, OfferIsolation 4, ContractType 2, RefreshCookies 8, ApplicationServiceInterview 14, EmailTemplates 2, EntretienConvocation 10, EventNotifierInterview 7, InterviewReminder 7, InterviewService 20, MailService 6, OrganizationService 4, SkillCatalog 5, SkillMatching 10, TextMatcher 3.

## Instance isolée (ne pas confondre avec la démo)

Lancée le 9 novembre 2026. Arrêtée après le parcours navigateur, uniquement pour pouvoir relancer `mvn test` (Maven réécrit `target/classes`). La démo 8081 est restée UP.

- Port **8091**. La démo **8081** répondait encore `UP` après la recette.
- Base **`hirelink_recette`**, créée pour l’occasion. Comptage en lecture seule : `hirelink.job_offers` = 118, `hirelink_recette.job_offers` = 53. La base de démo n’a pas été réinitialisée.
- SMTP absent (journal : aucun serveur configuré). Clés IA forcées vides : l’assistant est resté local. Secret JWT propre à cette instance : `backend/data/jwt.key` n’a pas été réécrit (date de fichier inchangée, 28 octobre 2026).
- Script : `tmp/recette_isolee.py` et `tmp/recette_suite.py` (dossier `tmp/` ignoré par Git).

Résultats HTTP sur 8091, code corrigé :

- 21/21 : santé, Swagger refusé (401), login inconnu et mauvais mot de passe tous deux 401, cinq comptes de démo, 8 offres recruteur, pipeline 11, notifications, crédits, OCP interdit de dépublier / lire le pipeline / télécharger l’ics Atlas (403), vitrine de 52 offres sans champs de modération, formules, souscription Premium simulée, facture `HL-202611-1-1`, 53 offres admin, mot de passe oublié identique pour une adresse inconnue et une adresse connue.
- Suite : 2 entretiens dans le pipeline ; pause du membre Karim puis 403 sur son pipeline ; réintégration puis 200 ; grille d’entretien IA locale, crédit passé de 200 à 199.

Parcours navigateur du code corrigé, le 9 novembre 2026, sur un second serveur statique `http://localhost:5501` (origine distincte : le jeton du port 5500 n’a pas été remplacé). Compte recruteur Atlas.

- Tableau de bord : Premium, 199 crédits sur 200, entreprise non vérifiée, 6 actions, 3 candidatures à trier, aucun entretien aujourd’hui.
- Candidats : 11 dossiers (8 en cours, 2 entretiens, 1 refus).
- Pipeline : 8 offres actives, 8 dossiers actifs, colonnes Reçues 3, Présélection 2, Shortlist 1, Entretien 4.
- Entretiens : 2 à venir (Amina Tazi le 10 nov., Sara El Amrani le 12 nov.), 0 à replanifier, 0 passés.
- Notifications : 1 au total, 1 non lue (nouvelle connexion).
- Offres : 8 (7 en ligne, 1 hors ligne), sans champs de modération.

Ces chiffres sont ceux du jeu de données `hirelink_recette`. Ils diffèrent de la démo 8081 (crédits 12/20, 18 actions, offre de test L1).

Deux blocages ont été corrigés avant ce parcours. Les pages mettaient en cache `config.js` et `api.js`, donc le paramètre `?api=` était ignoré. `api.js` lit maintenant le même réglage d’onglet que `config.js`. L’ouverture de tous les ports locaux, ajoutée pour joindre 8091, a ensuite été retirée : la politique livrée n’autorise que le port 8081. Le serveur 5501 a été arrêté après le parcours. Le frontend 5500 répondait encore 200.

## Cahier v6.1

Introuvable dans le dépôt et sur le Bureau (les fichiers « v6 » trouvés sont un guide LexHub et des notes IPv6, sans rapport). La v5 du dépôt n’a pas été utilisée comme substitut.

## Commit local et installation propre

Commit de sauvegarde, branche `finalisation/livraison`, sans push : `8dc3eb203054af4fc3d7a445119ac014637b401e`.

Installation faite depuis un clone de ce commit, dossier `HireLink-install-8dc3eb2`, base `hirelink_install`, port **8092**, frontend statique **5502**. Le script `LANCER-HIRELINK.bat` n'a pas été exécuté : il viserait le port 8081 et la base `hirelink`. Le clone ne contenait ni `application-local.properties` ni `jwt.key`. SMTP absent. Après le démarrage, `hirelink.job_offers` valait toujours 118 et `hirelink_install.job_offers` 53.

Preuves HTTP sur 8092 :

- Santé UP, 52 offres publiques. Les champs de modération sont présents mais neutres (`suspended=false`, `reportCount=0`, `quarantineReason=null`) : la vitrine ne révèle pas une suspension.
- Candidat : candidature créée (200) avec un CV joint nommé, puis 1 dossier au statut `SUBMITTED`. Un envoi sans CV sur un profil vide renvoie 400, comportement prévu.
- Équipe Atlas : 4 membres actifs. Huit invitations successives et six en parallèle ont été acceptées ; le nombre d'actifs est resté 4. Une invitation sans réponse n'occupe pas un siège. L'acceptation, elle, revérifie le plafond sous verrou de ligne (correctif après cette installation).
- Facture `HL-202611-1-1`, visible par l'administrateur, passée à `REGLEE`. Quatre souscriptions simultanées après un retour au palier Accès : une facture nouvelle, trois refus, aucun numéro en double (3 factures au total, y compris celle du retour).
- Crédits IA : 8 grilles en parallèle, 8 réussites, débit exact de 8 (199 puis 191).
- Administration : 48 comptes et 53 offres renvoyés en liste complète.
- Après le verrou des sièges et de la facture : `mvn test` sur `hirelink_test`, 109 tests, 0 échec, 0 erreur.

## Pagination des listes administrateur

Les écrans Compte et Offres filtrent et recherchent la liste entière dans le navigateur. Les découper en pages sans déplacer cette recherche côté serveur rendrait les filtres faux. Sur la base d'installation, ces listes font 48 et 53 lignes, déjà chargées avec le recruteur et l'entreprise (D2). La pagination n'est donc pas appliquée.

## Corrections restantes (prochaine action exacte)

1. Rien n'a été poussé. `origin/main` reste `83b5bcf`. La publication GitHub attend une autorisation.
2. Le port 8081 n'a pas été redémarré. La clé `backend/data/jwt.key` n'a pas été régénérée.
3. Les pages autorisent de nouveau seulement le port local 8081, pas un port quelconque. Une recette sur un autre port doit citer ce port dans la balise CSP de la page.

## Opérations interdites sans autorisation

- Supprimer ou réinitialiser la base `hirelink`. Modifier ses données métier. Purger la recette L1.
- Redémarrer le backend de démonstration (port 8081, processus démarré à 10 h 09).
- Régénérer la clé JWT (`backend/data/jwt.key`) ou changer des secrets externes.
- Activer un vrai SMTP ou un vrai paiement.
- `git reset --hard`, `git clean -fd`, force-push, réécriture d’historique.
- `git push` / publication GitHub.
- Commit, sauf demande explicite.

## Reprendre après interruption

1. Lire ce fichier, puis `git status` (ne rien écraser).
2. Vérifier que 8081 et 5500 sont encore ceux décrits ci-dessus avant d’ouvrir un navigateur sur la démo.
3. Lire la fin du terminal de `mvn test` s’il n’est pas encore reporté ici.
4. Continuer à l’étape « Corrections restantes », dans l’ordre.
5. Mettre à jour ce fichier avant de s’arrêter, sans y copier de secret.
