# HireLink — Postman + Newman (ISO)

Suite API professionnelle pour l’instance **ISO** (`http://127.0.0.1:18081`), sans Docker.

## Interdictions

- Ne **jamais** pointer `baseUrl` vers `:8081` (démo / JAR F3C2 / base `hirelink`).
- Ne pas contourner CAPTCHA, OTP ou MFA.
- Ne pas committer `environments/*.local.json` ni `reports/`.

## Fichiers

| Fichier | Rôle |
|---------|------|
| `HireLink.postman_collection.json` | Collection Postman v2.1 |
| `environments/HireLink.iso.environment.example.json` | Environnement sans mots de passe |
| `scripts/build-collection.mjs` | Régénère la collection |
| `scripts/run-newman.ps1` | Lancement Newman (Windows) |

## Prérequis Windows

1. Node.js 20+ (`node`, `npm` / `npx`)
2. Backend ISO démarré sur **18081** (base isolée, typiquement `hirelink_iso`)
3. Comptes seed présents (voir `DataSeeder` / e2e)

## Import Postman Desktop

1. Import → `HireLink.postman_collection.json`
2. Import → `environments/HireLink.iso.environment.example.json`
3. Renseigner les mots de passe seed dans l’environnement (ou variables de collection)
4. Sélectionner l’environnement **HireLink ISO**
5. Exécuter le dossier `1. Authentification…` en premier (jetons)

## Newman (automatisé)

PowerShell :

```powershell
cd C:\Users\daurinia\Desktop\HireLink-iso\postman\scripts

# Mots de passe = comptes seed locaux UNIQUEMENT (jamais production)
$env:HL_CANDIDATE_PASSWORD = "<seed-candidat>"
$env:HL_RECRUITER_PASSWORD = "<seed-recruteur>"
$env:HL_ADMIN_PASSWORD     = "<seed-admin>"
# optionnel : $env:HL_API = "http://127.0.0.1:18081/api/v1"

.\run-newman.ps1
```

Rapports : `postman/reports/newman-*.json` (gitignorés).

## Domaines couverts

1. Prérequis & santé
2. Authentification et sécurité (CAPTCHA réel)
3. Profil candidat et CV (lecture)
4. Recherche et catalogue
5. Offres et candidatures
6. ATS et entretiens (lecture)
7. Organisations
8. Abonnements et crédits IA (lecture)
9. Administration (refus d’accès + MFA admin non contournée)

Couverture **représentative** (smoke + authz), pas exhaustive de tous les endpoints admin/ATS.

### Billing `/quote` — piège ESSENTIEL

| Rôle | `plan` valide pour devis payant | Note |
|------|----------------------------------|------|
| CANDIDAT | `PERFORMANCE` (défaut si omis) | `ESSENTIEL` → **400** métier |
| RECRUTEUR (OWNER) | `PROFESSIONNEL` / `ENTREPRISE` | `ESSENTIEL` catalogue gratuit → **400** si passé à `/quote` |

Message candidat : `Formule candidat payante : Performance uniquement.`

## Non automatisés (manuel)

- Vérification OTP e-mail / reset-password (jeton e-mail)
- MFA TOTP admin (`/auth/mfa/verify`)
- Passkeys WebAuthn
- Upload CV / avatar / logo (multipart)
- Planification / annulation d’entretien (effets métier)
- `POST /billing/subscribe`, paiements, erase/deactivate compte
- Sync admin external-offers, mutations promotions

## Régénérer la collection

```powershell
node postman\scripts\build-collection.mjs
```
