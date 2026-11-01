# Tests automatiques des boutons — HireLink

Exécution du 01/11/2026 (Playwright, Chrome), sur 6 rôles (public, candidat, Owner, Recruteur, Hiring Manager, admin) et 3 variantes (clair, sombre, mobile 390 px).

**4172 contrôles, 4169 réussis, 3 limite(s).** Dont 19 scénarios avec vérification en base et 135 actions sensibles (suppression, refus, désactivation…) recensées mais non cliquées par le balayage. La limite restante est la connexion par passkey, impossible à tester sans authentificateur.

Méthode : chaque élément cliquable visible (liens, boutons, onglets, menus, filtres, cases) est cliqué sur une page fraîchement chargée. Le test relève l’effet obtenu (navigation, modale, changement d’état ou de contenu, thème) ainsi que toute erreur console ou HTTP. Les liens internes sont vérifiés en HTTP 200 et les ancres vérifiées sur la page.

## Synthèse par rôle

| rôle | variante | testés | réussis |
|---|---|---|---|
| admin | dark | 45 | 45 |
| admin | light | 49 | 49 |
| admin | mobile | 48 | 48 |
| candidat | dark | 279 | 279 |
| candidat | light | 284 | 284 |
| candidat | mobile | 279 | 279 |
| hm | dark | 261 | 261 |
| hm | light | 265 | 265 |
| hm | mobile | 263 | 263 |
| owner | dark | 328 | 328 |
| owner | light | 334 | 334 |
| owner | mobile | 319 | 319 |
| public | dark | 170 | 169 |
| public | light | 157 | 156 |
| public | mobile | 143 | 142 |
| recruteur | dark | 315 | 315 |
| recruteur | light | 316 | 316 |
| recruteur | mobile | 317 | 317 |

## Limites, corrections et réponses métier

| page | rôle | variante | élément | action attendue | résultat |
|---|---|---|---|---|---|
| set-password.html | public | light | button#btn-passkey « Se connecter avec une passkey » | réagit au clic | LIMITE (aucun authentificateur WebAuthn en navigateur automatisé) |
| set-password.html | public | dark | button#btn-passkey « Se connecter avec une passkey » | réagit au clic | LIMITE (aucun authentificateur WebAuthn en navigateur automatisé) |
| set-password.html | public | mobile | button#btn-passkey « Se connecter avec une passkey » | réagit au clic | LIMITE (aucun authentificateur WebAuthn en navigateur automatisé) |
| app/candidate-profile.html | candidat | light | button#btn-confirm-cv « Confirmer ces sections » | dans un volet replié | CORRIGÉ (désactivé tant qu’aucun CV n’est déposé, voir scénario) |
| app/candidate-profile.html | candidat | light | button#coach-analyze « Faire la synthèse » | réagit au clic | OK (réponse métier 402 : crédits épuisés, message affiché) |
| app/candidate-profile.html | candidat | dark | button#btn-confirm-cv « Confirmer ces sections » | dans un volet replié | CORRIGÉ (désactivé tant qu’aucun CV n’est déposé, voir scénario) |
| app/candidate-profile.html | candidat | dark | button#coach-analyze « Faire la synthèse » | réagit au clic | OK (réponse métier 402 : crédits épuisés, message affiché) |
| app/candidate-profile.html | candidat | mobile | button#btn-confirm-cv « Confirmer ces sections » | dans un volet replié | CORRIGÉ (désactivé tant qu’aucun CV n’est déposé, voir scénario) |
| app/candidate-profile.html | candidat | mobile | button#coach-analyze « Faire la synthèse » | réagit au clic | OK (réponse métier 402 : crédits épuisés, message affiché) |
| app/candidate-applications.html | candidat | light | button#d-ai-prep « Synthèse de mon CV » | réagit au clic | OK (réponse métier 402 : crédits épuisés, message affiché) |
| app/candidate-applications.html | candidat | dark | button#d-ai-prep « Synthèse de mon CV » | réagit au clic | OK (réponse métier 402 : crédits épuisés, message affiché) |
| app/candidate-applications.html | candidat | mobile | button#d-ai-prep « Synthèse de mon CV » | réagit au clic | OK (réponse métier 402 : crédits épuisés, message affiché) |

## Scénarios avec vérification en base

| page | rôle | élément | action attendue | résultat |
|---|---|---|---|---|
| app/candidate-profile.html | candidat | [scénario BDD] button#btn-save-profile « Enregistrer » | enregistre le profil en base | OK (BDD avant=Tétouan,Tanger,Hybride après=Tétouan,Tanger,Hybride,Rabat) |
| app/notifications.html | hm | [scénario BDD] button#btn-readall « Tout marquer comme lu » | read_flag=1 en base | OK (non lues 2 → 0) |
| app/admin.html | admin | [scénario BDD] form#rr-form « Ajouter un motif » | ligne créée dans refusal_reason_catalog | OK (lignes=1) |
| app/admin.html | admin | [scénario BDD] select#user-org « Organisation » | n’affiche que les comptes de l’organisation | OK (4 comptes, tous Atlas Digital) |
| app/admin.html | admin | [scénario BDD] chip « Hiring Managers » | filtre sur le rôle d’organisation | OK (1 compte(s) : MA Mehdi Alaoui m.alaoui@atlasdigital.ma) |
| index.html | recruteur | [scénario BDD] bouton thème (app → site public) | la préférence suit sur le site public | OK (data-theme=dark) |
| app/candidate.html | candidat | [scénario BDD] menu profil > « Aide » | ouvre le menu puis le panneau d’aide | OK (menu=true aide=true) |
| app/candidate.html | candidat | [scénario BDD] button#rc-bell « Notifications » | ouvre le panneau des notifications | OK (Notifications Tout marquer comme lu Candidature reçue Votre) |
| app/candidate.html | candidat | [scénario BDD] menu profil > « Se déconnecter » + confirmation | demande confirmation, supprime la session et quitte l’application | OK (confirmation affichée, redirection /auth.html) |
| app/recruiter.html | owner | [scénario BDD] menu profil > « Aide » | ouvre le menu puis le panneau d’aide | OK (menu=true aide=true) |
| app/recruiter.html | owner | [scénario BDD] button#rc-bell « Notifications » | ouvre le panneau des notifications | OK (Notifications Tout marquer comme lu Nouvelle candidature Im) |
| app/recruiter.html | owner | [scénario BDD] menu profil > « Se déconnecter » + confirmation | demande confirmation, supprime la session et quitte l’application | OK (confirmation affichée, redirection /auth.html) |
| app/recruiter.html | hm | [scénario BDD] menu profil > « Aide » | ouvre le menu puis le panneau d’aide | OK (menu=true aide=true) |
| app/recruiter.html | hm | [scénario BDD] button#rc-bell « Notifications » | ouvre le panneau des notifications | OK (Notifications Tout marquer comme lu Rappel d'entretien Dema) |
| app/recruiter.html | hm | [scénario BDD] menu profil > « Se déconnecter » + confirmation | demande confirmation, supprime la session et quitte l’application | OK (confirmation affichée, redirection /auth.html) |
| app/recruiter-offers.html | owner | [scénario BDD] menu offre ⋯ > « Critères de scorecard » | ouvre la modale des critères | OK (modale=true) |
| app/recruiter-offers.html | owner | [scénario BDD] menu offre ⋯ > « Dépublier » | bascule job_offers.published en base | OK (offre #8 published → 0) |
| app/recruiter-offers.html | owner | [scénario BDD] menu offre ⋯ > « Publier » | rétablit l’état initial en base | OK (offre #8 published → 1) |
| app/candidate-profile.html | candidat | [scénario BDD] details « Sections lues » > button#btn-confirm-cv « Confirmer ces sections » | sans CV déposé : bouton désactivé avec explication (le serveur refuserait) | OK (désactivé=true, « Déposez d’abord un CV (PDF ou image) pour confirmer ses sections. ») |

La matrice complète (page, rôle, élément, action attendue, résultat), ligne par ligne, se trouve dans `docs/TESTS_BOUTONS.csv`.