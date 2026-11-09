# -*- coding: utf-8 -*-
"""Sections 7 à 10 du cahier des charges v5, et la matrice de traçabilité calculée sur le dépôt.

La section 9 ne recopie pas des numéros de ligne à la main : elle cherche le marqueur
(route REST, message serveur, champ d entité) dans les fichiers du dépôt et retient la
première ligne réellement trouvée. Un marqueur introuvable est affiché comme tel.
"""
import re
import pathlib

REPO = pathlib.Path(r"C:\Users\daurinia\Desktop\HireLink")
BACKEND = REPO / "backend" / "src" / "main" / "java" / "ma" / "ensat" / "hirelink"
FRONTEND = REPO / "frontend"

TABLES = [
    ("users", "User", "Hibernate", "comptes, rôles, profil candidat, MFA, formule"),
    ("organizations", "Organization", "Hibernate", "entreprise, ICE, logo, présentation"),
    ("memberships", "Membership", "Hibernate", "appartenance à une organisation + OrgRole"),
    ("notifications", "InAppNotification", "Hibernate", "notifications internes avec lien profond"),
    ("job_offers", "JobOffer", "Hibernate", "offre, cycle de vie, quarantaine, SLA de réponse"),
    ("applications", "Application", "Hibernate", "candidature, statut, entretien, CV, échéances"),
    ("application_status_history", "ApplicationStatusHistory", "Hibernate", "frise : chaque changement d'étape"),
    ("scorecard_criteria", "ScorecardCriterion", "Hibernate", "critères de décision d'une offre"),
    ("scorecard_entries", "ScorecardEntry", "Hibernate", "note et commentaire par évaluateur"),
    ("refusal_reason_catalog", "RefusalReason", "Hibernate", "catalogue des motifs de refus"),
    ("offer_reports", "OfferReport", "Hibernate", "signalements candidats, seuil de suspension"),
    ("tags", "Tag", "Hibernate", "étiquettes d'offre"),
    ("job_offer_tags", "jointure JobOffer ↔ Tag", "Hibernate", "table de liaison many-to-many"),
    ("organization_events", "OrganizationEvent", "Hibernate", "fil d'événements de l'équipe"),
    ("organization_notes", "OrganizationNote", "Hibernate", "notes internes par organisation"),
    ("organization_invitations", "OrganizationInvitation", "Hibernate", "invitations en attente"),
    ("company_follows", "CompanyFollow", "Hibernate", "candidats qui suivent une entreprise, couple unique"),
    ("billing_invoices", "BillingInvoice", "Hibernate", "factures et avoirs"),
    ("commercial_plans", "CommercialPlan", "Hibernate", "formules : crédits IA et sièges uniquement"),
    ("sent_emails", "SentEmail", "Hibernate", "e-mails réellement envoyés et leur statut"),
    ("email_otps", "EmailOtp", "schema.sql puis Hibernate", "codes de connexion et de vérification"),
    ("password_reset_tokens", "PasswordResetToken", "Hibernate", "jetons de réinitialisation à usage unique"),
    ("user_sessions", "UserSession", "Hibernate", "sessions, révocations, appareils"),
    ("webauthn_credentials", "WebAuthnCredential", "Hibernate", "passkeys"),
    ("audit_logs", "AuditLog", "Hibernate", "journal d'audit des actions sensibles"),
    ("ai_usage_logs", "AiUsageLog", "Hibernate", "consommation IA par fonction et fournisseur"),
]

ENUMS = [
    ("Role", "CANDIDAT, RECRUTEUR, ADMINISTRATEUR", "users.role", "trois espaces, aucune valeur intermédiaire"),
    ("OrgRole", "OWNER, RECRUTEUR, HIRING_MANAGER", "memberships.org_role", "droits d'équipe, distincts du rôle global"),
    ("ApplicationStatus", "SUBMITTED, SCREENING, SHORTLISTED, INTERVIEW, REJECTED, WITHDRAWN "
                          "(+ OFFER, HIRED historiques)", "applications.status", "R01 (transitions), RG-02 (statuts terminaux)"),
    ("ContractType", "CDI, CDD, Stage, PFE, PFA, Stage d'été, Alternance, Freelance, Temps partiel",
     "job_offers.contract_type", "vocabulaire du catalogue"),
    ("WorkMode", "ONSITE, HYBRID, REMOTE", "job_offers.work_mode", "filtre « mode de travail »"),
    ("ExperienceLevel", "NONE, JUNIOR, INTERMEDIATE, SENIOR, EXPERT", "job_offers.experience_level",
     "niveau demandé par l'offre"),
    ("EducationLevel", "BAC, BAC_2, BAC_3, BAC_4, BAC_5, DOCTORATE", "job_offers.education_level", "diplôme demandé"),
    ("CompensationType", "PAID, UNPAID, NEGOTIABLE, UNSPECIFIED", "job_offers.compensation_type", "rémunération"),
    ("PayPeriod / PayBasis", "HOUR, DAY, MONTH, YEAR / GROSS, NET", "job_offers.salary_period, salary_basis",
     "fourchette affichée"),
    ("PlanTier", "ESSENTIEL, PERFORMANCE, ACCES, PREMIUM", "users.plan_tier",
     "change crédits IA et sièges, rien d'autre"),
    ("SentEmail.Status", "SENT, FAILED, LOGGED", "sent_emails.status",
     "statut réel de l'envoi, affiché sans promesse non tenue"),
]

VOCABULAIRES = [
    ("Niveau d'expérience d'un profil", "Stagiaire, Junior, Confirmé, Senior, Lead, Manager",
     "select « pf-level »", "MeController refuse tout autre libellé (RG-09)"),
    ("Fuseau horaire d'un entretien", "Africa/Casablanca, Europe/Paris, Europe/London, UTC",
     "liste ZONES", "InterviewService.schedule (RG-05)"),
    ("Nature d'entretien", "RH, TECHNIQUE, MANAGER, COMPÉTENCES, PROJET, CONTRE-RÉFÉRENCE",
     "KIND_FR", "InterviewService.schedule"),
    ("Type d'entretien", "ONSITE, VISIO", "SCHEDULABLE", "adresse ou lien obligatoire selon le type (RG-04)"),
    ("Motif de refus", "catalogue administrable", "table refusal_reason_catalog",
     "GET /refusal-reasons, géré par l'administrateur"),
    ("Type de notification interne", "APPLICATION, FOLLOW, STATUS, INTERVIEW, SLA, ASSIGN, SYSTEM, "
     "MODERATION, HIRED, INFO", "colonne notifications.kind",
     "NotificationService ; le centre de notifications filtre sur ces types"),
    ("Source d'e-mail", "SECURITY et ACCOUNT obligatoires, APPLICATION et INTERVIEW réglables",
     "MailService.Kind", "un e-mail non obligatoire part seulement si l'interrupteur du compte est actif"),
]

# (référence, ce qui est exigé, marqueur à chercher, dossier de recherche, motif de la preuve)
TRAÇABILITÉ = [
    ("EF-02", "connexion par mot de passe et jeton", '@PostMapping("/login")', BACKEND / "controller"),
    ("EF-01", "inscription par code reçu", '@PostMapping("/verify-register-otp")', BACKEND / "controller"),
    ("EF-06", "double authentification TOTP", '@PostMapping("/mfa/setup")', BACKEND / "controller"),
    ("EF-07", "CAPTCHA après deux échecs", "AuthThrottle", BACKEND / "security"),
    ("EF-10", "sessions actives et révocation", '@GetMapping("/sessions")', BACKEND / "controller"),
    ("EF-11", "export et effacement RGPD", '@PostMapping("/erase")', BACKEND / "controller"),
    ("EF-13 / RG-09", "liste fermée des niveaux d'expérience", "EXPERIENCE_LEVELS", BACKEND / "controller"),
    ("EF-16", "analyse de CV", '@PostMapping(value = "/cv"', BACKEND / "controller" / "OcrController.java"),
    ("EF-21", "liste publique des offres", '@GetMapping("/public")', BACKEND / "controller"),
    ("EF-25", "vitrine publique d'entreprise", '@GetMapping("/public/company")', BACKEND / "controller"),
    ("EF-76 / RG-12", "suivre une entreprise, un seul couple", 'columnNames = {"candidate_user_id", "organization_id"}', BACKEND / "model"),
    ("EF-76", "route de suivi", '@RequestMapping("/api/v1/follows")', BACKEND / "controller"),
    ("EF-78", "notification des followers à la publication", "announceNewOffer", BACKEND / "service"),
    ("EF-79", "compteur des candidats qui suivent", '"followerCount"', BACKEND / "controller"),
    ("EF-80", "flux temps réel SSE", '@GetMapping(value = "/stream"', BACKEND / "controller"),
    ("EF-72", "préparation d'entretien par l'assistant", '@PostMapping("/interview-prep")', BACKEND / "controller"),
    ("EF-73", "grille d'entretien par l'assistant", '@PostMapping("/interview-kit")', BACKEND / "controller"),
    ("EF-74", "synthèse de CV", '@PostMapping("/cv-summary")', BACKEND / "controller"),
    ("EF-75", "coach IA", '@PostMapping("/coach")', BACKEND / "controller"),
    ("EF-27 / RG-10", "signalement et seuil de suspension", "SUSPENDED_BY_REPORTS", BACKEND),
    ("EF-28 / RM01", "anti-doublon de candidature", "Candidature déjà existante", BACKEND),
    ("EF-30", "retrait de candidature", "req.status() == ApplicationStatus.WITHDRAWN", BACKEND / "service"),
    ("EF-31", "frise du dossier", '@GetMapping("/applications/{id}/timeline")', BACKEND / "controller"),
    ("EF-32 / R02", "isolation par organisation", "assertCanAccessApplication", BACKEND / "service"),
    ("EF-34 / R04", "refus avec motif", "applyRefusalCatalog(app, req.rejectionReason())", BACKEND / "service"),
    ("EF-36", "scorecard par critère", '@GetMapping("/offers/{offerId}/scorecard-criteria")', BACKEND / "controller"),
    ("EF-38", "rythme de réponse mesuré", '@GetMapping("/pace")', BACKEND / "controller"),
    ("EF-39 / RG-04", "adresse ou lien obligatoire", "Indiquez le lien de visioconférence", BACKEND / "service"),
    ("EF-41 / RG-05", "fuseaux acceptés", 'ZONES = Set.of', BACKEND / "service"),
    ("EF-42 / RG-07", "report qui remet la réponse à zéro", "app.setInterviewConfirmation(PENDING)",
     BACKEND / "service" / "InterviewService.java"),
    ("EF-45", "export .ics", '@GetMapping("/{id}/interview.ics")', BACKEND / "controller"),
    ("EF-46 / RG-08", "rappel J-1 une seule fois", 'fixedDelayString = "${hirelink.interview.reminder-poll-ms',
     BACKEND / "service" / "InterviewReminderService.java"),
    ("EF-49", "vérification de l'ICE", '@PostMapping("/mine/ice/check")', BACKEND / "controller"),
    ("EF-51", "transfert de propriété", '@PatchMapping("/mine/members/{userId}/ownership")', BACKEND / "controller"),
    ("EF-56", "modération d'offre", '@PostMapping("/offers/{id}/suspend")', BACKEND / "controller"),
    ("EF-59", "plans commerciaux", '@GetMapping("/plans")', BACKEND / "controller"),
    ("EF-62", "e-mails réellement envoyés", '@RequestMapping("/api/v1/admin/emails")', BACKEND / "controller"),
    ("EF-64", "journal d'audit", '@GetMapping("/audit")', BACKEND / "controller"),
    ("EF-65", "notifications internes et badge non lu", '@GetMapping', BACKEND / "controller" / "NotificationController.java"),
    ("EF-66", "lien profond jusqu'à l'élément concerné", 'querySelectorAll("[data-focus]")', FRONTEND / "js"),
    ("RG-11", "message d'erreur unique", "GlobalExceptionHandler", BACKEND / "config"),
    ("EN-18", "compteur de filtre = lignes affichées", "filter-summary", FRONTEND / "js"),
    ("EN-19", "date locale, pas UTC", "class BusinessTime", BACKEND / "service" / "BusinessTime.java"),
    ("R03", "frise réservée aux ayants droit", "Accès historique refusé (R03)", BACKEND / "controller"),
    ("R04", "motif de refus obligatoire", "Choisissez un motif de refus.", BACKEND / "service"),
    ("R05", "checklist à trois points du dossier", "hl-checklist-v2", FRONTEND / "js"),
    ("R11", "retrait réservé au candidat", "Seul le candidat peut retirer (R11)", BACKEND / "service"),
    ("R12", "frise append-only", "Timeline immuable F13 / R12", BACKEND / "model"),
    ("R15", "prompt IA sans donnée d'identité", "R15 : compétences / rôles / niveau uniquement", BACKEND / "controller"),
    ("R16", "crédit IA débité après succès seulement", "R16 : débit seulement si succès", BACKEND / "controller"),
]


def _anchor(marker, root: pathlib.Path):
    """Cherche `marker` dans les fichiers sources et renvoie (chemin relatif, ligne) de la première occurrence."""
    files = [root] if root.is_file() else sorted(root.rglob("*.java")) + sorted(root.rglob("*.html")) \
        + sorted(root.rglob("*.js"))
    for f in files:
        try:
            text = f.read_text(encoding="utf-8")
        except (OSError, UnicodeDecodeError):
            continue
        for i, line in enumerate(text.splitlines(), start=1):
            if marker in line:
                return str(f.relative_to(REPO)).replace("\\", "/"), i
    return None, None


def section_7(doc, h2, table, para, caption, page_break):
    page_break(doc)
    h2(doc, "7. Modèle de données", 1)
    para(doc, "Vingt-six tables dans le schéma `hirelink`. Vingt-cinq correspondent aux entités JPA et sont "
              "créées par Hibernate au premier démarrage (ddl-auto=update) ; la vingt-sixième, "
              "job_offer_tags, est la table de jointure générée par l'annotation ManyToMany. Le fichier "
              "database/schema.sql ne décrit que quatre tables et n'est plus la référence : cette divergence "
              "est assumée et notée ici plutôt que masquée.")
    caption(doc, f"Tableau 7.1 — Les {len(TABLES)} tables, leur entité et ce qu'elles portent")
    table(doc, ["Table", "Entité JPA", "Créée par", "Contenu"],
          [(t, e, c, d) for t, e, c, d in TABLES], col_widths=[4.0, 3.6, 2.6, 6.8], size=8)

    h2(doc, "7.1 Énumérations", 2)
    caption(doc, f"Tableau 7.2 — Les {len(ENUMS)} énumérations du domaine et leur colonne")
    table(doc, ["Enum", "Valeurs", "Colonne", "Règle liée"],
          [(a, b, c, d) for a, b, c, d in ENUMS], col_widths=[3.0, 6.4, 3.6, 4.0], size=8)

    h2(doc, "7.2 Vocabulaires contrôlés", 2)
    para(doc, "Un vocabulaire contrôlé est une liste que le serveur connaît. Là où le front seul filtrait, "
              "la vérification a été déplacée côté serveur : un appel direct à l'API est maintenant soumis à "
              "la même liste qu'un formulaire.")
    caption(doc, "Tableau 7.3 — Listes fermées et endroit où elles sont imposées")
    table(doc, ["Vocabulaire", "Valeurs admises", "Source d'interface", "Contrôle serveur"],
          VOCABULAIRES, col_widths=[3.6, 5.4, 3.0, 5.0], size=8)


def section_8(doc, h2, table, para, caption, page_break):
    page_break(doc)
    h2(doc, "8. Parcours fonctionnels", 1)
    para(doc, "Six parcours complets, chacun avec le point d'observation et la capture correspondante de la "
              "série v5 (docs/guide-captures/v5-2026-11-06). Les comptes indiqués sont ceux semés par "
              "DataSeeder.")
    rows = [
        ("P-01", "Découverte et candidature",
         "candidat@hirelink.ma",
         "Accueil → offres → filtre ville → détail (score 71 %) → Postuler (lettre + CV) → « Candidature "
         "envoyée » → Mes candidatures.",
         "Un second clic sur Postuler renvoie « Candidature déjà existante (RM01) » (RM01).",
         "11-candidat-offres.png, 12-candidat-detail-offre.png, 13-candidat-mes-candidatures.png"),
        ("P-02", "Tri d'un pipeline",
         "recruteur@hirelink.ma",
         "Pipeline → colonne Reçues → ouvrir un dossier → Présélection → Shortlist.",
         "Les quatre compteurs du haut de page bougent exactement du nombre de lignes déplacées (EN-18).",
         "21-recruteur-pipeline.png, 23-recruteur-candidats.png"),
        ("P-03", "Refus motivé",
         "recruteur@hirelink.ma",
         "Dossier → Refuser → choisir un motif → message rédigé → valider.",
         "Sans motif la demande est refusée ; la date de première décision est enregistrée et sert au calcul "
         "du rythme de réponse (R04, EF-38).",
         "21-recruteur-pipeline.png"),
        ("P-04", "Planification d'un entretien",
         "recruteur@hirelink.ma puis candidat@hirelink.ma",
         "Entretiens → planifier (nature, type, date, fuseau, adresse ou lien) → le candidat reçoit "
         "l'invitation → confirme → ajoute l'entretien au calendrier.",
         "Un fuseau hors liste et le serveur répond « Fuseau horaire non pris en charge. » (RG-05) ; un report "
         "remet la réponse du candidat à zéro (RG-07).",
         "24-recruteur-entretiens.png, 15-candidat-notifications.png"),
        ("P-05", "Vie d'équipe",
         "n.bennani@atlasdigital.ma",
         "Entreprise & équipe → inviter un membre → le promouvoir → transférer la propriété → noter un "
         "événement.",
         "Le créateur reste le seul à pouvoir transférer ; chaque action laisse une ligne dans le journal "
         "d'audit (RG-01).",
         "26-recruteur-equipe.png, 25-recruteur-agenda.png"),
        ("P-06", "Modération et pilotage",
         "admin@hirelink.ma",
         "À traiter → offres signalées → rétablir ou suspendre → entreprises (vérification ICE) → comptes → "
         "plans → audit.",
         "La fiche publique d'une entreprise suspendue disparaît de la recherche ; l'audit exporte la décision.",
         "32-admin-moderation.png, 31-admin-entreprises.png, 33-admin-audit.png"),
    ]
    caption(doc, "Tableau 8.1 — Six parcours, du clic à la preuve")
    table(doc, ["Réf", "Parcours", "Compte", "Déroulé", "Ce qui prouve la règle", "Captures"],
          rows, col_widths=[1.0, 2.6, 2.6, 4.2, 4.4, 2.2], size=8)


def section_9(doc, h2, table, para, caption, page_break):
    page_break(doc)
    h2(doc, "9. Matrice de traçabilité", 1)
    para(doc, "Chaque ligne relie une exigence à l'endroit exact du dépôt où elle est écrite. Le chemin et le "
              "numéro de ligne sont produits par une recherche du marqueur dans les sources au moment de la "
              "génération du document : ils ne sont pas recopiés à la main et suivent donc le code. Une ligne "
              "« marqueur introuvable » signifie que la preuve doit être reprise, pas que la fonction "
              "manque forcément.")
    rows = []
    introuvables = 0
    for ref, quoi, marker, root in TRAÇABILITÉ:
        chemin, ligne = _anchor(marker, root)
        if chemin is None:
            introuvables += 1
            rows.append((ref, quoi, "marqueur introuvable : " + marker, "à reprendre"))
        else:
            rows.append((ref, quoi, f"{chemin}:{ligne}", marker))
    caption(doc, f"Tableau 9.1 — {len(TRAÇABILITÉ)} exigences tracées, {introuvables} marqueur(s) à reprendre")
    table(doc, ["Réf", "Exigence", "Fichier:ligne (calculé)", "Marqueur cherché"],
          rows, col_widths=[2.4, 4.6, 6.4, 3.6], size=8)


def section_10(doc, h2, table, para, caption, page_break, bullets):
    page_break(doc)
    h2(doc, "10. Planning, risques, limites et glossaire", 1)

    h2(doc, "10.1 Avancement par séance", 2)
    caption(doc, "Tableau 10.1 — Ce qui a été fait, dans l'ordre des séances")
    table(doc, ["Séance", "Livré", "Vérifié par"],
          [("1", "Produit existant, audit des écrans et des promesses non tenues",
            "relecture page par page, retrait des promesses de visibilité"),
           ("2", "Architecture commentée, contrat front/back, routes et champs",
            "inventaire des 154 endpoints et des 27 pages"),
           ("3", "Rôles d'entreprise, cycle de vie des offres, modération administrateur",
            "tests manuels + collection Postman"),
           ("4", "Crédits IA visibles, CAPTCHA serveur, badges sobres",
            "GET /ai/credits, POST /auth/captcha"),
           ("5", "Nettoyage de la base de démonstration, schéma mort supprimé",
            "26 tables, plus aucune table morte"),
           ("6", "Documentation : cahier des charges, diagrammes, captures, tests",
            "le présent document, docs/diagrammes/v5, docs/postman"),
           ],
          col_widths=[1.2, 8.4, 7.4], size=9)

    h2(doc, "10.2 Risques et réponses", 2)
    caption(doc, "Tableau 10.2 — Risques identifiés, impact et parade déjà en place")
    table(doc, ["Réf", "Risque", "Impact", "Réponse en place"],
          [("RQ-01", "Le mot de passe racine de la base locale sert aussi en production",
            "compromission des données", "profil prod avec variables d'environnement, jamais de mot de passe "
            "dans le dépôt"),
           ("RQ-02", "Les clés d'API IA se retrouvent dans le code",
            "coût et fuite de secret", "application-local.properties gitignored, statut masqué dans "
            "l'interface"),
           ("RQ-03", "Le schéma diverge entre Hibernate et database/schema.sql",
            "base incohérente au déploiement", "documenté en section 7 ; la référence est l'entité JPA"),
           ("RQ-04", "Une règle écrite seulement dans le navigateur est contournable",
            "données corrompues", "déplacement des contrôles côté serveur (RG-05, RG-09)"),
           ("RQ-05", "Le vocabulaire du front et celui du serveur diffèrent",
            "lignes illisibles en base", "liste fermée serveur + message qui rappelle le vocabulaire"),
           ("RQ-06", "Un e-mail promis n'est pas envoyé (SMTP local désactivé)",
            "promesse non tenue", "table sent_emails avec le statut réel, affichage de ce statut"),
           ("RQ-07", "La base de démonstration est abîmée pendant les tests",
            "présentation faussée", "collection lecture seule séparée, aucune écriture métier"),
           ("RQ-08", "Une offre de test reste visible publiquement",
            "crédibilité", "purge systématique après sonde, vérification par requête avant soutenance"),
           ("RQ-09", "Le CAPTCHA bloque une démonstration",
            "écran bloqué", "limitation en mémoire, réinitialisée au redémarrage"),
           ("RQ-10", "Une fonction annoncée n'existe pas",
            "perte de confiance", "section 2.3 « hors périmètre » assumée, matrice de traçabilité section 9"),
           ],
          col_widths=[1.2, 5.2, 3.0, 7.6], size=8)

    h2(doc, "10.3 Limites assumées", 2)
    para(doc, "Ces limites ne sont pas des oublis : elles sont écrites pour que la soutenance ne promette pas "
              "ce que le logiciel ne fait pas.")
    bullets(doc, [
        "HireLink ne remplace pas la décision d'embauche : la promesse finale (contrat, salaire, prise de "
        "poste) vit dans l'entreprise, hors plateforme. Les statuts OFFER et HIRED ne sont plus atteignables.",
        "Le paiement en ligne n'est pas branché : les factures sont un registre administratif, pas un "
        "encaissement.",
        "La vérification d'identité d'entreprise s'appuie sur le format et la cohérence de l'ICE, pas sur un "
        "appel officiel à la DGI.",
        "L'analyse de CV dépend d'un fournisseur d'IA quand une clé est configurée ; sans clé, un repli local "
        "plus simple prend le relais.",
        "Aucune application mobile : l'interface est responsive dans un navigateur.",
        "Le stockage des fichiers est local (backend/data) ; une migration objet n'est pas implémentée.",
    ])

    h2(doc, "10.4 Glossaire", 2)
    caption(doc, "Tableau 10.3 — Termes utilisés dans ce document")
    table(doc, ["Terme", "Sens retenu ici"],
          [("ATS", "Applicant Tracking System : logiciel qui suit les candidatures d'une entreprise."),
           ("ICE", "Identifiant Commun de l'Entreprise marocain, quinze chiffres ; servi par la plateforme "
            "comme preuve d'existence légale."),
           ("Pipeline", "Colonnes d'étapes d'une candidature : Reçues, Présélection, Shortlist, Entretien."),
           ("Scorecard", "Grille de critères et de notes remplie par les évaluateurs après un entretien."),
           ("SLA de réponse", "Délai que l'entreprise s'engage à tenir pour répondre à une candidature ; "
            "mesuré, pas promis."),
           ("Hiring manager", "Responsable du poste, membre de l'équipe qui ne voit que les dossiers qui lui "
            "sont attribués."),
           ("Formule (plan)", "Abonnement : il change le nombre de crédits IA et de sièges, jamais la "
            "visibilité des offres."),
           ("Crédit IA", "Unité de consommation d'un appel à un modèle de langage, comptée par fonction."),
           ("Passkey", "Clé WebAuthn stockée dans le navigateur ou le système, sans mot de passe."),
           ("Totp", "Code à six chiffres à durée limitée, produit par une application d'authentification."),
           ("DataSeeder", "Classe qui remplit la base de démonstration au premier démarrage."),
           ("newman", "Exécuteur en ligne de commande de collections Postman."),
           ],
          col_widths=[3.2, 13.8], size=9)
