# HireLink — Démarrer (installation vérifiée)

Guide destiné au professeur / jury. **Délai réel mesuré sur machine de préparation : ~8–15 minutes** la première fois (Java/Maven/MySQL déjà installés, première compilation Maven plus longue). Ce n’est **pas** une promesse « 5 minutes » non vérifiée.

## Prérequis

| Outil | Version |
|-------|---------|
| Java | 21+ |
| Maven | 3.9+ |
| MySQL | 8.0+ |
| Navigateur | Chrome / Edge / Firefox |
| Node.js | 18+ (pour servir le frontend avec `npx serve`) |

## Pas à pas

1. Copier le dépôt (clone Git **après** autorisation de push, ou archive fournie).
2. `scripts\VERIFY-PREREQS.bat`
3. Créer une base **vierge** (exemple) :
   ```bat
   set HIRELINK_DB_NAME=hirelink_demo
   scripts\INSTALL-DB.bat
   ```
   Le script **refuse** d’écraser une base existante.
4. Copier `backend\src\main\resources\application-local.properties.example`  
   → `backend\application-local.properties`  
   Renseigner JDBC vers `hirelink_demo` (utilisateur/mot de passe locaux).
5. Terminal 1 : `scripts\LANCER-BACKEND.bat`  
   Attendre `Tomcat started` sur le port configuré (`HIRELINK_PORT`, défaut 8081).
6. Terminal 2 : `scripts\LANCER-FRONTEND.bat`  
   Ouvrir `http://127.0.0.1:5500`
7. Comptes seed (après premier démarrage avec `hirelink.seed.enabled=true`) : voir README § Comptes.

## Arrêt

`scripts\ARRETER-HIRELINK.bat` — n’arrête que les processus HireLink/serve sur les ports configurés.

## Limites honnêtes

- Sans SMTP : e-mails **journalisés** (statut LOGGED / hint SMTP_OFF), pas « envoyés ».
- Sans clé LLM : assistant en **repli local** explicite.
- Paiement : **simulé**.
