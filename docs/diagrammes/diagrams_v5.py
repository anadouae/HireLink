# -*- coding: utf-8 -*-
"""Sources des diagrammes HireLink v5.

Chaque entree est un source PlantUML fidele au code reel de la plateforme
(25 entites JPA, 26 tables, 21 controlleurs, 154 endpoints, 27 pages front).
"""

# ---------------------------------------------------------------- cas d'utilisation (une seule vue, tous les acteurs)
# Un libellé "usecase" accepte une vraie fin de ligne ; un libellé "actor" non : HTTP 400.
# C'est pourquoi les étiquettes d'acteurs utilisent la séquence backslash-backslash-n.
CAS_UTILISATION = """@startuml cas_utilisation_hirelink_v6
left to right direction
title HireLink — cas d'utilisation généraux (tous les acteurs)

actor "Visiteur\\nsans compte" as V
actor Candidat as C
actor "Recruteur\\nmembre ou créateur" as R
actor "Responsable du poste\\n(hiring manager)" as H
actor Administrateur as A
actor "Système de planification\\nrappels, SLA, purges" as SYS

rectangle "Espace public" {
  usecase UC_visu_offres as "Consulter les offres publiées"
  usecase UC_visu_entreprise as "Voir la vitrine d'une entreprise"
  usecase UC_compte as "Créer son compte et se connecter\n(mot de passe, code par e-mail, Google, passkey)"
}

rectangle "Espace candidat" {
  usecase UC_filtres as "Filtrer : ville, contrat,\nexpérience, mode de travail"
  usecase UC_profil as "Gérer son profil : compétences,\nCV, disponibilité"
  usecase UC_ocr as "Importer un CV et\nl'analyser (OCR)"
  usecase UC_postuler as "Postuler (assistant en étapes)"
  usecase UC_suivi as "Suivre l'avancement\nde ses candidatures"
  usecase UC_retirer as "Retirer sa candidature"
  usecase UC_reponse as "Répondre à une invitation :\nconfirmer, refuser, proposer un créneau"
  usecase UC_ics as "Ajouter l'entretien au calendrier (.ics)"
  usecase UC_notif as "Lire ses notifications"
  usecase UC_follow as "Suivre une entreprise\net recevoir ses nouvelles offres"
  usecase UC_unfollow as "Ne plus suivre une entreprise"
  usecase UC_securite as "Sécurité : sessions, MFA, passkey"
  usecase UC_rgpd as "Exporter ses données (RGPD)"
  usecase UC_ia as "Aide IA : réécrire son profil,\npréparer un entretien"
  usecase UC_signaler as "Signaler une offre abusée"
}

rectangle "Espace entreprise" {
  usecase UC_new as "Créer, modifier, publier une offre"
  usecase UC_duplicate as "Dupliquer une offre\nou prolonger sa date limite"
  usecase UC_close as "Clôturer ou archiver une offre"
  usecase UC_pipeline as "Trier les candidatures\ndans le pipeline"
  usecase UC_refuser as "Refuser avec un motif"
  usecase UC_scorecard as "Shortlist et scorecard"
  usecase UC_planifier as "Planifier un entretien\n(sur site ou visio)"
  usecase UC_reporter as "Reporter ou annuler un entretien"
  usecase UC_agenda as "Consulter l'agenda de l'équipe"
  usecase UC_equipe as "Inviter un membre, promouvoir,\ntransférer la propriété"
  usecase UC_notes as "Note interne et événements\nde l'organisation"
  usecase UC_sla as "Vérifier son rythme de réponse réel"
  usecase UC_assign as "Attribuer un dossier à un\nresponsable du poste"
  usecase UC_followers as "Voir combien de candidats\nsuivent l'entreprise"
  usecase UC_credits as "Suivre la consommation des crédits IA"
  usecase UC_recherche as "Rechercher un candidat\ndans son organisation"
}

rectangle "Espace responsable du poste" {
  usecase UC_hm_dossiers as "Ne voir que les dossiers\nqui lui sont assignés"
}

rectangle "Espace administrateur" {
  usecase UC_moderation as "Modérer les offres en quarantaine"
  usecase UC_ice as "Vérifier l'ICE d'une entreprise"
  usecase UC_comptes as "Gérer les comptes : activer,\nsuspendre, inviter"
  usecase UC_orgs as "Gérer les organisations\net leurs logos"
  usecase UC_billing as "Gérer les plans commerciaux\net la facturation"
  usecase UC_motifs as "Gérer les motifs de refus"
  usecase UC_stats as "Statistiques et consommation IA"
  usecase UC_emails as "Consulter les e-mails réellement envoyés"
  usecase UC_search_admin as "Rechercher transversalement\n(candidats, offres, entreprises)"
  usecase UC_retention as "Purger les données mortes (rétention)"
  usecase UC_audit as "Consulter et exporter\nle journal d'audit"
}

V --> UC_visu_offres
V --> UC_visu_entreprise
V --> UC_compte

C --> UC_filtres
C --> UC_profil
C --> UC_postuler
C --> UC_suivi
C --> UC_retirer
C --> UC_reponse
C --> UC_notif
C --> UC_follow
C --> UC_unfollow
C --> UC_securite
C --> UC_rgpd
C --> UC_ia
C --> UC_signaler

R --> UC_new
R --> UC_duplicate
R --> UC_close
R --> UC_pipeline
R --> UC_planifier
R --> UC_reporter
R --> UC_agenda
R --> UC_equipe
R --> UC_notes
R --> UC_sla
R --> UC_assign
R --> UC_notif
R --> UC_followers
R --> UC_credits
R --> UC_recherche

H --> UC_hm_dossiers
H --> UC_scorecard
H --> UC_planifier

A --> UC_moderation
A --> UC_ice
A --> UC_comptes
A --> UC_orgs
A --> UC_billing
A --> UC_motifs
A --> UC_stats
A --> UC_emails
A --> UC_search_admin
A --> UC_retention
A --> UC_audit

UC_postuler ..> UC_ocr : <<include>>
UC_postuler ..> UC_profil : <<include>>
UC_reponse ..> UC_ics : <<extend>>
UC_pipeline ..> UC_refuser : <<extend>>
UC_pipeline ..> UC_scorecard : <<extend>>
UC_assign ..> UC_hm_dossiers : <<include>>
SYS --> UC_planifier : relance
SYS --> UC_sla : mesure
SYS --> UC_notif : alerte

' Les trois espaces comptent assez de cas pour depasser une page de rapport.
' Un lien cache avec direction (« -[hidden]right-> ») est ignore par le moteur sur
' une source de cas d'utilisation ; la forme sans direction (« -[hidden]-> ») place
' le second cas dans la colonne suivante et fait passer la vue de 46 lignes a 19.
UC_filtres -[hidden]-> UC_suivi
UC_suivi -[hidden]-> UC_notif
UC_new -[hidden]-> UC_agenda
UC_agenda -[hidden]-> UC_sla
UC_moderation -[hidden]-> UC_stats

note right of UC_follow
  La notification de type « Suivi » est émise par le
  serveur quand l'entreprise suivie publie une offre.
end note

note bottom of UC_equipe
  Seul le créateur de l'espace (OWNER) gère l'équipe
  et la formule ; un membre RECRUTEUR se voit opposer
  un 403 par le serveur.
end note
@enduml"""
CLASSES = """@startuml classes_hirelink_v5
skinparam packageStyle rectangle
skinparam classAttributeIconSize 0
hide circle

package "Identité et accès" {
  class User {
    id: Long
    email: String
    passwordHash: String
    firstName: String
    lastName: String
    fullName: String
    gender: String
    nationality: String
    jobTitle: String
    role: Role
    active: boolean
    emailVerified: boolean
    mustSetPassword: boolean
    googleSub: String
    emailNotifications: Boolean
    skills: String
    soughtRoles: String
    experienceLevel: String
    availability: String
    preferredContracts: String
    preferredLocations: String
    workMode: String
    city: String
    birthDate: LocalDate
    phone: String
    school: String
    educationDomain: String
    graduationYear: Integer
    cvFileName: String
    cvStorageName: String
    cvSections: String
    company: String
    companyIce: String
    companyPresentation: String
    phoneCountry: String
    passwordChangedAt: Instant
    companyVerified: boolean
    verificationStatus: String
    planTier: PlanTier
    profileComplete: boolean
    mfaEnabled: boolean
    mfaSecretEnc: String
    mfaBackupHashes: String
    assignedOrgId: Long
    createdAt: Instant
  }
  class Organization {
    id: Long
    name: String
    ice: String
    iceFormatValid: Boolean
    iceLegalName: String
    iceAddress: String
    iceSource: String
    iceCheckedAt: Instant
    verified: boolean
    verificationStatus: String
    verificationMethod: String
    logoUrl: String
    sector: String
    city: String
    employeeRange: String
    website: String
    presentation: String
    planTier: PlanTier
    createdAt: Instant
  }
  class Membership {
    id: Long
    orgRole: OrgRole
    active: boolean
    invitedAt: Instant
    acceptedAt: Instant
    suspendedAt: Instant
  }
  class CompanyFollow {
    id: Long
    candidateUserId: Long
    organizationId: Long
    createdAt: Instant
  }
  class OrganizationEvent {
    id: Long
    title: String
    description: String
    startsAt: Instant
    durationMinutes: int
    location: String
    videoUrl: String
    category: String
    createdAt: Instant
  }
  class OrganizationNote {
    id: Long
    title: String
    content: String
    inkColor: String
    noteColor: String
    drawingData: String
    positionX: int
    positionY: int
    updatedAt: Instant
  }
  class OrganizationInvitation {
    id: Long
    email: String
    orgRole: OrgRole
    tokenHash: String
    expiresAt: Instant
    acceptedAt: Instant
    revokedAt: Instant
    version: long
  }
  class UserSession {
    id: String
    userId: Long
    tokenHash: String
    previousHash: String
    deviceHash: String
    createdAt: Instant
    lastUsedAt: Instant
    idleExpiresAt: Instant
    absoluteExpiresAt: Instant
    revokedAt: Instant
    revokedReason: String
    ip: String
    userAgent: String
    deviceLabel: String
  }
  class WebAuthnCredential {
    id: Long
    credentialId: String
    publicKeyCose: String
    signatureCount: long
    userHandle: String
    userId: Long
    name: String
    lastUsedAt: Instant
    createdAt: Instant
  }
  class EmailOtp {
    id: Long
    email: String
    purpose: String
    code: String
    attempts: int
    consumed: boolean
    expiresAt: Instant
    createdAt: Instant
  }
  class PasswordResetToken {
    id: Long
    tokenHash: String
    expiresAt: Instant
    usedAt: Instant
    userId: Long
    createdAt: Instant
  }
}

package "Recrutement" {
  class JobOffer {
    id: Long
    title: String
    description: String
    requiredSkills: String
    niceToHaveSkills: String
    location: String
    contractType: String
    workMode: WorkMode
    experienceLevel: ExperienceLevel
    educationLevel: EducationLevel
    compensationType: CompensationType
    salaryMin: Integer
    salaryMax: Integer
    salaryPeriod: PayPeriod
    salaryBasis: PayBasis
    currency: String
    durationMonths: Integer
    startDate: LocalDate
    startAsap: Boolean
    schedule: String
    agreementRequired: Boolean
    hiringPossible: Boolean
    probationMonths: Integer
    positions: Integer
    benefits: String
    deadline: LocalDate
    responseSlaDays: int
    published: boolean
    suspended: boolean
    reportCount: int
    quarantineReason: String
    closedAt: Instant
    archivedAt: Instant
    createdAt: Instant
  }
  class Application {
    id: Long
    status: ApplicationStatus
    matchScore: Double
    coverLetter: String
    cvSource: String
    cvFileName: String
    cvStorageName: String
    cvSections: String
    applicationDetails: String
    advanceNote: String
    rejectionReason: String
    refusalReasonCode: String
    assignedHmUserId: Long
    interviewType: String
    interviewKind: String
    interviewAt: Instant
    interviewDurationMinutes: Integer
    interviewTimeZone: String
    interviewStatus: String
    interviewConfirmation: String
    interviewLocation: String
    interviewVideoUrl: String
    interviewMapUrl: String
    interviewParticipants: String
    interviewNotes: String
    interviewCancelReason: String
    interviewCandidateNote: String
    interviewReminderSent: Boolean
    responseDeadline: Instant
    firstDecisionAt: Instant
    slaBreachNotified: boolean
    submittedAt: Instant
    updatedAt: Instant
  }
  class ApplicationStatusHistory {
    id: Long
    applicationId: Long
    fromStatus: String
    toStatus: String
    actorUserId: Long
    payloadJson: String
    createdAt: Instant
  }
  class ScorecardCriterion {
    id: Long
    jobOfferId: Long
    label: String
    weight: int
    veto: boolean
  }
  class ScorecardEntry {
    id: Long
    applicationId: Long
    criterionId: Long
    evaluatorUserId: Long
    score: int
    comment: String
    createdAt: Instant
  }
  class RefusalReason {
    id: Long
    code: String
    label: String
    active: boolean
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
    status: String
    handledByEmail: String
    resolvedAt: Instant
    createdAt: Instant
  }
}

package "Transverse" {
  class InAppNotification {
    id: Long
    userId: Long
    title: String
    body: String
    kind: String
    link: String
    readFlag: boolean
    createdAt: Instant
  }
  class SentEmail {
    id: Long
    recipient: String
    subject: String
    kind: String
    status: Status
    error: String
    bodyHtml: String
    createdAt: Instant
  }
  class AuditLog {
    id: Long
    actorUserId: Long
    actorEmail: String
    action: String
    targetType: String
    targetId: String
    detail: String
    ip: String
    success: boolean
    createdAt: Instant
  }
  class AiUsageLog {
    id: Long
    userId: Long
    organizationId: Long
    feature: String
    provider: String
    model: String
    charsIn: int
    charsOut: int
    creditsSpent: int
    success: boolean
    createdAt: Instant
  }
  class CommercialPlan {
    id: Long
    code: String
    label: String
    audience: String
    monthlyMad: int
    yearlyMad: int
    credits: int
    features: String
  }
  class BillingInvoice {
    id: Long
    number: String
    planCode: String
    planLabel: String
    cycle: String
    amountHt: double
    tva: double
    amountTtc: double
    status: String
    note: String
    issuedAt: Instant
    cancelsInvoiceId: Long
  }
}

' ---------------------------------------------------------------- associations
' Chaque ligne correspond a une colonne portee par le code (annotation JPA ou
' colonne *_id declaree dans l entite). Le libelle est le verbe metier, la
' cardinalite est ltee de la colonne elle-meme (nullable, unique, cascade).

Organization "1" -- "0..*" Membership : compte les membres
User "1" -- "0..*" Membership : adhère à
User "0..*" -- "0..1" Organization : est possédée par
User "0..*" -- "0..1" Organization : est rattachée à
User "1" -- "0..*" JobOffer : publie
User "1" -- "0..*" Application : dépose
JobOffer "1" -- "0..*" Application : reçoit
Application "1" *-- "0..*" ApplicationStatusHistory : laisse une trace
Application "0..*" -- "0..1" User : est assignée à un responsable
User "1" -- "0..*" ScorecardEntry : évalue
ScorecardCriterion "1" -- "0..*" ScorecardEntry : est notée par
JobOffer "1" *-- "0..*" ScorecardCriterion : définit sa grille
Application "0..*" -- "0..1" RefusalReason : est refusée pour
JobOffer "1" -- "0..*" OfferReport : est signalée
User "1" -- "0..*" OfferReport : signale
JobOffer "0..*" -- "0..*" Tag : est étiquetée
User "1" -- "0..*" CompanyFollow : suit une entreprise
Organization "1" -- "0..*" CompanyFollow : est suivie
Organization "1" -- "0..*" OrganizationEvent : journalise la vie d équipe
User "1" -- "0..*" OrganizationEvent : crée
Organization "1" -- "0..*" OrganizationNote : porte ses post-it
User "1" -- "0..*" OrganizationNote : rédige
Organization "1" -- "0..*" OrganizationInvitation : invite
User "1" -- "0..*" OrganizationInvitation : rattache
Organization "1" -- "0..*" BillingInvoice : est facturée
CommercialPlan "1" -- "0..*" BillingInvoice : porte la formule
BillingInvoice "0..*" -- "0..1" BillingInvoice : est annulée par un avoir
User "1" *-- "0..*" UserSession : ouvre
User "1" -- "0..*" WebAuthnCredential : détient une passkey
User "1" -- "0..*" PasswordResetToken : demande une réinitialisation
User "1" -- "0..*" InAppNotification : reçoit
User "0..*" -- "0..*" AiUsageLog : consomme de l IA
Organization "0..*" -- "0..*" AiUsageLog : consomme de l IA
User "0..*" -- "0..1" AuditLog : commet

note bottom of User
  Colonnes réellement portées : users 45, job_offers 35, applications 35,
  organizations 20. Les attributs affichés sont ceux lus ou écrits par
  l application ; aucun champ de ce diagramme n est inventé.
  `role` : CANDIDAT | RECRUTEUR | ADMINISTRATEUR (Role.java).
  Dans une organisation : OWNER = Créateur, RECRUTEUR = Recruteur,
  HIRING_MANAGER = Responsable du poste (OrgRole.libelleFr).
end note

note right of Application
  `status` : SUBMITTED -> SCREENING -> SHORTLISTED -> INTERVIEW,
  REJECTED et WITHDRAWN terminaux. OFFER / HIRED = données historiques
  seulement, aucune transition nouvelle ne mène à ces deux statuts.
  Les données d entretien sont des colonnes de cette table : un seul
  créneau par dossier, reprogrammable mais non cumulable.
  `refusalReasonCode` est un lien logique vers refusal_reason_catalog.code,
  sans clé étrangère : le motif reste lisible même si l admin le retire.
end note

note bottom of JobOffer
  Une offre n a PAS de colonne organization_id : elle tient son organisation
  par son recruteur (job_offers.recruiter_id -> users -> memberships).
  `contractType` est une String qui porte le libellé de ContractType
  (CDI, CDD, Stage, PFE, PFA, Stage d'été, Alternance, Freelance,
  Temps partiel), pas le nom de l enum.
  25 entités JPA + la jointure job_offer_tags = 26 tables (ddl-auto=update).
  database/schema.sql ne crée que 4 tables (DDL hérité, non référentiel).
end note

note bottom of CompanyFollow
  `company_follows` porte une contrainte unique sur le couple
  (candidate_user_id, organization_id) : un candidat ne peut suivre une
  entreprise qu une fois. C est la source de la notification de type FOLLOW
  envoyée à la publication d une offre, et du compteur « candidats suivent »
  visible par l équipe de recrutement.
end note

note bottom of EmailOtp
  Trois tables sans clé étrangère, volontairement : email_otps (par e-mail),
  sent_emails (par destinataire) et audit_logs (par cible polymorphe
  targetType / targetId). Elles survivent à la suppression du compte.
end note
@enduml
"""

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
database "MariaDB hirelink (26 tables)" as DB

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
  NS -> DB : INSERT notifications / organization_events
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
MAIL -> DB : INSERT sent_emails (statut réel de l'envoi)
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
  S -> DB : SELECT email_otps (purpose, code, expires_at)
else MFA actif
  S --> F : 200 {mfaRequired: true}
  U -> F : code à 6 chiffres
  F -> A : POST /auth/mfa/verify
  A -> TOTP : vérifier TOTP ou code de secours
end
S -> SS : créer user_sessions (tokenHash, idle et absolute expiration, ip, deviceLabel)
SS -> DB : INSERT user_sessions
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
      scorecard_criteria / scorecard_entries
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
      :REJECTED + motif (refusal_reason_code -> refusal_reason_catalog)\\nfirst_decision_at = maintenant;
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
    application_status_history + une ligne audit_logs.
end note
@enduml"""

# ---------------------------------------------------------------- paquets
PAQUETS = """@startuml paquets_hirelink_v5
skinparam packageStyle rectangle
title Architecture logique — HireLink (ma.ensat.hirelink)

package "frontend/ (statique, servi sur :5500)" {
  [pages HTML (9)\\nindex, auth, company, about, help,\\ncheckout, set-password, invitation, security] as HTMLRacine
  [app/*.html (18)\\ncandidat (6) · recruteur (9) ·\\nadmin (1) · security (1) · notifications (1)] as HTMLApp
  [js/ api.js · ui.js ·\\npage-par-page] as JS
  [css/ · assets/ logos locaux] as CSS
}

package "backend/ src/main/java/ma/ensat/hirelink" {
  package "controller (21)" {
    [AuthController, MeController,\\nAccountController] as CAuth
    [JobOfferController,\\nApplicationController,\\nOfferReportController,\\nScorecardController] as CRecruteur
    [OrganizationController,\\nBillingController,\\nCompanyFollowController] as COrg
    [AiAssistController, OcrController,\\nSkillController, MediaController] as CAi
    [AdminController,\\nAdminEmailController,\\nCatalogSuggestController,\\nCatalogPrivacyController,\\nRefusalReasonController,\\nNotificationController,\\nHealthController] as CAdmin
  }
  package "service (52)" {
    [AuthService, SessionService,\\nOtpService, PasswordResetService,\\nCaptchaService, TotpService,\\nWebAuthnService, GoogleTokenService] as SAuth
    [JobOfferService, ApplicationService,\\nInterviewService, ScorecardService,\\nRefusalReasonService, ResponsivenessService] as SRecruteur
    [OrganizationService,\\nOrganizationInvitationService,\\nCompanyVerificationService (ICE),\\nCompanyFollowService,\\nBillingCatalogService,\\nRetentionService] as SOrg
    [HttpLlmProvider, LocalLlmProvider,\\nAiCreditService, LanguageCheckService,\\nSkillMatchingService, OcrService] as SAi
    [NotificationService, EventNotifier,\\nMailService, EmailTemplates,\\nInterviewReminderService,\\nSlaMonitorService, AuditService] as STransverse
  }
  package "repository (25 Spring Data JPA)" as Repo
  package "model (25 entités + 11 enums)" as Model
  package "security" {
    [JwtService, JwtAuthFilter,\\nAuthThrottle, RateLimitFilter,\\nPasswordPolicy, ClientInfo] as Sec
  }
  package "config" {
    [SecurityConfig, DataSeeder,\\nGlobalExceptionHandler,\\nSecurityHeadersFilter] as Conf
  }
  package "dto (records ApiDtos / AuthDtos)" as DTO
}

database "MariaDB `hirelink`\\n3306 · 26 tables" as DB
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
    artifact "schéma `hirelink`\\n26 tables · 3306\\nroot@127.0.0.1" as SQL
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
BACK ..> SMTPX : e-mails transactionnels\\n(statut réel dans sent_emails)
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
  [JavaScript natif\\napi.js, ui.js, aucun framework] as JSF
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
  [Spring Data JPA\\n25 repositories] as JPA
  [Hibernate ddl-auto = update\\ncrée les 25 tables d'entités] as HB
  [MariaDB 10.4 · schéma `hirelink`\\n26 tables (25 entités\\n+ jointure job_offer_tags)] as MDB
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
  [DataSeeder : jeux de démonstration\\ncomptes, organisations, offres, suivis] as SEED
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
    "01_cas_utilisation": CAS_UTILISATION,
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
