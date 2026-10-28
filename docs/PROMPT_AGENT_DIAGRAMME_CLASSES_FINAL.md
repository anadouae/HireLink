# PROMPT AGENT — Diagramme de classes exhaustif HireLink

Copie-colle le bloc ci-dessous tel quel dans ton agent (assistant de code ou de diagrammes).

---

## PROMPT À COPIER

```
Tu es un expert UML / Spring Data JPA. Je travaille sur le projet HireLink (ATS recrutement, Spring Boot 3, package ma.ensat.hirelink).

OBJECTIF
Créer le DIAGRAMME DE CLASSES FINAL ET EXHAUSTIF de l’application (livrable académique clair, lisible, niveau ingénieur).

CONTRAINTES DE SORTIE
1) Générer :
   - un fichier PlantUML (.puml) complet
   - une image PNG haute résolution (ou SVG) propre, fond blanc
   - une légende courte (cardinalités + types de relations JPA)
2) Le diagramme doit être EXHAUSTIF : toutes les entités @Entity et tous les enums du domaine.
3) Afficher pour chaque classe les attributs principaux (id + champs métier importants), pas seulement le nom.
4) Montrer clairement les relations avec cardinalités UML et stéréotypes JPA :
   - @ManyToOne / @OneToMany / @ManyToMany
   - table d’association Membership (User ↔ Organization)
   - table de jointure job_offer_tags (JobOffer ↔ Tag)
5) Organiser par packages / zones visuelles :
   - Identité & org (User, Organization, Membership, Role, OrgRole, PlanTier)
   - Recrutement (JobOffer, Application, ApplicationStatus, ApplicationStatusHistory, Tag)
   - Évaluation (ScorecardCriterion, ScorecardEntry, MatchingAnalysis, RefusalReason)
   - Transversal (InAppNotification, ConsentRecord, EmailOtp, AiUsageLog)
6) Titre du diagramme : « HireLink — Diagramme de classes (modèle JPA final) »
7) NE PAS écrire « Séance 2/3/4 » dans le titre.
8) Style : professionnel, boîtes lisibles, flèches non croisées autant que possible, français OK pour les notes.
9) Enregistrer dans :
   - docs/diagrammes/diagramme_classes_hirelink_FINAL.puml
   - docs/diagrammes/diagramme_classes_hirelink_FINAL.png
10) Après génération, liste en 10 lignes max : entités, relations N-N, et comment Hibernate crée les tables (ddl-auto=update).

MODÈLE À RESPECTER (source de vérité du code actuel)

Entities / tables :
- User (users) : id, email, passwordHash, fullName, role(Role), active, skills, company, companyIce, companyVerified, profileComplete, soughtRoles, preferredLocations, preferredContracts, experienceLevel, planTier(PlanTier), assignedOrgId, createdAt
  Relations: @OneToMany jobOffers (mappedBy recruiter), @OneToMany applications (mappedBy candidate), @OneToMany memberships (mappedBy user)
- Organization (organizations) : id, name, ice, verified, planTier, createdAt
  Relations: @ManyToOne owner (User), @OneToMany memberships
- Membership (memberships) : id, orgRole(OrgRole), active, invitedAt, acceptedAt
  Relations: @ManyToOne organization, @ManyToOne user
  (représente la N-N User↔Organization avec attributs)
- JobOffer (job_offers) : id, title, description, requiredSkills, location, contractType, responseSlaDays, published, createdAt
  Relations: @ManyToOne recruiter (User), @OneToMany applications, @ManyToMany tags (JoinTable job_offer_tags)
- Application (applications) : id, status(ApplicationStatus), matchScore, rejectionReason, cvSource, cvFileName, coverLetter, interview fields, refusalReasonCode, assignedHmUserId, submittedAt, updatedAt
  Relations: @ManyToOne candidate (User), @ManyToOne jobOffer
- Tag (tags) : id, name ; @ManyToMany mappedBy offers
- ApplicationStatusHistory (application_status_history) : id, applicationId, fromStatus, toStatus, actorUserId, payloadJson, createdAt
- MatchingAnalysis (matching_analyses) : id, applicationId, score, coveredSkills, missingSkills, createdAt
- ScorecardCriterion (scorecard_criteria) : id, jobOfferId, label, weight, veto
- ScorecardEntry (scorecard_entries) : id, applicationId, criterionId, evaluatorUserId, score, comment, createdAt
- RefusalReason (refusal_reason_catalog) : id, code, label, active
- InAppNotification (notifications) : id, userId, title, body, kind, readFlag, createdAt
- ConsentRecord (consent_records) : id, userId, purpose, granted, createdAt
- EmailOtp (email_otps) : id, email, code, purpose, expiresAt, consumed, createdAt
- AiUsageLog (ai_usage_logs) : id, userId, organizationId, feature, provider, model, charsIn/Out, creditsSpent, success, createdAt

Enums :
- Role : CANDIDAT, RECRUTEUR, ADMINISTRATEUR
- OrgRole : OWNER, RECRUTEUR, HIRING_MANAGER
- ApplicationStatus : SUBMITTED, SCREENING, SHORTLISTED, INTERVIEW, OFFER, ACCEPTED, REJECTED, WITHDRAWN
- PlanTier : ACCES, ESSENTIEL, PERFORMANCE, PREMIUM

IMPORTANT
- Ne simplifie pas en enlevant des entities.
- Si le diagramme devient dense : utilise des packages UML + notes, mais garde TOUT.
- Vérifie la cohérence des cardinalités avec les annotations JPA ci-dessus.
```

---

## Utilisation
1. Ouvre un nouvel agent sur le projet `HireLink`.
2. Colle le prompt.
3. Vérifie que les 2 fichiers FINAL sont créés dans `docs/diagrammes/`.
4. Remplace éventuellement l’ancien PNG dans ton rapport oral.
