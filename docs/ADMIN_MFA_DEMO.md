# MFA administrateur — procédure de démonstration (sans secret dans Git)

**Règles :** ne pas contourner le MFA · ne pas committer de secret TOTP · ne pas désactiver MFA pour « faire passer » un test.

## Compte

| Champ | Valeur |
|-------|--------|
| Email | `admin@hirelink.ma` |
| Mot de passe seed | `Admin123!` |
| MFA au seed | **désactivée** par défaut (démo pédagogique) |

`HIRELINK_ADMIN_MFA_REQUIRED=false` dans `.env.example` : le mot de passe seul suffit en local. En production, mettre `true`.

## Activation MFA (autorisée, manuelle)

1. Se connecter avec `admin@hirelink.ma` / `Admin123!`.
2. Ouvrir **Paramètres / Sécurité** (page compte).
3. Lancer **Configurer la double authentification**.
4. Scanner le QR / saisir le secret dans une appli Authenticator (Google Authenticator, Microsoft Authenticator…).
5. Confirmer avec un code TOTP à 6 chiffres.
6. **Noter les codes de secours hors Git** (papier / gestionnaire local).
7. Se déconnecter puis se reconnecter : **écran défi MFA obligatoire** avant le tableau de bord admin.

## Test manuel attendu

| Étape | Attendu |
|-------|---------|
| Login + MFA valide | Accès `admin.html` |
| Login + MFA invalide | Refus, pas de session |
| Candidat sur `/api/v1/admin/*` | 403 |

## Ce qui n’est PAS dans le dépôt

- Aucun secret TOTP
- Aucun QR
- Aucun code de secours

## Statut Vague 7

Si l’activation n’a pas été réalisée sur l’instance sous test : documenter **BLOCKED / NON EXÉCUTÉ** — ne pas déclarer PASS MFA.
