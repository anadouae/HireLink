# Vérification employeurs — comment le marché fait (et HireLink)

## « Code email » clarifié
Sur Indeed / LinkedIn / HireLink, ce n’est **pas** un produit nommé « code email ».  
C’est une **méthode de connexion** : on envoie un **code OTP** à votre adresse e-mail pour prouver que vous la contrôlez (passwordless).  
Sur HireLink : bouton *« Se connecter avec un code reçu par e-mail »*.

## Techniques du marché (anti-faux recruteurs)

| Plateforme | Méthode principale |
|------------|--------------------|
| **Indeed** | Carte bancaire + téléphone + ID fiscal |
| **LinkedIn** | Page entreprise + email **@domaine.com** (pas Gmail) |
| **France** | SIRET (= ICE au Maroc) + raison sociale |
| **Workable / Greenhouse** | Contrat B2B + facturation société |

HireLink combine plusieurs couches **sans API payante** :
1. Format ICE valide  
2. ICE unique en base  
3. Registre interne (raison sociale ↔ ICE)  
4. Email pro (domaine société, pas free-mail pour publier)  
5. Flag `company_verified`  
6. **Blocage publication** tant que non vérifié  
7. Admin peut forcer la validation  

Voir aussi `ICE_VERIFICATION.md`.

## Sécurité SaaS déjà en place
- JWT stateless + BCrypt  
- OTP TTL + log console (démo)  
- Rôles Spring Security (`CANDIDAT` / `RECRUTEUR` / `ADMINISTRATEUR`)  
- CORS whitelist  
- En-têtes : `X-Content-Type-Options`, `X-Frame-Options`, `Referrer-Policy`, `Permissions-Policy`, HSTS si HTTPS  
- Rejet motivé obligatoire (RM03)  
- SLA côté candidatures  

## Growth SaaS réellement implémenté
- Popup de changement de formule (`frontend/js/growth.js`) — candidat : **Performance 39 MAD**, recruteur : **Premium 299 MAD**
- Nudge après délai + couronne « Premium » dans la barre d’espace (`app-shell.js`)
- Cartes de prix de la landing alignées sur la table `commercial_plans`
- Offres **triées** par score de compatibilité (Jaccard + lieux + contrats + métiers)
- Ce que la formule change réellement : le **plafond de crédits IA** (5 / 40 / 20 / 200, `AiCreditService`) et le **nombre de sièges** d’équipe (5 / 25, `OrganizationService`). Aucun classement payant, aucune mise en avant — le matching ne regarde pas `plan_tier`.
