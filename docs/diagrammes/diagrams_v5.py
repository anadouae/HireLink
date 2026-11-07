# -*- coding: utf-8 -*-
"""Sources des diagrammes HireLink v5.

Chaque entree est un source PlantUML fidele au code reel de la plateforme
(24 entites JPA, 21 controlleurs, 27 pages front, 49 requetes lecture seule vertes).
"""

# ---------------------------------------------------------------- cas d'utilisation (3 vues)
# Forme longue refusee par le serveur PlantUML public : un libelle "usecase" contenant
# une sequence backslash-n provoque HTTP 400 ; la forme "usecase ID as \"libelle\"" l'accepte.
CAS_CANDIDAT = """@startuml cas_utilisation_candidat_v5
left to right direction
title HireLink — cas d'utilisation du candidat
actor Candidat as C
rectangle "Espace candidat" {
  usecase UC_consulter as "Consulter les offres publiées"
  usecase UC_filtres as "Filtrer : ville, contrat,\nexpérience, mode de travail"
  usecase UC_vitrine as "Voir la vitrine d'une entreprise"
  usecase UC_profil as "Gérer son profil : compétences,\nCV, disponibilité"
  usecase UC_ocr as "Importer un CV et\nl'analyser (OCR)"
  usecase UC_postuler as "Postuler (assistant en étapes)"
  usecase UC_suivi as "Suivre l'avancement\nde ses candidatures"
  usecase UC_retirer as "Retirer sa candidature"
  usecase UC_reponse as "Répondre à une invitation :\nconfirmer, refuser, proposer un créneau"
  usecase UC_ics as "Ajouter l'entretien au calendrier (.ics)"
  usecase UC_notif as "Lire ses notifications"
  usecase UC_securite as "Sécurité : sessions, MFA, passkey"
  usecase UC_rgpd as "Exporter ses données (RGPD)"
  usecase UC_ia as "Aide IA : réécrire son profil,\npréparer un entretien"
  usecase UC_signaler as "Signaler une offre abusée"
}
C --> UC_consulter
C --> UC_filtres
C --> UC_vitrine
C --> UC_profil
C --> UC_postuler
C --> UC_suivi
C --> UC_retirer
C --> UC_reponse
C --> UC_notif
C --> UC_securite
C --> UC_rgpd
C --> UC_ia
C --> UC_signaler
UC_postuler ..> UC_ocr : <<include>>
UC_postuler ..> UC_profil : <<include>>
UC_reponse ..> UC_ics : <<extend>>
@enduml"""

CAS_RECRUTEUR = """@startuml cas_utilisation_recruteur_v5
left to right direction
title HireLink — cas d'utilisation du recruteur
actor Recruteur as R
actor SYS as "Système de planification\n(rappels, SLA, purges)"
rectangle "Espace recruteur" {
  usecase UC_new as "Créer, modifier, publier une offre"
  usecase UC_close as "Clôturer ou archiver une offre"
  usecase UC_pipeline as "Trier les candidatures\ndans le pipeline"
  usecase UC_refuser as "Refuser avec un motif"
  usecase UC_scorecard as "Shortlist et scorecard"
  usecase UC_planifier as "Planifier un entretien\n(sur site ou visio)"
  usecase UC_reporter as "Reporter ou annuler un entretien"
  usecase UC_agenda as "Consulter l'agenda de l'équipe"
  usecase UC_equipe as "Inviter un membre, promouvoir,\ntransférer la propriété"
  usecase UC_notes as "Note interne et événements\nde l'organisation"
  usecase UC_sla as "Vérifier le rythme de réponse promis"
  usecase UC_credits as "Suivre la consommation des crédits IA"
  usecase UC_recherche as "Rechercher un candidat\ndans son organisation"
}
R --> UC_new
R --> UC_close
R --> UC_pipeline
R --> UC_planifier
R --> UC_reporter
R --> UC_agenda
R --> UC_equipe
R --> UC_notes
R --> UC_sla
R --> UC_credits
R --> UC_recherche
UC_pipeline ..> UC_refuser : <<extend>>
UC_pipeline ..> UC_scorecard : <<extend>>
SYS --> UC_planifier : relance
SYS --> UC_sla : mesure
@enduml"""

CAS_ADMIN = """@startuml cas_utilisation_admin_v5
left to right direction
title HireLink — cas d'utilisation de l'administrateur
actor Administrateur as A
rectangle "Espace administrateur" {
  usecase UC_moderation as "Modérer les offres en quarantaine"
  usecase UC_ice as "Vérifier l'ICE d'une entreprise"
  usecase UC_comptes as "Gérer les comptes : activer,\nsuspendre, inviter"
  usecase UC_orgs as "Gérer les organisations\net leurs logos"
  usecase UC_billing as "Gérer les plans commerciaux\net la facturation"
  usecase UC_motifs as "Gérer les motifs de refus"
  usecase UC_stats as "Statistiques et consommation IA"
  usecase UC_emails as "Consulter les e-mails réellement envoyés"
  usecase UC_search as "Rechercher transversalement\n(candidats, offres, entreprises)"
  usecase UC_retention as "Purger les données mortes (rétention)"
}
A --> UC_moderation
A --> UC_ice
A --> UC_comptes
A --> UC_orgs
A --> UC_billing
A --> UC_motifs
A --> UC_stats
A --> UC_emails
A --> UC_search
A --> UC_retention
@enduml"""

# ---------------------------------------------------------------- classes
CLASSES = """@startuml classes_hirelink_v5
skinparam packageStyle rectangle
skinparam classAttributeIconSize 0
hide circle

package "Identité et accès" {
  class User {
    id: Long
    email: String
    passwordHash: String
    fullName: String
    role: Role
    skills: String
    experienceLevel: String
    birthDate: LocalDate
    availability: String
    cvFileName: String
    cvSections: String
    assignedOrgId: Long
    planTier: PlanTier
    verificationStatus: String
    mfaEnabled: Boolean
    mustSetPassword: Boolean
    emailVerified: Boolean
  }
  class Membership {
    id: Long
    orgRole: OrgRole
    suspendedAt: Instant
  }
  class Organization {
    id: Long
    name: String
    ice: String
    iceFormatValid: Boolean
    iceLegalName: String
    iceCheckedAt: Instant
    logoUrl: String
    sector: String
    city: String
    website: String
    presentation: String
  }
  class OrganizationEvent {
    id: Long
    title: String
    createdBy: User
  }
  class OrganizationNote {
    id: Long
    owner: User
  }
  class OrganizationInvitation {
    id: Long
    email: String
    user: User
  }
  class UserSession {
    id: Long
    tokenHash: String
    idleExpiresAt: Instant
    absoluteExpiresAt: Instant
    revokedAt: Instant
    ip: String
    deviceLabel: String
  }
  class WebAuthnCredential {
    id: Long
    credentialId: String
    publicKeyCose: String
    lastUsedAt: Instant
  }
  class EmailOtp {
    id: Long
    email: String
    purpose: String
    code: String
    expiresAt: Instant
  }
  class PasswordResetToken {
    id: Long
    tokenHash: String
    expiresAt: Instant
    usedAt: Instant
  }
}

package "Recrutement" {
  class JobOffer {
    id: Long
    title: String
    description: String
    location: String
    contractType: ContractType
    workMode: WorkMode
    experienceLevel: ExperienceLevel
    educationLevel: EducationLevel
    compensationType: CompensationType
    salaryMin: Integer
    salaryMax: Integer
    salaryPeriod: PayPeriod
    salaryBasis: PayBasis
    currency: String
    deadline: LocalDate
    responseSlaDays: Integer
    published: Boolean
    suspended: Boolean
    archivedAt: Instant
    closedAt: Instant
    quarantineReason: String
    reportCount: Integer
  }
  class Application {
    id: Long
    status: ApplicationStatus
    matchScore: Double
    coverLetter: String
    cvSource: String
    cvFileName: String
    submittedAt: Instant
    firstDecisionAt: Instant
    responseDeadline: Instant
    interviewType: String
    interviewKind: String
    interviewAt: Instant
    interviewTimeZone: String
    interviewStatus: String
    interviewLocation: String
    interviewVideoUrl: String
    interviewMapUrl: String
    interviewParticipants: String
    interviewNotes: String
    interviewReminderSent: Boolean
    rejectionReason: String
    refusalReasonCode: String
  }
  class ApplicationStatusHistory {
    id: Long
    applicationId: Long
    fromStatus: ApplicationStatus
    toStatus: ApplicationStatus
    actorUserId: Long
    payloadJson: String
  }
  class ScorecardCriterion {
    id: Long
    jobOfferId: Long
    label: String
  }
  class ScorecardEntry {
    id: Long
    applicationId: Long
    criterionId: Long
    evaluatorUserId: Long
    comment: String
  }
  class RefusalReason {
    id: Long
    code: String
    label: String
  }
  class Tag {
    id: Long
    name: String
  }
  class OfferReport {
    id: Long
    jobOfferId: Long
    reporterUserId: Long
    reason: String
    resolvedAt: Instant
  }
}

package "Transverse" {
  class InAppNotification {
    id: Long
    userId: Long
    title: String
    body: String
    link: String
  }
  class SentEmail {
    id: Long
    recipient: String
    subject: String
    kind: String
    status: String
    error: String
  }
  class AuditLog {
    id: Long
    actorUserId: Long
    action: String
    targetType: String
    targetId: Long
    ip: String
  }
  class AiUsageLog {
    id: Long
    userId: Long
    organizationId: Long
    feature: String
    provider: String
    model: String
  }
  class CommercialPlan {
    id: Long
    code: String
    label: String
    audience: String
    features: String
  }
  class BillingInvoice {
    id: Long
    planCode: String
    amount: Integer
    status: String
  }
}

User "1" --o "0..*" JobOffer : publie
User "1" -- "0..*" Application : postule
JobOffer "1" *-- "0..*" Application
Application "1" *-- "0..*" ApplicationStatusHistory
JobOffer "1" o-- "0..*" ScorecardCriterion
Application "1" o-- "0..*" ScorecardEntry
Organization "1" *-- "0..*" Membership
User "1" -- "0..*" Membership : adhère
Organization "1" -- "0..*" OrganizationEvent
Organization "1" -- "0..*" OrganizationNote
Organization "1" -- "0..*" OrganizationInvitation
Organization "1" -- "0..*" BillingInvoice
User "1" *-- "0..*" UserSession
User "1" *-- "0..*" WebAuthnCredential
JobOffer "0..*" o-- "0..*" Tag
User "1" -- "0..*" InAppNotification
JobOffer "1" -- "0..*" OfferReport : signalée
Application "1" -- "0..*" AiUsageLog

note bottom of User
  `role` : Role = CANDIDAT | RECRUTEUR | ADMINISTRATEUR
  `experienceLevel` : vocabulaire du profil
  Stagiaire, Junior, Confirmé, Senior, Lead, Manager
  (vérifié côté serveur dans MeController)
end note

note right of Application
  `status` : SUBMITTED → SCREENING → SHORTLISTED → INTERVIEW,
  REJECTED et WITHDRAWN terminaux.
  OFFER / HIRED = uniquement données historiques.
  Un seul et même pipeline candidat et recruteur.
end note

note bottom of JobOffer
  24 tables d entités créées par Hibernate (ddl-auto=update),
  plus la jointure job_offer_tags : 25 tables au total.
  database/schema.sql ne crée que 4 tables (DDL hérité).
end note
@enduml"""

# ---------------------------------------------------------------- sequence : candidature
SEQ_CANDIDATURE = """@startuml sequence_candidature_v5
autonumber
skinparam ParticipantPadding 12
box "Navigateur" #EFF6FF
  actor "Candidat" as C
  participant "candidate-applications.html\\n+ app/api.js" as F
endbox
box "Backend Spring Boot :8081" #FEF3C7
  participant "JwtAuthFilter" as J
  participant "ApplicationController" as AC
  participant "ApplicationService" as AS
  participant "SkillMatchingService" as SM
  participant "NotificationService" as NS
endbox
database "MariaDB hirelink (25 tables)" as DB

C -> F : saisit lettre + compétences, clique « Postuler »
F -> J : POST /api/v1/applications\\nAuthorization: Bearer <jwt>
J -> J : vérifie signature, session active,\\nexpiration idle/absolue
J -> AC : utilisateur injecté
AC -> AS : create(candidate, request)
AS -> DB : SELECT applications WHERE candidate_id = ? AND job_offer_id = ?
alt déjà candidat
  AS --> AC : ResponseStatusException(CONFLICT)
  AC --> F : 409 « Candidature déjà existante (RM01) »
  F --> C : message en français, formulaire conservé
else première candidature
  AS -> DB : INSERT applications (status = SUBMITTED)
  AS -> SM : score(candidate.skills, offer.requiredSkills)
  SM --> AS : matchScore (0..1)
  AS -> DB : UPDATE applications SET match_score = ?
  AS -> NS : notifie le recruteur + événement organisation
  NS -> DB : INSERT in_app_notification / organization_event
  AS -> DB : INSERT application_status_history (null -> SUBMITTED)
  AS --> AC : ApplicationResponse
  AC --> F : 201 + id, statut, score
  F --> C : « Candidature envoyée »,\\ncompteur et frise rafraîchis
end
@enduml"""

# ---------------------------------------------------------------- sequence : entretien
SEQ_ENTRETIEN = """@startuml sequence_entretien_v5
autonumber
skinparam ParticipantPadding 12
box "Backend" #FEF3C7
  participant "InterviewService" as IV
  participant "ApplicationController" as AC
  participant "InterviewReminderService" as RM
  participant "EventNotifier / MailService" as MAIL
endbox
actor "Recruteur" as R
actor "Candidat" as C
database "MariaDB hirelink" as DB
participant "Calendrier du candidat\\n(Outlook, Google Agenda)" as CAL

== planification ==
R -> AC : PUT /applications/{id}/interview\\n{type: ONSITE|VISIO, kind, startsAt, timeZone, lieu ou lien}
AC -> IV : schedule(app, request)
IV -> IV : type dans {ONSITE, VISIO}, kind connu,\\nfuseau dans Africa/Casablanca|Europe/Paris|Europe/London|UTC
IV -> IV : créneau dans le futur, adresse ou lien obligatoire
IV -> IV : recalcule l'instant avec le fuseau choisi
IV -> DB : UPDATE applications SET interview_* ,\\ninterview_status = SCHEDULED,\\ninterview_candidate_note = null
IV -> MAIL : notification + e-mail au candidat
MAIL -> DB : INSERT sent_email (statut réel de l'envoi)
IV --> R : 200, échéance de réponse affichée

== réponse du candidat ==
C -> AC : POST /applications/{id}/interview/answer\\n{accept | decline | nouvelle date}
AC -> IV : answer(user, app, request)
IV -> IV : vérifie que l'acteur est le candidat concerné
IV -> DB : UPDATE applications SET interview_status = CONFIRMED\\nou CANCELLED / reschedule
IV -> MAIL : notifie l'équipe de recrutement

== rappel et calendrier ==
RM -> DB : SELECT applications WHERE interview_reminder_sent = false\\nAND interview_at dans la fenêtre
RM -> MAIL : rappel J-1 (une seule fois)
RM -> DB : UPDATE interview_reminder_sent = true
C -> AC : GET /applications/{id}/interview.ics
AC -> IV : interviewCalendar(user, id)
alt aucun entretien planifié
  IV --> C : 404 « Aucun entretien planifié »
else entretien actif
  IV --> C : 200 text/calendar (DTSTART en UTC, fuseau d'origine en commentaire)
  C -> CAL : importation du fichier .ics
end
@enduml"""

# ---------------------------------------------------------------- sequence : authentification
SEQ_AUTH = """@startuml sequence_auth_v5
autonumber
actor "Utilisateur" as U
box "Frontend :5500" #EFF6FF
  participant "auth.html" as F
endbox
box "Backend :8081" #FEF3C7
  participant "AuthController" as A
  participant "AuthService" as S
  participant "CaptchaService" as CAP
  participant "SessionService" as SS
  participant "TotpService" as TOTP
endbox
database "MariaDB hirelink" as DB
participant "Google OAuth" as G

U -> F : e-mail + mot de passe
F -> A : POST /auth/login
A -> S : login(request, ClientInfo)
S -> DB : SELECT user + comparaison bcrypt
alt deux échecs ou plus pour cet IP + compte
  S -> CAP : générer une épreuve (image SVG + question)
  CAP --> F : captcha à résoudre
  U -> F : réponse
  F -> A : POST /auth/login {captchaId, captchaSolution}
  A -> CAP : vérifier
end
alt mot de passe erroné
  S --> A : ResponseStatusException(UNAUTHORIZED)
  A --> F : 401 « E-mail ou mot de passe incorrect »
else compte Google / code par e-mail
  S -> G : vérifier id_token
  S -> DB : SELECT email_otp (purpose, code, expires_at)
else MFA actif
  S --> F : 200 {mfaRequired: true}
  U -> F : code à 6 chiffres
  F -> A : POST /auth/login/totp
  A -> TOTP : vérifier TOTP ou code de secours
end
S -> SS : créer user_session (tokenHash, idle et absolute expiration, ip, deviceLabel)
SS -> DB : INSERT user_session
S --> F : AuthResponse {accessToken (JWT), expiresIn}
F -> F : conserve le JWT en mémoire,\\ncookie httpOnly pour le refresh
@enduml"""

# ---------------------------------------------------------------- activite : pipeline
ACTIVITE = """@startuml activite_pipeline_v5
skinparam ActivityDiamondBackgroundColor #FFFFFF
title Cycle de vie d'une candidature (table `applications.status`)

start
:Le candidat postule\\nSUBMITTED;
:Historique : application_status_history\\n(actor = candidat);
if (Le recruteur ouvre le dossier ?) then (tri)
  :SCREENING;
  if (Présélection ?) then (retenue)
    :SHORTLISTED;
    note right
      Scorecard possible à cette étape :
      scorecard_criterion / scorecard_entry
    end note
    if (Entretien ?) then (oui)
      :INTERVIEW;
      :Le recruteur planifie (sur site ou visio)\\ninterview_status = SCHEDULED;
      if (Réponse du candidat avant échéance ?) then (accepte)
        :interview_status = CONFIRMED;
        :Rappel J-1 envoyé une seule fois;
        :L'entretien a lieu\\n(hors plateforme : la décision finale\\nappartient à l'entreprise);
        stop
      elseif (refuse ou propose un créneau) then (reprogrammation)
        :Report : interview_status = SCHEDULED\\navec une nouvelle date;
        detach
      else (silence)
        :Relance, puis dépassement de SLA ;\\nla décision reste au recruteur;
        stop
      endif
    else (refus ici)
      :REJECTED + motif (refusal_reason)\\nfirst_decision_at = maintenant;
      detach
    endif
  else (refus)
    :REJECTED + motif\\nmatchScore conservé;
    detach
  endif
else (candidat se retire)
  :WITHDRAWN;
  detach
endif
note right
  Transition interdites côté serveur :
  - statut terminal (REJECTED, WITHDRAWN) non rejouable ;
  - OFFER et HIRED conservés pour les données
    historiques mais aucune nouvelle transition ;
  - chaque déplacement écrit une ligne
    application_status_history + un audit_log.
end note
@enduml"""

# ---------------------------------------------------------------- paquets
PAQUETS = """@startuml paquets_hirelink_v5
skinparam packageStyle rectangle
title Architecture logique — HireLink (ma.ensat.hirelink)

package "frontend/ (statique, servi sur :5500)" {
  [pages HTML\\nindex, auth, company, checkout,\\nset-password, invitation, help] as HTMLRacine
  [app/*.html\\ncandidat (7) · recruteur (8) ·\\nadmin (1) · sécurité/notifications] as HTMLApp
  [js/ api.js · ui.js ·\\npage-par-page] as JS
  [css/ · assets/ logos locaux] as CSS
}

package "backend/ src/main/java/ma/ensat/hirelink" {
  package "controller (20)" {
    [AuthController, MeController,\\nAccountController] as CAuth
    [JobOfferController,\\nApplicationController,\\nOfferReportController,\\nScorecardController] as CRecruteur
    [OrganizationController,\\nBillingController] as COrg
    [AiAssistController, OcrController,\\nSkillController, MediaController] as CAi
    [AdminController,\\nAdminEmailController,\\nCatalogSuggestController,\\nCatalogPrivacyController,\\nRefusalReasonController,\\nNotificationController,\\nHealthController] as CAdmin
  }
  package "service (51)" {
    [AuthService, SessionService,\\nOtpService, PasswordResetService,\\nCaptchaService, TotpService,\\nWebAuthnService, GoogleTokenService] as SAuth
    [JobOfferService, ApplicationService,\\nInterviewService, ScorecardService,\\nRefusalReasonService, ResponsivenessService] as SRecruteur
    [OrganizationService,\\nOrganizationInvitationService,\\nCompanyVerificationService (ICE),\\nBillingCatalogService,\\nRetentionService] as SOrg
    [HttpLlmProvider, LocalLlmProvider,\\nAiCreditService, LanguageCheckService,\\nSkillMatchingService, OcrService] as SAi
    [NotificationService, EventNotifier,\\nMailService, EmailTemplates,\\nInterviewReminderService,\\nSlaMonitorService, AuditService] as STransverse
  }
  package "repository (24 Spring Data JPA)" as Repo
  package "model (24 entités + 11 enums)" as Model
  package "security" {
    [JwtService, JwtAuthFilter,\\nAuthThrottle, RateLimitFilter,\\nPasswordPolicy, ClientInfo] as Sec
  }
  package "config" {
    [SecurityConfig, DataSeeder,\\nGlobalExceptionHandler,\\nSecurityHeadersFilter] as Conf
  }
  package "dto (records ApiDtos / AuthDtos)" as DTO
}

database "MariaDB `hirelink`\\n3306 · 25 tables" as DB
storage "backend/data/\\ncvs · org-logos · jwt.key" as FS
cloud "API LLM\\nGemini · Groq · Mistral" as LLM
cloud "SMTP (désactivé en local\\npar --spring.mail.host=)" as SMTP

HTMLApp --> JS
JS --> CAuth : REST /api/v1 + Bearer JWT
JS --> CRecruteur
JS --> CAi
JS --> CAdmin
CRecruteur --> SRecruteur
CAuth --> SAuth
CAi --> SAi
CAdmin --> SOrg
CRecruteur --> STransverse
SAuth --> Repo
SRecruteur --> Repo
SOrg --> Repo
SAi --> Repo
SAi --> LLM : clés depuis application-local.properties
STransverse --> SMTP
STransverse --> FS
Repo --> Model
Repo --> DB
Sec --> CRecruteur : filtre la requête
Conf --> Sec
DTO --> CRecruteur
note right of Conf
  GlobalExceptionHandler transforme chaque
  ResponseStatusException en
  {"message": "texte français"} + statut HTTP.
end note
@enduml"""

# ---------------------------------------------------------------- deploiement
DEPLOIEMENT = """@startuml deploiement_hirelink_v5
title Déploiement local de démonstration (poste de l'étudiant)

node "Windows 11 — poste local" {
  node "Navigateur" {
    artifact "Site statique\\nfrontend/ (27 pages)\\nhttp://localhost:5500" as FRONT
  }
  node "JVM 23 — Spring Boot 3.3.13" {
    artifact "hirelink-backend\\nhttp://localhost:8081/api/v1\\n(-Xmx512m, classpath par argfile)" as BACK
    artifact "backend/data/\\ncvs · org-logos · jwt.key" as DATA
  }
  node "MariaDB 10.4 (service `mysql`, XAMPP)" {
    artifact "schéma `hirelink`\\n25 tables · 3306\\nroot@127.0.0.1" as SQL
  }
}

node "Réseau (sorties)" {
  artifact "api.openai.com / generativeai\\ngroq.com / api.mistral.ai\\n(clés : application-local.properties, gitignored)" as LLMS
  artifact "compte de test SMTP\\ninactif en local" as SMTPX
}

FRONT --> BACK : REST + JWT (Authorization)\\nCORS : origine :5500 autorisée
BACK --> SQL : JDBC HikariCP\\nspring.jpa.hibernate.ddl-auto = update
BACK --> DATA : écriture CV et logos
BACK ..> LLMS : appel IA (optionnel,\\nfallback LocalLlmProvider sin clé)
BACK ..> SMTPX : e-mails transactionnels\\n(statut réel dans sent_email)
BACK ..> FRONT : jetons, erreurs en français\\nenvelope {message}

note bottom of BACK
  Démarrage :
  java @backend/hirelink.cp.argfile \\
    ma.ensat.hirelink.HireLinkApplication \\
    --spring.mail.host= \\
    --hirelink.otp.log-to-console=true
  Données de départ : DataSeeder (comptes
  candidat@ / recruteur@ / admin@hirelink.ma
  + organisations et offres de démonstration).
end note
@enduml"""

# ---------------------------------------------------------------- BPMN
BPMN = """@startuml bpmn_processus_recrutement_v5
|-Candidat-|
start
:Consulte les offres publiées\\n(filtres ville, contrat, expérience);
:Prépare son profil\\n(import CV, compétences, disponibilité);
:Postule (lettre + CV);
note right : écriture : applications (SUBMITTED)
|-HireLink (système)-|
:Contrôle anti-doublon\\nune seule candidature par offre;
:Calcule le score de compétences\\net crée la notification recruteur;
|-Recruteur-|
:Trie le dossier\\n(SCREENING);
if (Présélection) then (oui)
  :Met en shortlist\\n(SHORTLISTED);
  if (Entretien) then (oui)
    :Planifie l'entretien\\n(sur site ou visio, fuseau, lieu ou lien);
    |-Candidat-|
    :Reçoit l'invitation\\n(confirme, refuse, propose un créneau);
    if (Réponse avant échéance) then (acceptée)
      |-HireLink (système)-|
      :Rappel J-1 envoyé une seule fois\\n(interview_reminder_sent = true);
      |-Recruteur-|
      :Mène l'entretien et saisit la scorecard;
      :Décide embauche ou refus\\n(la décision finale vit dans l'entreprise);
      |-Candidat-|
      :Ajoute l'entretien au calendrier (.ics)\\net suit l'avancement;
      stop
    else (aucune réponse)
      |-Recruteur-|
      :Constate le dépassement de SLA\\n(sla_breach_notified);
      stop
    endif
  else (refus motivé)
    :Refuse avec un motif\\n(REJECTED, first_decision_at);
    |-Candidat-|
    :Est notifié du refus;
    stop
  endif
else (refus direct)
  :Refuse avec un motif\\n(REJECTED);
  |-Candidat-|
  :Est notifié du refus;
  stop
endif
@enduml"""

# ---------------------------------------------------------------- stack
STACK = """@startuml stack_technologique_v5
title Stack technique de HireLink — couches et outils
skinparam rectangleBorderColor #1a1418

rectangle "Couche présentation" #EFF6FF {
  [HTML5 sémantique\\n27 pages] as H
  [CSS3 variables\\néchelle typographique, badges sobres] as CSS
  [JavaScript natif\\napi.js, ui.js, aucune framework] as JSF
  [SVG locaux : logos ENSA, UAE, HireLink,\\nmascotte, icônes métier] as SVG
}
rectangle "Couche service / API" #FEF3C7 {
  [Java 21+ (exécuté sur JDK 23)] as JAVA
  [Spring Boot 3.3.13\\nWeb, Validation] as SB
  [Spring Security\\nJWT + filtres + @PreAuthorize] as SEC
  [DTO records (ApiDtos, AuthDtos)] as DTO2
  [GlobalExceptionHandler\\nerreurs en français] as GEH
}
rectangle "Couche données" #DCFCE7 {
  [Spring Data JPA\\n24 repositories] as JPA
  [Hibernate ddl-auto = update\\ncrée les 24 tables d'entités] as HB
  [MariaDB 10.4 · schéma `hirelink`\\n25 tables (24 entités\\n+ jointure job_offer_tags)] as MDB
  [database/schema.sql\\n4 tables (DDL hérité)] as SQLF
  [backend/data/ fichiers\\nCV, logos, jwt.key] as FSX
}
rectangle "Capacités externes" #FCE7F3 {
  [IA : Gemini · Groq · Mistral\\nHttpLlmProvider, fallback LocalLlmProvider] as AI
  [OCR : analyse de CV\\nOcrService] as OCR
  [OAuth : GoogleTokenService] as GOOG
  [WebAuthn : passkeys] as WA
  [SMTP (désactivable\\npar --spring.mail.host=)] as MAILX
  [Calendrier : export .ics] as ICS
}
rectangle "Outillage et qualité" #F1F5F9 {
  [Maven (backend),\\nnpm/npx] as MVN
  [Postman + newman\\n73 requêtes, 49 en lecture seule, 101 assertions vertes] as POST
  [python-docx (rapport et cahier des charges)\\nPlantUML (diagrammes)] as PDOC
  [Git · .gitignore\\nbackup-db/, tmp/, *.zip exclus] as GIT
  [DataSeeder : jeux de démonstration\\n3 rôles + 20 organisations] as SEED
}

H --> CSS
CSS --> JSF
JSF --> SB : REST /api/v1
SB --> SEC
SEC --> DTO2
DTO2 --> JPA
JPA --> HB
HB --> MDB
SB --> AI
SB --> OCR
SB --> GOOG
SB --> WA
SB --> MAILX
SB --> ICS
JPA --> FSX
MVN --> SB
POST --> SB
SEED --> MDB
PDOC --> GIT
@enduml"""

DIAGRAMS = {
    "01a_cas_utilisation_candidat": CAS_CANDIDAT,
    "01b_cas_utilisation_recruteur": CAS_RECRUTEUR,
    "01c_cas_utilisation_admin": CAS_ADMIN,
    "02_classes": CLASSES,
    "03_sequence_candidature": SEQ_CANDIDATURE,
    "04_sequence_entretien": SEQ_ENTRETIEN,
    "05_sequence_authentification": SEQ_AUTH,
    "06_activite_pipeline": ACTIVITE,
    "07_paquets": PAQUETS,
    "08_deploiement": DEPLOIEMENT,
    "09_bpmn_processus_recrutement": BPMN,
    "10_stack_technologique": STACK,
}
