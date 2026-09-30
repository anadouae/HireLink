# Configurer Google Sign-In (HireLink)

Guide pas à pas pour brancher le vrai bouton Google (au lieu du mode démo).

## 1. Google Cloud Console

1. Ouvrir [https://console.cloud.google.com/](https://console.cloud.google.com/)
2. Créer un projet (ex. `HireLink-ENSA`)
3. Menu **APIs & Services → OAuth consent screen**
   - Type : **External** (ou Internal si Workspace)
   - Nom app : `HireLink`
   - Email support : le vôtre
   - Scopes : `email`, `profile`, `openid`
   - Ajouter votre compte en **Test user** si l’app est en Testing
4. **APIs & Services → Credentials → Create credentials → OAuth client ID**
   - Application type : **Web application**
   - Name : `HireLink Web`
   - **Authorized JavaScript origins** :
     - `http://localhost:5500`
     - `http://127.0.0.1:5500`
     - `http://localhost:8081` (si vous servez le front via Spring)
   - **Authorized redirect URIs** (souvent non nécessaires pour GIS One Tap / popup, mais utile) :
     - `http://localhost:5500`
5. Copier le **Client ID** (forme `xxxxx.apps.googleusercontent.com`)

## 2. Backend HireLink

Fichier : `backend/src/main/resources/application.properties`

```properties
hirelink.google.client-id=VOTRE_CLIENT_ID.apps.googleusercontent.com
hirelink.google.dev-bypass=false
```

Puis redémarrer Spring Boot.

- `dev-bypass=true` → le front propose « Google (démo) » sans Client ID (uniquement pour la salle de classe).
- `dev-bypass=false` + Client ID renseigné → vrai jeton Google vérifié côté serveur (`GoogleTokenService`).

## 3. Front

Le bouton Google lit `/api/v1/auth/config` :
- si `googleClientId` est non vide → GIS (Google Identity Services)
- sinon + bypass → formulaire démo

Hard refresh (`Ctrl+F5`) sur `auth.html` après redémarrage API.

## 4. Vérification

1. `http://localhost:5500/auth.html`
2. Choisir rôle → **Continuer avec Google**
3. Compte Google test → redirection espace candidat / recruteur
4. En base : `users.google_sub` renseigné, `email_verified=1`

## 5. Prod (plus tard)

- Ajouter `https://votredomaine.com` dans les origines JS
- Désactiver `dev-bypass`
- Restreindre le consent screen (Publishing status)
- Ne jamais committer un Client Secret dans le repo (HireLink n’utilise que le Client ID côté front + vérif ID token côté serveur)
