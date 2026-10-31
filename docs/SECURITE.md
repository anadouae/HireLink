# HireLink – Sécurité (OWASP Top 10 2021 / ASVS niveau 2)

Ce document décrit les mesures de sécurité réellement implémentées dans HireLink, les fichiers concernés et la façon de les vérifier.
Chaque mesure a été testée sur l'application en fonctionnement (voir « Tests exécutés »). Les limites connues sont listées à la fin : elles sont assumées et non cachées.

> Environnement de test : Spring Boot 3.3.13, Java 21, **MariaDB 10.11** (MySQL 8 n'était pas installable sur la machine de test ; le schéma est généré par Hibernate et n'utilise aucune syntaxe propre à MariaDB, mais MySQL 8 n'a pas été testé ici).

## 1. Vue d'ensemble

| Domaine | Choix |
|---|---|
| Mots de passe | BCrypt coût 12 (mise à niveau automatique à la connexion des anciens hachages), 12 à 64 caractères, liste de mots de passe interdits, pas d'e-mail ni de nom dans le mot de passe, hachage factice pour les comptes inconnus (pas de différence de temps) |
| Session | Jeton d'accès JWT signé HMAC (**HS512** avec la clé générée automatiquement ; HS256 si vous fournissez un secret de 32 à 63 caractères : en prod, choisissez-en un d'au moins 64) de 15 min (`iss`, `aud`, `typ`, `jti`, `sid`) + jeton de rafraîchissement **opaque** stocké uniquement haché (SHA-256) dans `user_sessions`, cookie `hl_rt` `HttpOnly` `SameSite=Strict` limité à `/api/v1/auth` |
| Rotation | Chaque rafraîchissement remplace le jeton ; la réutilisation d'un ancien jeton **révoque toute la session** (détection de vol) |
| Durées | Inactivité : 120 min ; durée absolue : 168 h (`hirelink.session.idle-minutes`, `hirelink.session.absolute-hours`) |
| MFA | TOTP (RFC 6238) optionnel pour tous, **obligatoire pour l'administrateur** (sauf connexion par passkey), 10 codes de secours à usage unique |
| Passkeys | WebAuthn (bibliothèque Yubico) optionnel pour candidat et recruteur ; administrateur = passkey **ou** mot de passe + TOTP |
| Actions sensibles | Ré-authentification (mot de passe ou code e-mail, + TOTP si activé) valable quelques minutes |
| Anti-abus | Verrouillage de compte (5 échecs / 15 min → 429 + `Retry-After`), limites par IP, OTP, MFA, ré-authentification, mot de passe oublié, plafond global |
| Secrets | Aucun secret dans le dépôt : variables d'environnement (`.env.example`) ou `application-local.properties` (ignoré par Git) |

## 2. Table de correspondance OWASP Top 10 2021 / ASVS 4.0 niveau 2

| OWASP / ASVS | Mesure dans HireLink | Fichier(s) | Comment tester |
|---|---|---|---|
| **A01 Broken Access Control** – ASVS V4.1, V4.2 | `@PreAuthorize` par rôle sur chaque contrôleur ; contrôle **d'objet** (organisation, propriétaire de l'offre, recruteur assigné pour le rôle « responsable de recrutement ») ; refus par défaut (`anyRequest().authenticated()`) ; 403 journalisé (`ACCESS_DENIED`) | `config/SecurityConfig`, `controller/*`, `service/OrganizationService.assertCanAccessApplication`, `service/ApplicationService`, `service/ScorecardService` | Se connecter en recruteur d'une autre organisation et appeler `GET /api/v1/applications/{id}` d'une candidature qui n'est pas la sienne → **403** |
| A01 – ASVS V4.3 (administration) | Tout `/admin/**` réservé à `ADMINISTRATEUR` ; MFA obligatoire pour ce rôle | `controller/AdminController`, `service/AuthService` | Appeler `/api/v1/admin/users` avec un jeton candidat → 403 |
| A01 – IDOR fichiers | CV, logos : accès contrôlé par propriétaire / organisation ; noms de fichiers générés (UUID) | `service/CvStorage`, `service/OrgLogoStorage`, `controller/ApplicationController`, `controller/MeController` | Télécharger le CV d'un autre candidat → 403/404 |
| **A02 Cryptographic Failures** – ASVS V2.4, V6, V9 | BCrypt 12 ; refresh et codes de secours stockés hachés ; OTP e-mail stocké sous forme de HMAC tronqué ; secret TOTP chiffré AES-GCM ; JWT signé HMAC (HS512 avec la clé générée de 64 octets) ; cookie `Secure` en prod | `security/PasswordPolicy`, `service/SessionService`, `service/TotpService`, `security/JwtService`, `service/OtpService` | Inspecter la base : aucune colonne ne contient de jeton ou de code en clair |
| A02 – Secrets | Clé JWT lue dans `HIRELINK_JWT_SECRET`, sinon générée dans `backend/data/jwt.key` (ignoré par Git) ; refus de démarrer en prod sans clé de 32 caractères minimum | `security/JwtService`, `application-prod.properties`, `.env.example` | Démarrer avec `--spring.profiles.active=prod` sans variable → échec explicite |
| **A03 Injection** – ASVS V5.3 | Requêtes JPA/paramétrées uniquement (aucune concaténation SQL) ; Bean Validation `@Valid` + `@Size` sur tous les DTO, tailles alignées sur les colonnes ; échappement HTML systématique côté interface (`esc()`), aucun `onclick`/`onerror` en ligne | `dto/ApiDtos`, `repository/*`, `frontend/js/*.js` | Saisir `"><i class=x>` dans nom, offre, lettre, note : le texte s'affiche tel quel, aucun élément n'est injecté (test XSS automatisé) |
| A03 – XSS / CSP – ASVS V5.3, V14.4 | Politique CSP sur **chaque page** (`<meta http-equiv="Content-Security-Policy">`) : `script-src 'self' https://accounts.google.com`, `object-src 'none'`, `base-uri 'self'`, `form-action 'self'` ; scripts en ligne supprimés ; CSP + `X-Content-Type-Options` + `Referrer-Policy` + `Permissions-Policy` + `X-Frame-Options: DENY` côté API | `frontend/**/*.html`, `config/SecurityHeadersFilter` | Ouvrir la console du navigateur : aucune erreur « Refused to execute inline script » ; `curl -I http://localhost:8081/api/v1/health` pour les en-têtes |
| **A04 Insecure Design** – ASVS V1, V11 | Modélisation des menaces par rôle ; limitation de débit ; réponses génériques (pas d'énumération de comptes : même réponse pour compte inconnu et mot de passe faux, et pour « mot de passe oublié ») ; plafond de taille des requêtes (8 Mo fichier, 1 Mo formulaire) | `security/AuthThrottle`, `security/RateLimitFilter`, `service/PasswordResetService` | Demander un lien de réinitialisation pour une adresse inconnue → même message |
| **A05 Security Misconfiguration** – ASVS V14.1, V14.3 | Profil `prod` : pas de données de démo, pas d'OTP dans les logs, Swagger désactivé, cookie `Secure`, secrets obligatoires ; page d'erreur sans trace ni message interne ; `/health` ne renvoie que l'état et `database.connected` (ni version ni nom de base) ; Swagger désactivé par défaut même en dev | `application.properties`, `application-prod.properties`, `controller/HealthController`, `config/GlobalExceptionHandler` | `GET /swagger-ui.html` → 401 sans connexion, 403 une fois connecté (accès refusé par défaut) ; provoquer une erreur 500 : la réponse ne contient qu'une référence |
| A05 – CORS – ASVS V14.5 | Liste blanche d'origines (`HIRELINK_CORS_ORIGINS`), pas de `*` avec identifiants ; vérification d'`Origin` + en-tête `X-HireLink-CSRF` sur `/auth/refresh` et `/auth/logout` | `config/SecurityConfig`, `security/RefreshCookies` | `curl -H "Origin: http://evil.example" -X POST …/auth/refresh` → 403 |
| **A06 Vulnerable Components** – ASVS V14.2 | Spring Boot 3.3.4 → **3.3.13** (correctifs de sécurité) ; dépendances minimales ; Yubico WebAuthn 2.6.0, ZXing pour le QR code | `backend/pom.xml` | `mvn dependency:tree` ; pour aller plus loin : `mvn org.owasp:dependency-check-maven:check` (non exécuté ici, pas d'accès à la base NVD) |
| **A07 Identification & Authentication Failures** – ASVS V2, V3 | Politique de mot de passe (12–64, liste noire) ; verrouillage ; MFA TOTP + codes de secours ; passkeys ; sessions renouvelées, listées et révocables ; révocation de toutes les sessions lors d'un changement de mot de passe, de rôle ou d'une suspension ; réinitialisation par lien à usage unique (30 min, haché) ; ré-authentification avant action sensible | `service/AuthService`, `service/AccountService`, `service/SessionService`, `service/TotpService`, `service/WebAuthnService`, `service/PasswordResetService`, `frontend/app/security.html` | Voir scénarios « Sécurité » du guide d'installation et de test |
| A07 – OTP | Code à 6 chiffres, 15 min, 5 essais maximum (persistés même en cas d'échec), usage unique | `service/OtpService` | 6 mauvais codes puis le bon code → refusé |
| A07 – Google | Jeton Google vérifié (signature, `aud`, `iss`, `email_verified`) ; contournement de développement désactivé et sans reprise de compte existant | `service/GoogleTokenService`, `service/AuthService.google` | Jeton falsifié → 401 générique |
| **A08 Software & Data Integrity Failures** – ASVS V10, V12 | Fichiers acceptés **par signature binaire** (PDF, PNG, JPG, WEBP, TXT ; SVG et GIF refusés pour les CV, SVG refusé pour les logos), `Content-Type` explicite en téléchargement, noms de fichier assainis ; JWT : signature HMAC vérifiée avec la clé du serveur (jetons non signés refusés), `iss`/`aud` vérifiés, `typ` vérifié (un jeton MFA ne sert pas de jeton d'accès) | `service/FileSignature`, `service/CvStorage`, `service/OrgLogoStorage`, `security/JwtService` | Envoyer un `.exe` renommé en `.pdf` → 400 (« Format de CV non accepté ») ; fichier > 8 Mo → 413 |
| **A09 Security Logging & Monitoring Failures** – ASVS V7 | Journal d'audit en base (`audit_log`) : connexions réussies/échouées, verrouillage, MFA, changements de mot de passe/rôle, sessions révoquées, réutilisation de jeton, accès refusés, changements d'étape, export et effacement de données, suspension… ; écriture dans une transaction indépendante (conservée même si l'action échoue) ; aucun mot de passe/jeton dans les logs | `service/AuditService`, `model/AuditLog`, `controller/AdminController` (`GET /admin/audit`), vue « Journal d'audit » de `app/admin.html` | Se connecter avec un mauvais mot de passe puis consulter le journal en tant qu'administrateur |
| **A10 SSRF** – ASVS V12.6 | Aucune URL fournie par l'utilisateur n'est récupérée côté serveur (les liens de site web ne sont que stockés et affichés) ; appels sortants limités à des hôtes fixes : fournisseurs IA configurés, Google, OpenStreetMap Nominatim et ESCO (suggestions de lieux et de compétences) ; le texte saisi n'y est transmis que comme paramètre de recherche encodé | `service/Llm*`, `service/GoogleTokenService` | Revue de code : aucun `HttpClient` ne reçoit une URL fournie par l'utilisateur (`service/Http*`, `controller/CatalogSuggestController`) |
| ASVS V8 (données) – RGPD | Export des données personnelles (`DATA_EXPORT` audité), consentements, désactivation puis **anonymisation** du compte (`ACCOUNT_ERASED`), après ré-authentification | `controller/CatalogPrivacyController`, `service/PrivacyService`, `app/security.html` | Page « Sécurité » → exporter, puis supprimer le compte |

## 3. Sessions

* `POST /auth/login` renvoie le jeton d'accès (15 min) et pose le cookie `hl_rt`.
* `POST /auth/refresh` (cookie + en-tête `X-HireLink-CSRF` + `Origin` autorisé) : nouveau couple de jetons, ancien jeton invalidé. Si l'ancien jeton est présenté de nouveau → session révoquée, événement `SESSION_TOKEN_REUSE`.
* À **chaque requête**, `JwtAuthFilter` vérifie : signature, expiration (tolérance 60 s), `iss`, `aud`, `typ`, que le compte est actif, que la session `sid` existe et n'est pas révoquée.
* La page `Sécurité` liste les sessions actives (appareil, IP, dernière activité) et permet de les révoquer une à une ou toutes ensemble.
* Révocation automatique : changement de mot de passe, réinitialisation, changement de rôle, suspension ou suppression du compte.

## 4. Authentification renforcée

* **TOTP** : activation par QR code + clé manuelle, vérification d'un premier code, génération de 10 codes de secours (affichés une seule fois, stockés hachés). Un code TOTP déjà utilisé est refusé (anti-rejeu).
* **Administrateur** : à la première connexion par mot de passe, l'application impose l'enrôlement TOTP (écran QR code), puis exige le code à chaque connexion. Aide pour la démonstration sans téléphone : `python docs/outils/totp.py <clé>` (bibliothèque standard uniquement).
* **Passkeys** : `Sécurité → Passkeys → Ajouter` ; sur la page de connexion, bouton « Se connecter avec une passkey » (sans saisir d'e-mail). Fonctionne sur `http://localhost:5500` (identifiant de partie de confiance `localhost`).
* **Ré-authentification** : changement de mot de passe, activation/désactivation MFA, suppression de passkey, export et suppression du compte exigent une confirmation récente.

### 4.1 Notifications en temps réel et e-mails

* **Flux SSE** : le jeton d'accès ne transite jamais dans l'URL. Le client demande un *ticket* (`POST /notifications/stream-ticket`, authentifié) à usage unique, valable 30 secondes et lié à l'utilisateur ; le flux (`GET /notifications/stream?ticket=…`) n'envoie que les notifications de ce compte. Huit flux simultanés au maximum par utilisateur ; la déconnexion ferme le flux ; CSP inchangée (`connect-src` vers l'API uniquement).
* **E-mails** : le mot de passe SMTP ne figure que dans les variables `HIRELINK_MAIL_*` ou dans `application-local.properties` (ignoré par Git) ; le dépôt ne contient que des valeurs modèles. Le contenu dynamique des messages HTML est échappé. Les codes et liens secrets ne sont pas conservés dans la boîte d'envoi, qui est réservée à l'administrateur (aperçu dans une fenêtre isolée `sandbox`) et purgée après 30 jours. Les e-mails de sécurité sont toujours envoyés ; les messages de suivi respectent la préférence de l'utilisateur.
* **Vérification d'entreprise** : modifier l'ICE ou la raison sociale relance la vérification ; les autres champs de la fiche ne modifient jamais le statut.

## 5. Secrets – ce qui était dans le dépôt

Audit de l'historique Git :

| Élément | Constat | Action |
|---|---|---|
| `hirelink.jwt.secret` (valeur de développement en clair, non reproduite ici) | **Était commité** dans `application.properties` depuis le premier commit | Retiré du code ; lu dans `HIRELINK_JWT_SECRET` ou généré. **À considérer comme compromis : ne jamais le réutiliser.** |
| Clés IA Groq / Gemini / Mistral | **Jamais commitées** (seuls des champs vides ou des variables existaient) | Variables `HIRELINK_LLM_API_KEY`, `HIRELINK_GEMINI_API_KEY`, `HIRELINK_GROQ_API_KEY`, `HIRELINK_MISTRAL_API_KEY` |
| Identifiant client Google (`…apps.googleusercontent.com`) | Public par conception (n'est pas un secret) | Surchargeable avec `HIRELINK_GOOGLE_CLIENT_ID` |
| Mot de passe MySQL | Aucun mot de passe réel dans le dépôt (`spring.datasource.password=` est vide pour le développement local) | En prod : `HIRELINK_DB_PASSWORD` |

Noms de variables utilisés : `HIRELINK_JWT_SECRET`, `HIRELINK_CORS_ORIGINS`, `HIRELINK_LLM_API_KEY`, `HIRELINK_GEMINI_API_KEY`, `HIRELINK_GROQ_API_KEY`, `HIRELINK_MISTRAL_API_KEY`, `HIRELINK_GOOGLE_CLIENT_ID`, `HIRELINK_WEBAUTHN_RP_ID`, `HIRELINK_WEBAUTHN_ORIGINS`, `HIRELINK_FRONTEND_URL`, `HIRELINK_ADMIN_EMAIL`, `HIRELINK_ADMIN_PASSWORD`, `HIRELINK_SWAGGER_ENABLED`, et en prod `HIRELINK_DB_URL`, `HIRELINK_DB_USER`, `HIRELINK_DB_PASSWORD`, `HIRELINK_MAIL_*` (HOST, PORT, USERNAME, PASSWORD, FROM).
Modèles : `.env.example` et `backend/src/main/resources/application-local.properties.example`. **L'application démarre sans aucune clé IA** (le scoring retombe sur l'analyse locale).
Les secrets restent visibles dans l'historique Git ancien : si le dépôt a été publié, changer les valeurs et, au besoin, réécrire l'historique (hors périmètre de cette branche).

## 6. Tests exécutés

Tous les tests ont été exécutés sur l'application réellement démarrée (backend Spring Boot + MariaDB 10.11, interface servie par `python -m http.server 5500`, navigateur Chrome piloté automatiquement). Résultat final : **toutes les vérifications ci-dessous sont passées** ; les erreurs trouvées en cours de route (réponse 500 sur valeur trop longue, compteur d'essais OTP perdu lors d'un échec, échecs de connexion non journalisés, mot de passe généré affiché dans les logs, Swagger exposé, reprise de compte par le contournement Google de développement) ont été corrigées avant.

| Domaine | Vérifié |
|---|---|
| Connexion / mot de passe | politique (trop court, liste noire, e-mail dans le mot de passe), réponse identique compte inconnu / mot de passe faux, verrouillage au 6ᵉ échec (429 + `Retry-After`), limites par IP |
| Sessions | rotation du refresh, **réutilisation d'un ancien jeton → session révoquée**, expiration par inactivité et durée absolue (horodatages avancés en base), liste et révocation des sessions, révocation lors du changement/de la réinitialisation du mot de passe et de la suspension, cookie `HttpOnly` + `SameSite=Strict`, CSRF/Origin sur `/auth/refresh` |
| MFA / passkeys | enrôlement TOTP administrateur (QR + clé manuelle), code TOTP rejoué refusé, codes de secours à usage unique, passkey virtuelle dans Chrome (ajout, connexion sans e-mail, suppression), le jeton d'enrôlement/MFA n'est pas un jeton d'accès |
| Ré-authentification | action sensible sans confirmation → 403, avec mot de passe (+ TOTP si activé) → OK, échecs limités |
| Mot de passe oublié | réponse neutre, lien à usage unique, expiration, mot de passe faible refusé, sessions révoquées |
| Contrôle d'accès | par rôle et par organisation (403), responsable de recrutement limité aux candidatures assignées, accès au CV d'un tiers refusé, critères de grille d'une autre organisation refusés, routes d'administration fermées aux autres rôles |
| Entrées / fichiers | tailles maximales (400), injection SQL (pas de 500), fichier de 9 Mo (413), contenu déguisé refusé par signature, SVG refusé, corps JSON de 5 Mo sans 500 |
| En-têtes / CORS | `nosniff`, `X-Frame-Options`, CSP, `Referrer-Policy`, `Permissions-Policy`, `Cache-Control: no-store`, CORS : origine étrangère et `null` refusées, jamais `*`, Swagger/actuator fermés |
| XSS | charge `"><i class=xssprobe></i>` + `onerror` dans noms, offre, lettre, notes d'étape, lieu d'entretien, critères, organisation : **0 élément injecté, 0 erreur console** sur 20 pages (candidat et recruteur) avec la CSP active ; les textes s'affichent échappés |
| Journal d'audit | présence des événements `LOGIN_FAILURE`, `LOGIN_LOCKED`, `PASSWORD_CHANGE`, `SESSION_TOKEN_REUSE`, `STAGE_CHANGE`, `USER_SUSPENDED`, `USER_REACTIVATED`, `DATA_EXPORT`, `ACCOUNT_ERASED` (les autres types sont codés mais non vérifiés un à un) |
| RGPD | export sans hachage ni secret MFA, effacement avec anonymisation, le dernier administrateur ne peut pas s'effacer |
| Profil `prod` | démarrage **refusé** sans `HIRELINK_JWT_SECRET` ; avec secrets : aucune donnée de démo, aucun code OTP ni secret dans les logs, Swagger fermé, seul l'administrateur d'amorçage existe et doit enrôler son TOTP |
| Base de données | refresh stocké en SHA-256, mots de passe en BCrypt, secret TOTP chiffré |

Les rôles ne peuvent pas changer après la création du profil (`403`), donc il n'existe aucun chemin d'élévation de privilège ; à la création du profil, le jeton est réémis avec le nouveau rôle, et `SessionService.revokeAll` est disponible pour toute évolution future (changement de rôle par un administrateur).

## 7. Limites connues

1. **Limitation de débit en mémoire** : les compteurs (verrouillage, IP) sont remis à zéro au redémarrage et ne sont pas partagés entre plusieurs instances. En production multi-instance, utiliser Redis ou un proxy (nginx, Cloudflare).
2. **CSP : `style-src 'unsafe-inline'`** reste autorisé (styles en ligne de l'interface) ; les scripts en ligne, eux, sont interdits.
3. **Jeton d'accès dans `localStorage`** : choix de compromis pour une application à pages statiques ; une faille XSS permettrait de le voler pendant 15 min. Atténuations : CSP stricte, échappement systématique, durée de vie courte, refresh `HttpOnly` non lisible par JavaScript. Une évolution possible est de ne garder le jeton qu'en mémoire.
4. **Base de données testée : MariaDB 10.11** uniquement.
5. **E-mails** : en développement, les codes et liens sont affichés dans la console du serveur (`hirelink.otp.log-to-console`). En prod, un serveur SMTP est obligatoire.
6. **Comptes de démonstration** : leurs anciens mots de passe faibles sont conservés pour la démo ; ils ne sont créés qu'avec `hirelink.seed.enabled=true` (désactivé en prod).
7. Pas d'analyse automatique des dépendances (OWASP Dependency-Check) ni de test d'intrusion externe.
8. Les passkeys exigent `localhost` ou HTTPS ; avec une adresse IP ou un autre nom d'hôte, régler `HIRELINK_WEBAUTHN_RP_ID` et `HIRELINK_WEBAUTHN_ORIGINS`.
