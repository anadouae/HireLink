# Fonctionnement de l’administration HireLink

Ce document décrit ce que voit et ce que peut faire un compte **administrateur**. Il ne contient aucun identifiant : le compte administrateur de démonstration est remis séparément.

L’espace se trouve dans `frontend/app/admin.html` (une page, une vue par onglet du menu). Toutes les routes sont sous `/api/v1/admin/…` et réservées au rôle `ADMINISTRATEUR`. Chaque action sensible est inscrite au journal d’audit.

---

## 1. Vue d’ensemble (`#dash`)

Source : `GET /admin/overview?granularity=week|month` (`AdminInsightsService`).

| Bloc | Contenu |
|---|---|
| Indicateurs | revenu mensuel des abonnements actifs, montant encaissé (factures payées), comptes (dont vus ces 30 derniers jours), entreprises, offres publiées, candidatures |
| Revenus par formule | barres horizontales : Essentiel, Professionnel, Entreprise (recruteurs) et Performance (candidats) |
| Répartition des formules | deux anneaux : entreprises (Essentiel / Professionnel / Entreprise) et candidats (Gratuit / Performance) |
| Inscriptions | histogramme des 12 dernières semaines ou des 12 derniers mois |
| À traiter | signalements en attente, offres suspendues, factures non payées (nombre et montant), entreprises sans badge, e-mails en échec sur 7 jours. Chaque tuile ouvre l’onglet concerné, déjà filtré |

Les dates sont calculées en heure du Maroc (Africa/Casablanca).

## 2. Comptes (`#users`)

- Liste avec rôle, entreprise, formule, date d’inscription ; filtres Candidats, Recruteurs, Créateurs d’entreprise, Désactivés.
- **Désactiver / Réactiver** un compte (`PATCH /admin/users/{id}/active`) : un compte désactivé ne peut plus se connecter.
- **Déconnecter** (`POST /admin/users/{id}/revoke-sessions`) : ferme toutes les sessions ouvertes du compte. L’administrateur ne peut pas se déconnecter lui-même par ce bouton.
- Chaque action demande une confirmation.

## 3. Entreprises (`#orgs`)

Filtres : Toutes, Vérifiées, Sans badge, Formule payante, Désactivées, Retirées. La fiche d’une entreprise (`GET /admin/organizations/{id}`) montre les membres, les offres, la formule et l’état du badge.

### 3.1 Badge « entreprise vérifiée » (modèle LinkedIn)

Le badge est **automatique** (`CompanyVerificationService`) :

1. Le nom de l’entreprise est obligatoire ; l’ICE est facultatif (s’il est saisi, son format est contrôlé et il ne doit pas appartenir à une autre entreprise).
2. Le badge est accordé si le créateur de l’entreprise utilise une **adresse e-mail professionnelle** : ni messagerie gratuite (Gmail, Outlook, Yahoo, Menara…), ni adresse d’école ou d’université (`.edu`, `.ac.`, `etu.`, `student.`).
3. Sinon, l’entreprise reste « E-mail professionnel requis » : elle utilise HireLink normalement, sans badge.

L’administrateur **ne peut pas** accorder le badge à la main (la route renvoie une erreur explicite). Il peut :

- **Retirer le badge** (`PATCH /admin/organizations/{id}` avec `verified=false`) : statut « Vérification retirée ». L’entreprise ne peut plus relancer la vérification elle-même ; elle doit passer par le Support.
- **Relancer la vérification** (`POST /admin/organizations/{id}/recheck`) : applique de nouveau la règle automatique ci-dessus.

### 3.2 Désactivation (réversible)

`PATCH /admin/organizations/{id}/active` avec `active=false` (`AdminOrganizationService`) :

- les membres actifs sont désactivés et leurs sessions fermées ;
- les offres publiées de l’entreprise (et celles de ses recruteurs) sont dépubliées ;
- la liste exacte des comptes et des offres mis en pause est conservée (`paused_member_ids`, `paused_offer_ids`).

À la réactivation (`active=true`), **seuls** ces comptes et ces offres sont rétablis. Une offre suspendue par la modération, archivée ou clôturée entre-temps n’est pas republiée.

### 3.3 Retrait

`DELETE /admin/organizations/{id}` : l’entreprise passe à l’état « Retirée », perd son badge et n’est plus réactivable depuis l’interface. **Aucune donnée n’est supprimée** en base (candidatures, factures et historique restent consultables).

## 4. Offres (`#offers`) et signalements (`#moderation`)

- Liste des offres avec filtres ; actions **Suspendre**, **Rétablir**, **Dépublier**, **Écarter les signalements**.
- Trois signalements suspendent une offre automatiquement ; l’administrateur confirme la sanction, l’annule ou écarte les signalements. Les décisions passées sont listées avec la date et l’auteur.

## 5. Messages du support (`#support`)

Messages envoyés depuis la page Support (`GET /admin/support`, `PATCH /admin/support/{id}`). Filtres À traiter / Traités / Tous ; **Lire** ouvre le message complet, **Répondre par e-mail** ouvre la messagerie, puis **Marquer traité** (ou **Rouvrir**).

## 6. Recherche web (`#external`)

Deux encadrés : les offres importées (en pause) et la recherche web en direct (active), avec la liste des sites interrogés. Un tableau donne l’état de chaque source d’import et la date de la dernière mise à jour ; le bouton « Mettre à jour maintenant » interroge les sources d’import actives (il reste grisé tant que l’import est en pause).

## 7. Formules et factures (`#plans`)

Un sélecteur centré bascule entre trois volets :

- **Formules** : prix, crédits IA et nombre d’abonnés de chaque formule ; modification du prix et des libellés (`PATCH /admin/plans/{code}`).
- **Factures** : toutes les factures, filtrables par état (payée, non payée…), téléchargement en PDF.
- **Consommation IA** : crédits utilisés ce mois-ci, par compte, avec une jauge par rapport au plafond de la formule.

Les anciennes formules ACCES et PREMIUM ne sont plus proposées : au démarrage, leurs abonnés éventuels passent sur Essentiel et Professionnel, leurs lignes restent en base pour l’historique des factures, et les mentions internes en double sont retirées des descriptions.

## 8. Journal d’audit (`#audit`)

`GET /admin/audit` (paginé, 25 lignes par page) avec : catégories (Connexions et sécurité, Comptes, Équipes, Offres et candidatures, Facturation, Administration), période Du / Au (raccourcis 7 j, 30 j, 90 j, Tout), résultat (réussi / échoué) et recherche libre. **Exporter** produit un CSV (jusqu’à 2 000 lignes) avec les mêmes filtres.

## 9. Journal des e-mails (`#emails`)

`GET /admin/emails` (paginé) : état (Envoyé, Non envoyé, Échec, avec compteurs), période, type de message et recherche par destinataire ou objet. « Non envoyé » signifie que l’envoi d’e-mails n’est pas configuré : le message est seulement enregistré. Le contenu des messages confidentiels (codes, liens) n’est jamais conservé. Historique de 30 jours.

## 10. Mon compte

`frontend/app/account.html` : nom, photo de profil (1,5 Mo maximum), mot de passe, dernières connexions. La double authentification de l’administrateur est décrite dans `docs/ADMIN_MFA_DEMO.md`.

---

## Changements de base appliqués au démarrage

| Quoi | Comment |
|---|---|
| Colonnes d’accès des entreprises (`access_status`, `access_changed_at`, `paused_member_ids`, `paused_offer_ids`) | Hibernate `ddl-auto=update` ; migration `V011__organization_access.sql` pour une base gérée à la main |
| Photos de profil : `user_avatars.data` en `MEDIUMBLOB` | `AvatarColumnRepair` au démarrage ; migration `V010__user_avatar_mediumblob.sql` |
| Catalogue des formules (anciennes ACCES / PREMIUM, lignes en double) | Nettoyage au démarrage (`PlanCatalogMigrationService`) |
