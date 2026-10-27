package ma.ensat.hirelink.config;

import ma.ensat.hirelink.model.*;
import ma.ensat.hirelink.repository.*;
import ma.ensat.hirelink.service.CompanyVerificationService;
import ma.ensat.hirelink.service.OrganizationService;
import ma.ensat.hirelink.service.SkillMatchingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Jeu de données opérationnel Maroc — comptes utilisables + pipeline rempli.
 * Pas de placeholders « Offre enrichie #n ».
 */
@Configuration
public class DataSeeder {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    @Value("${hirelink.seed.force-reset:false}")
    private boolean forceReset;

    @Bean
    CommandLineRunner seedData(
            UserRepository users,
            JobOfferRepository offers,
            ApplicationRepository applications,
            RefusalReasonRepository refusalReasons,
            OrganizationService organizationService,
            OrganizationRepository organizations,
            MembershipRepository memberships,
            ApplicationStatusHistoryRepository history,
            MatchingAnalysisRepository matchings,
            InAppNotificationRepository notifications,
            ConsentRecordRepository consents,
            ScorecardCriterionRepository criteria,
            ScorecardEntryRepository scoreEntries,
            AiUsageLogRepository aiLogs,
            PasswordEncoder encoder,
            SkillMatchingService matching,
            CompanyVerificationService companyVerificationService
    ) {
        return args -> {
            seedRefusalCatalog(refusalReasons);

            if (forceReset) {
                log.warn("hirelink.seed.force-reset=true — purge puis rechargement des données");
                scoreEntries.deleteAll();
                criteria.deleteAll();
                aiLogs.deleteAll();
                notifications.deleteAll();
                consents.deleteAll();
                matchings.deleteAll();
                history.deleteAll();
                applications.deleteAll();
                memberships.deleteAll();
                offers.deleteAll();
                organizations.deleteAll();
                users.deleteAll();
            }

            if (users.count() == 0) {
                seedOperational(
                        users, offers, applications, organizationService,
                        history, matchings, notifications, consents,
                        criteria, scoreEntries, aiLogs, encoder, matching
                );
            } else {
                ensureOrgs(users, organizationService);
                log.info("Seed OK — {} users, {} offres, {} candidatures, {} motifs",
                        users.count(), offers.count(), applications.count(), refusalReasons.count());
            }
            ensureMarketplace(users, offers, organizationService, encoder);
            enrichCompanies(organizations, companyVerificationService);
            logAccounts();
        };
    }

    private void seedRefusalCatalog(RefusalReasonRepository repo) {
        String[][] rows = {
                {"PROFILE_MISMATCH", "Compétences techniques insuffisamment alignées sur le poste"},
                {"EXPERIENCE_LEVEL", "Niveau d'expérience inférieur au minimum requis"},
                {"LANGUAGE_GAP", "Niveau de français / anglais insuffisant pour le poste"},
                {"LOCATION_UNAVAILABLE", "Mobilité ou lieu de travail incompatible"},
                {"SALARY_EXPECTATION", "Prétentions salariales hors enveloppe budgétaire"},
                {"PROCESS_WITHDRAW_ORG", "Poste pourvu ou recrutement clôturé"},
                {"OTHER_CATALOG", "Autre motif documenté"}
        };
        for (String[] r : rows) {
            if (repo.findByCode(r[0]).isEmpty()) {
                RefusalReason rr = new RefusalReason();
                rr.setCode(r[0]);
                rr.setLabel(r[1]);
                rr.setActive(true);
                repo.save(rr);
            }
        }
    }

    private void ensureOrgs(UserRepository users, OrganizationService organizationService) {
        users.findByEmailIgnoreCase("n.bennani@atlasdigital.ma").ifPresent(r -> {
            Organization org = organizationService.ensureForRecruiter(r);
            r.setPlanTier(PlanTier.ACCES);
            r.setAssignedOrgId(org.getId());
            users.save(r);
        });
        // rétrocompat anciens emails
        users.findByEmailIgnoreCase("recruteur@hirelink.ma").ifPresent(r -> {
            Organization org = organizationService.ensureForRecruiter(r);
            r.setAssignedOrgId(org.getId());
            users.save(r);
        });
    }

    private void enrichCompanies(OrganizationRepository organizations, CompanyVerificationService verification) {
        for (Organization org : organizations.findAll()) {
            verification.enrichProfile(org);
            if (org.isVerified() && (org.getVerificationMethod() == null || org.getVerificationMethod().isBlank())) {
                org.setVerificationMethod("AUTO_REGISTRY");
            }
            organizations.save(org);
        }
    }

    private void seedOperational(
            UserRepository users,
            JobOfferRepository offers,
            ApplicationRepository applications,
            OrganizationService organizationService,
            ApplicationStatusHistoryRepository history,
            MatchingAnalysisRepository matchings,
            InAppNotificationRepository notifications,
            ConsentRecordRepository consents,
            ScorecardCriterionRepository criteria,
            ScorecardEntryRepository scoreEntries,
            AiUsageLogRepository aiLogs,
            PasswordEncoder encoder,
            SkillMatchingService matching
    ) {
        Instant now = Instant.now();

        // —— Plateforme ——
        User admin = user(users, encoder, "admin@hirelink.ma", "Youssef Cherkaoui",
                Role.ADMINISTRATEUR, "Admin123!", null, null);
        admin.setProfileComplete(true);
        users.save(admin);

        // —— Entreprise : Atlas Digital (Tétouan / Tanger Tech) ——
        User nadia = user(users, encoder, "n.bennani@atlasdigital.ma", "Nadia Bennani",
                Role.RECRUTEUR, "Recruteur123!", null, "Atlas Digital");
        nadia.setCompanyIce("001544289000035");
        nadia.setCompanyVerified(true);
        nadia.setProfileComplete(true);
        nadia.setPlanTier(PlanTier.ACCES);
        users.save(nadia);

        User karim = user(users, encoder, "k.benali@atlasdigital.ma", "Karim Benali",
                Role.RECRUTEUR, "Recruteur123!", null, "Atlas Digital");
        karim.setCompanyIce("001544289000035");
        karim.setCompanyVerified(true);
        karim.setProfileComplete(true);
        karim.setPlanTier(PlanTier.ACCES);
        users.save(karim);

        User hm = user(users, encoder, "m.alaoui@atlasdigital.ma", "Mehdi Alaoui",
                Role.RECRUTEUR, "Recruteur123!", null, "Atlas Digital");
        hm.setCompanyIce("001544289000035");
        hm.setCompanyVerified(true);
        hm.setProfileComplete(true);
        hm.setPlanTier(PlanTier.ACCES);
        users.save(hm);

        Organization atlas = organizationService.ensureForRecruiter(nadia);
        // ensureForRecruiter a déjà nommé l'org depuis company + ICE
        nadia.setAssignedOrgId(atlas.getId());
        users.save(nadia);
        try {
            organizationService.invite(nadia, karim.getEmail(), OrgRole.RECRUTEUR);
        } catch (Exception ignored) {}
        try {
            organizationService.invite(nadia, hm.getEmail(), OrgRole.HIRING_MANAGER);
        } catch (Exception ignored) {}
        karim.setAssignedOrgId(atlas.getId());
        hm.setAssignedOrgId(atlas.getId());
        users.save(karim);
        users.save(hm);

        // —— Candidats ——
        User sara = candidate(users, encoder, "s.elamrani@gmail.com", "Sara El Amrani",
                "Java,Spring Boot,MySQL,REST,Git,React,Docker",
                "Développeuse Full-Stack Java / Spring",
                "Tétouan,Tanger,Hybride", "CDI,Stage", "Junior", PlanTier.ESSENTIEL);
        User youssef = candidate(users, encoder, "y.idrissi@outlook.com", "Youssef Idrissi",
                "Python,SQL,ETL,Pandas,Power BI,Airflow",
                "Data Analyst / Data Engineer",
                "Tanger,Casablanca,Hybride", "CDI", "Confirmé", PlanTier.PERFORMANCE);
        User amina = candidate(users, encoder, "a.tazi@gmail.com", "Amina Tazi",
                "Selenium,JUnit,Postman,Cypress,API testing,Java",
                "Ingénieure QA / Automatisation",
                "Rabat,Remote Maroc", "CDI,Alternance", "Junior", PlanTier.ESSENTIEL);
        User omar = candidate(users, encoder, "o.fassi@gmail.com", "Omar Fassi",
                "Figma,UX Research,UI,Design system,Prototypage",
                "Product Designer UX/UI",
                "Rabat,Casablanca,Remote", "CDI", "Confirmé", PlanTier.ESSENTIEL);
        User imane = candidate(users, encoder, "i.chraibi@gmail.com", "Imane Chraibi",
                "Docker,Kubernetes,Linux,CI/CD,GitLab,AWS",
                "Ingénieure DevOps",
                "Casablanca,Hybride", "CDI", "Junior", PlanTier.ESSENTIEL);

        // rétrocompat : alias emails historiques pointent aussi (comptes miroir pour le guide)
        mirrorAlias(users, encoder, "candidat@hirelink.ma", sara);
        mirrorAlias(users, encoder, "recruteur@hirelink.ma", nadia);

        for (User c : List.of(sara, youssef, amina, omar, imane)) {
            ConsentRecord gdpr = new ConsentRecord();
            gdpr.setUserId(c.getId());
            gdpr.setPurpose("TRAITEMENT_CANDIDATURE");
            gdpr.setGranted(true);
            consents.save(gdpr);
            ConsentRecord market = new ConsentRecord();
            market.setUserId(c.getId());
            market.setPurpose("ALERTES_OFFRES");
            market.setGranted(true);
            consents.save(market);
        }

        // —— Offres ——
        JobOffer oJava = offer(offers, nadia,
                "Développeur Full-Stack Java — produit ATS",
                """
                Atlas Digital recrute un(e) développeur(se) Full-Stack pour renforcer l'équipe produit \
                basée à Tétouan Tech. Vous concevrez des APIs Spring Boot, des écrans React et \
                participerez aux revues de code. Stack : Java 21, Spring Boot 3, MySQL, Git.
                Rémunération selon profil. SLA de réponse candidats : 5 jours ouvrés.
                """.stripIndent().replace("\n", " ").trim(),
                "Java,Spring Boot,MySQL,REST,Git,React",
                "Tétouan · Hybride", "CDI", 5, true, now.minus(18, ChronoUnit.DAYS));

        JobOffer oData = offer(offers, nadia,
                "Data Analyst — matching & reporting recrutement",
                """
                Au sein du pôle data, vous améliorerez les indicateurs de matching compétences / offre \
                et les tableaux de bord SLA. Environnement Python, SQL, Pandas ; collaboration étroite \
                avec les Product Owners de Tanger.
                """.stripIndent().replace("\n", " ").trim(),
                "Python,SQL,ETL,Pandas,Power BI",
                "Tanger · Présentiel 3j/semaine", "CDI", 7, true, now.minus(12, ChronoUnit.DAYS));

        JobOffer oQa = offer(offers, karim,
                "Ingénieur QA — automatisation pipeline recrutement",
                """
                Vous automatisez les parcours critique candidat / recruteur (Selenium, Cypress, Postman) \
                et sécurisez les non-régressions API avant chaque mise en production.
                """.stripIndent().replace("\n", " ").trim(),
                "Selenium,JUnit,Postman,Cypress,API testing",
                "Remote Maroc", "CDI", 4, true, now.minus(9, ChronoUnit.DAYS));

        JobOffer oUx = offer(offers, nadia,
                "Product Designer UX — parcours candidat & recruteur",
                """
                Conception des parcours d'authentification, de candidature guidée et de pipeline Kanban. \
                Maquettes Figma, design system, tests utilisateurs auprès de PME du Nord.
                """.stripIndent().replace("\n", " ").trim(),
                "Figma,UX Research,UI,Design system",
                "Rabat · Remote possible", "CDI", 5, true, now.minus(6, ChronoUnit.DAYS));

        JobOffer oDevops = offer(offers, karim,
                "Ingénieur DevOps junior — CI/CD & staging",
                """
                Mise en place des pipelines GitLab CI, conteneurisation Docker et supervision des \
                environnements de staging HireLink. Première expérience cloud appréciée.
                """.stripIndent().replace("\n", " ").trim(),
                "Docker,Linux,CI/CD,Git,Kubernetes",
                "Casablanca · Hybride", "CDI", 6, true, now.minus(4, ChronoUnit.DAYS));

        JobOffer oDraft = offer(offers, nadia,
                "Chargé(e) de recrutement IT — Nord du Maroc",
                """
                Sourcing développeurs et data, screening téléphonique, respect des SLA de réponse. \
                Offre en préparation (non publiée).
                """.stripIndent().replace("\n", " ").trim(),
                "Recrutement,ATS,Communication,IT",
                "Tétouan", "CDI", 5, false, now.minus(2, ChronoUnit.DAYS));

        // Scorecard sur l'offre Java
        ScorecardCriterion cTech = criterion(criteria, oJava.getId(), "Maîtrise Java / Spring", 3, false);
        ScorecardCriterion cCom = criterion(criteria, oJava.getId(), "Communication & teamwork", 2, false);
        ScorecardCriterion cCult = criterion(criteria, oJava.getId(), "Culture produit / ownership", 2, true);

        // —— Candidatures ——
        Application a1 = apply(applications, matching, sara, oJava, ApplicationStatus.INTERVIEW,
                now.minus(10, ChronoUnit.DAYS),
                "Bonjour,\n\nDiplômée de l'ENSA Tétouan, j'ai réalisé un PFE sur un ATS Spring Boot. "
                        + "Je souhaite rejoindre Atlas Digital pour contribuer au produit recrutement.\n\nCordialement,\nSara El Amrani",
                "CV_Sara_ElAmrani.pdf", "PROFILE");
        a1.setInterviewType("VISIO");
        a1.setInterviewAt(now.plus(3, ChronoUnit.DAYS));
        a1.setInterviewLocation("Google Meet");
        a1.setInterviewNotes("Entretien technique 45 min + culture culture");
        a1.setAssignedHmUserId(hm.getId());
        a1.setAdvanceNote("Bon niveau Spring — à confirmer sur étude de cas");
        applications.save(a1);

        Application a2 = apply(applications, matching, sara, oData, ApplicationStatus.SCREENING,
                now.minus(5, ChronoUnit.DAYS),
                "Intérêt pour le volet data / matching après mon stage analytics.",
                "CV_Sara_ElAmrani.pdf", "PROFILE");

        Application a3 = apply(applications, matching, sara, oUx, ApplicationStatus.SUBMITTED,
                now.minus(2, ChronoUnit.DAYS),
                "Appétence UX en complément du full-stack ; portfolio Figma joint.",
                "CV_Sara_ElAmrani.pdf", "UPLOAD");

        Application a4 = apply(applications, matching, youssef, oData, ApplicationStatus.SHORTLISTED,
                now.minus(8, ChronoUnit.DAYS),
                "3 ans d'expérience data retail à Tanger Free Zone. À l'aise sur ETL et Power BI.",
                "CV_Youssef_Idrissi.pdf", "PROFILE");
        a4.setAssignedHmUserId(hm.getId());
        applications.save(a4);

        Application a5 = apply(applications, matching, youssef, oDevops, ApplicationStatus.SUBMITTED,
                now.minus(3, ChronoUnit.DAYS),
                "Souhait de basculer progressivement vers l'ops data / CI.",
                "CV_Youssef_Idrissi.pdf", "PROFILE");

        Application a6 = apply(applications, matching, amina, oQa, ApplicationStatus.INTERVIEW,
                now.minus(7, ChronoUnit.DAYS),
                "Alternance QA terminée chez un éditeur Rabat ; Cypress & Postman au quotidien.",
                "CV_Amina_Tazi.pdf", "PROFILE");
        a6.setInterviewType("PHONE");
        a6.setInterviewAt(now.plus(1, ChronoUnit.DAYS));
        a6.setInterviewLocation("+212 6 XX XX XX XX");
        a6.setAssignedHmUserId(hm.getId());
        applications.save(a6);

        Application a7 = apply(applications, matching, amina, oJava, ApplicationStatus.REJECTED,
                now.minus(14, ChronoUnit.DAYS),
                "Candidature exploratoire côté développement.",
                "CV_Amina_Tazi.pdf", "PROFILE");
        a7.setRejectionReason("Compétences techniques insuffisamment alignées sur le poste");
        a7.setRefusalReasonCode("PROFILE_MISMATCH");
        applications.save(a7);

        Application a8 = apply(applications, matching, omar, oUx, ApplicationStatus.SCREENING,
                now.minus(4, ChronoUnit.DAYS),
                "Designer produit 4 ans, missions fintech Casablanca. Portfolio : omarfassi.design",
                "CV_Omar_Fassi.pdf", "UPLOAD");

        Application a9 = apply(applications, matching, imane, oDevops, ApplicationStatus.SUBMITTED,
                now.minus(1, ChronoUnit.DAYS),
                "Stage DevOps GitLab CI / Docker chez un intégrateur Casa.",
                "CV_Imane_Chraibi.pdf", "PROFILE");

        // Timeline
        hist(history, a1.getId(), null, "SUBMITTED", sara.getId(), now.minus(10, ChronoUnit.DAYS), null);
        hist(history, a1.getId(), "SUBMITTED", "SCREENING", nadia.getId(), now.minus(8, ChronoUnit.DAYS),
                "{\"note\":\"CV aligné Java/Spring\"}");
        hist(history, a1.getId(), "SCREENING", "SHORTLISTED", karim.getId(), now.minus(6, ChronoUnit.DAYS), null);
        hist(history, a1.getId(), "SHORTLISTED", "INTERVIEW", hm.getId(), now.minus(4, ChronoUnit.DAYS),
                "{\"type\":\"VISIO\"}");

        hist(history, a4.getId(), null, "SUBMITTED", youssef.getId(), now.minus(8, ChronoUnit.DAYS), null);
        hist(history, a4.getId(), "SUBMITTED", "SCREENING", nadia.getId(), now.minus(6, ChronoUnit.DAYS), null);
        hist(history, a4.getId(), "SCREENING", "SHORTLISTED", hm.getId(), now.minus(3, ChronoUnit.DAYS), null);

        hist(history, a7.getId(), null, "SUBMITTED", amina.getId(), now.minus(14, ChronoUnit.DAYS), null);
        hist(history, a7.getId(), "SUBMITTED", "REJECTED", nadia.getId(), now.minus(11, ChronoUnit.DAYS),
                "{\"code\":\"PROFILE_MISMATCH\"}");

        // Matching analyses
        matchRow(matchings, matching, a1, sara, oJava);
        matchRow(matchings, matching, a4, youssef, oData);
        matchRow(matchings, matching, a6, amina, oQa);
        matchRow(matchings, matching, a7, amina, oJava);

        // Scorecards sur entretien Sara
        score(scoreEntries, a1.getId(), cTech.getId(), hm.getId(), 4, "Solide sur Spring Boot et REST");
        score(scoreEntries, a1.getId(), cCom.getId(), hm.getId(), 5, "Clair et structuré");
        score(scoreEntries, a1.getId(), cCult.getId(), nadia.getId(), 4, "Bonne compréhension produit ATS");

        // Notifications
        notif(notifications, sara.getId(), "Entretien planifié",
                "Visio prévue pour Développeur Full-Stack Java — Atlas Digital.", "INTERVIEW", false);
        notif(notifications, sara.getId(), "Candidature reçue",
                "Votre candidature Product Designer a bien été enregistrée.", "APPLICATION", true);
        notif(notifications, nadia.getId(), "Nouvelle candidature",
                "Imane Chraibi a postulé sur Ingénieur DevOps junior.", "APPLICATION", false);
        notif(notifications, youssef.getId(), "Dossier shortlisté",
                "Votre profil Data Analyst est en shortlist chez Atlas Digital.", "PIPELINE", false);
        notif(notifications, amina.getId(), "Décision sur une candidature",
                "Votre candidature Développeur Full-Stack n'a pas été retenue (motif catalogue).", "DECISION", true);

        // AI usage
        AiUsageLog ai1 = new AiUsageLog();
        ai1.setUserId(sara.getId());
        ai1.setOrganizationId(null);
        ai1.setFeature("PREPARE_INTERVIEW");
        ai1.setProvider("local");
        ai1.setModel("heuristic-v1");
        ai1.setCharsIn(420);
        ai1.setCharsOut(980);
        ai1.setCreditsSpent(1);
        ai1.setSuccess(true);
        aiLogs.save(ai1);

        AiUsageLog ai2 = new AiUsageLog();
        ai2.setUserId(nadia.getId());
        ai2.setOrganizationId(atlas.getId());
        ai2.setFeature("DRAFT_REFUSAL");
        ai2.setProvider("local");
        ai2.setModel("heuristic-v1");
        ai2.setCharsIn(210);
        ai2.setCharsOut(540);
        ai2.setCreditsSpent(1);
        ai2.setSuccess(true);
        aiLogs.save(ai2);

        log.info("Données opérationnelles chargées — org={}, offres={}, apps={}",
                atlas.getName(), offers.count(), applications.count());
    }

    private void ensureMarketplace(
            UserRepository users,
            JobOfferRepository offers,
            OrganizationService organizationService,
            PasswordEncoder encoder
    ) {
        if (users.findByEmailIgnoreCase("recrutement.ocpgroup@hirelink.ma").isPresent()) return;
        Instant now = Instant.now();
        String[][] rows = {
                {"OCP Group", "002100000000011", "Casablanca", "Ingénieur procédés junior", "CDI", "Sur site", "Procédés,Chimie,Excel", "7", "Suivi des unités de production et des indicateurs qualité à Jorf Lasfar et au siège."},
                {"OCP Group", "002100000000011", "Khouribga", "Stage géologie", "Stage", "Sur site", "Géologie,Cartographie", "10", "Cartographie de sondages et mise à jour des modèles de gisement."},
                {"Maroc Telecom", "002100000000022", "Rabat", "Ingénieur réseaux", "CDI", "Hybride", "Réseaux,Linux,Cisco", "5", "Exploitation du cœur de réseau et des liaisons fibre."},
                {"Maroc Telecom", "002100000000022", "Casablanca", "Développeur d'applications", "CDI", "Hybride", "Java,Spring Boot,SQL", "7", "Services numériques grand public et outils internes."},
                {"Attijariwafa bank", "002100000000033", "Casablanca", "Analyste crédit entreprises", "CDI", "Sur site", "Finance,Excel,Analyse", "5", "Instruction des dossiers de financement PME."},
                {"Attijariwafa bank", "002100000000033", "Rabat", "Chargé de clientèle", "Premier emploi", "Sur site", "Relation client,Banque", "5", "Accueil, conseil et suivi du portefeuille particuliers."},
                {"Royal Air Maroc", "002100000000044", "Casablanca", "Agent d'escale", "CDI", "Sur site", "Accueil,Anglais,Français", "7", "Enregistrement, embarquement et information passagers à Mohammed V."},
                {"Royal Air Maroc", "002100000000044", "Casablanca", "Stage marketing digital", "Stage", "Hybride", "SEO,Réseaux sociaux", "10", "Campagnes de la saison été et suivi des audiences."},
                {"Renault Group Maroc", "002100000000055", "Tanger", "Ingénieur qualité usine", "CDI", "Sur site", "Qualité,Automobile,ISO", "5", "Contrôle des postes de la ligne de montage à Melloussa."},
                {"Renault Group Maroc", "002100000000055", "Tanger", "Stage logistique", "Stage", "Sur site", "Logistique,Excel", "10", "Flux pièces et ordonnancement des approvisionnements."},
                {"Stellantis", "002100000000066", "Kénitra", "Ingénieur process", "CDI", "Sur site", "Process,Lean,Automobile", "7", "Amélioration des temps de cycle sur l'usine de Kénitra."},
                {"Stellantis", "002100000000066", "Kénitra", "Alternance achats", "Alternance", "Sur site", "Achats,Négociation", "10", "Suivi des fournisseurs locaux de pièces."},
                {"inwi", "002100000000077", "Casablanca", "Développeur mobile", "CDI", "Hybride", "Kotlin,Swift,API", "7", "Parcours client de l'application inwi."},
                {"inwi", "002100000000077", "Rabat", "Ingénieur radio", "CDI", "Sur site", "Télécoms,4G,5G", "7", "Planification radio et qualité de service."},
                {"Intelcia", "002100000000088", "Rabat", "Superviseur centre de contact", "CDI", "Sur site", "Management,Relation client", "5", "Pilotage d'une équipe de conseillers francophones."},
                {"Intelcia", "002100000000088", "Casablanca", "Chargé de recrutement", "CDI", "Hybride", "Recrutement,Entretien", "5", "Volume de recrutement pour les comptes clients."},
                {"Capgemini Maroc", "002100000000099", "Casablanca", "Consultant Java", "CDI", "Hybride", "Java,Spring Boot,SQL", "7", "Missions chez des banques et opérateurs."},
                {"Capgemini Maroc", "002100000000099", "Rabat", "Business analyst", "CDI", "Hybride", "Recueil,UML,Agile", "7", "Cadrage des besoins et rédaction des user stories."},
                {"CGI Maroc", "002100000000101", "Casablanca", "Développeur .NET", "CDI", "Hybride", "C#,.NET,SQL", "7", "Applications métier pour le secteur public et privé."},
                {"CGI Maroc", "002100000000101", "Casablanca", "Testeur logiciel", "Premier emploi", "Hybride", "Postman,JUnit,SQL", "7", "Campagnes de tests fonctionnels et API."},
                {"HPS", "002100000000112", "Casablanca", "Ingénieur paiement", "CDI", "Sur site", "Java,Paiement,SQL", "7", "Évolutions de la plateforme PowerCARD."},
                {"HPS", "002100000000112", "Casablanca", "Product owner", "CDI", "Hybride", "Produit,Agile,Backlog", "7", "Priorisation du backlog d'un module monétique."},
                {"Bank of Africa", "002100000000123", "Casablanca", "Contrôleur de gestion", "CDI", "Sur site", "Finance,Excel,Reporting", "5", "Suivi budgétaire des réseaux d'agences."},
                {"Bank of Africa", "002100000000123", "Fès", "Chargé de conformité", "CDI", "Sur site", "Conformité,Droit", "7", "Contrôles LAB et revue des dossiers agence."},
                {"Marjane", "002100000000134", "Casablanca", "Chef de rayon", "CDI", "Sur site", "Commerce,Management", "5", "Animation du rayon et résultats du magasin."},
                {"Marjane", "002100000000134", "Marrakech", "Stage commerce", "Stage", "Sur site", "Vente,Merchandising", "10", "Mise en rayon et suivi des promotions."},
                {"Decathlon Maroc", "002100000000145", "Casablanca", "Responsable univers", "CDI", "Sur site", "Commerce,Sport,Management", "7", "Pilotage d'un univers sport et de l'équipe magasin."},
                {"Decathlon Maroc", "002100000000145", "Tanger", "Stage vente", "Stage", "Sur site", "Vente,Accueil", "10", "Conseil client sur le magasin de Tanger."},
                {"Cosumar", "002100000000156", "Casablanca", "Ingénieur qualité agroalimentaire", "CDI", "Sur site", "Qualité,HACCP,Agroalimentaire", "7", "Plans de contrôle des sucreries."},
                {"Cosumar", "002100000000156", "Kénitra", "Stage agronomie", "Stage", "Sur site", "Agronomie,Excel", "10", "Suivi des parcelles betteravières."},
                {"Managem", "002100000000167", "Marrakech", "Ingénieur mines", "CDI", "Sur site", "Mines,Géologie,Sécurité", "7", "Exploitation et sécurité d'un site minier."},
                {"Managem", "002100000000167", "Ouarzazate", "Géologue junior", "Premier emploi", "Sur site", "Géologie,Cartographie", "10", "Levés de terrain et échantillonnage."},
                {"ONCF", "002100000000178", "Rabat", "Ingénieur ferroviaire", "CDI", "Sur site", "Ferroviaire,Maintenance", "7", "Maintenance du matériel roulant et des voies."},
                {"ONCF", "002100000000178", "Casablanca", "Stage exploitation", "Stage", "Sur site", "Transport,Excel", "10", "Suivi de la régularité des trains voyageurs."},
                {"Tanger Med", "002100000000189", "Tanger", "Coordinateur logistique", "CDI", "Sur site", "Logistique,Port,Excel", "5", "Coordination des flux entre terminal et transporteurs."},
                {"Tanger Med", "002100000000189", "Tanger", "Stage commerce portuaire", "Stage", "Sur site", "Commerce,Anglais", "10", "Appui aux comptes armateurs."},
                {"Accor", "002100000000190", "Marrakech", "Réceptionniste", "CDI", "Sur site", "Accueil,Français,Anglais", "5", "Accueil des clients d'un hôtel du centre de Marrakech."},
                {"Accor", "002100000000190", "Agadir", "Revenue manager junior", "Premier emploi", "Sur site", "Excel,Yield,Hôtellerie", "7", "Tarifs et occupation de l'établissement."},
                {"Centrale Danone", "002100000000201", "Casablanca", "Ingénieur qualité laitier", "CDI", "Sur site", "Qualité,Agroalimentaire,HACCP", "7", "Contrôles laboratoire et libération des lots."},
                {"Centrale Danone", "002100000000201", "Casablanca", "Chargé de marketing", "CDI", "Hybride", "Marketing,Excel", "7", "Plans d'animation des marques laitières."},
                {"LabelVie", "002100000000212", "Casablanca", "Acheteur alimentaire", "CDI", "Sur site", "Achats,Négociation", "5", "Négociation des familles épicerie et frais."},
                {"Wafa Assurance", "002100000000223", "Casablanca", "Gestionnaire sinistres", "CDI", "Sur site", "Assurance,Relation client", "5", "Instruction des dossiers auto et habitation."},
                {"Wafa Assurance", "002100000000223", "Casablanca", "Actuaire junior", "Premier emploi", "Hybride", "Statistiques,Excel,Python", "7", "Provisionnement et études de portefeuille."},
                {"Alstom Maroc", "002100000000234", "Casablanca", "Ingénieur systèmes ferroviaires", "CDI", "Hybride", "Systèmes,Ferroviaire", "7", "Intégration des sous-systèmes de signalisation."},
                {"Alstom Maroc", "002100000000234", "Rabat", "Stage ingénieur", "Stage", "Sur site", "Ingénierie,Excel", "10", "Appui aux essais d'un projet de matériel roulant."}
        };
        java.util.Map<String, User> owners = new java.util.HashMap<>();
        int i = 0;
        for (String[] row : rows) {
            User owner = owners.get(row[0]);
            if (owner == null) {
                String slug = foldSlug(row[0]);
                owner = user(users, encoder, "recrutement." + slug + "@hirelink.ma", "Recrutement " + row[0],
                        Role.RECRUTEUR, "Recruteur123!", null, row[0]);
                owner.setCompanyIce(row[1]);
                owner.setCompanyVerified(true);
                owner.setCompanyPresentation(row[0] + " recrute au Maroc via HireLink. Les délais de réponse sont affichés sur chaque offre.");
                owner.setPreferredLocations(row[2]);
                owner.setProfileComplete(true);
                owner.setPlanTier(PlanTier.ACCES);
                users.save(owner);
                Organization org = organizationService.ensureForRecruiter(owner);
                owner.setAssignedOrgId(org.getId());
                users.save(owner);
                owners.put(row[0], owner);
            }
            offer(offers, owner, row[3], row[8], row[6], row[2] + " · " + row[5], row[4],
                    Integer.parseInt(row[7]), true, now.minus(i + 2L, ChronoUnit.HOURS));
            i++;
        }
        log.info("Catalogue entreprises chargé — {} offres au total", offers.count());
    }

    private static String foldSlug(String value) {
        String n = java.text.Normalizer.normalize(value, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(java.util.Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "");
        return n.length() > 24 ? n.substring(0, 24) : n;
    }

    /** Compte miroir pour ne pas casser le guide (mêmes MDP, profil lié). */
    private void mirrorAlias(UserRepository users, PasswordEncoder encoder, String email, User source) {
        if (users.findByEmailIgnoreCase(email).isPresent()) {
            return;
        }
        User u = new User();
        u.setEmail(email);
        u.setFullName(source.getFullName() + " (alias)");
        u.setRole(source.getRole());
        u.setPasswordHash(source.getPasswordHash());
        u.setEmailVerified(true);
        u.setActive(true);
        u.setSkills(source.getSkills());
        u.setCompany(source.getCompany());
        u.setCompanyIce(source.getCompanyIce());
        u.setCompanyVerified(source.isCompanyVerified());
        u.setProfileComplete(source.isProfileComplete());
        u.setSoughtRoles(source.getSoughtRoles());
        u.setPreferredLocations(source.getPreferredLocations());
        u.setPreferredContracts(source.getPreferredContracts());
        u.setExperienceLevel(source.getExperienceLevel());
        u.setPlanTier(source.getPlanTier());
        u.setAssignedOrgId(source.getAssignedOrgId());
        users.save(u);
    }

    private User candidate(
            UserRepository users, PasswordEncoder encoder,
            String email, String name, String skills, String sought,
            String locs, String contracts, String level, PlanTier tier
    ) {
        User c = user(users, encoder, email, name, Role.CANDIDAT, "Candidat123!", skills, null);
        c.setSoughtRoles(sought);
        c.setPreferredLocations(locs);
        c.setPreferredContracts(contracts);
        c.setExperienceLevel(level);
        c.setProfileComplete(true);
        c.setPlanTier(tier);
        return users.save(c);
    }

    private static User user(
            UserRepository repo, PasswordEncoder encoder,
            String email, String name, Role role, String rawPassword,
            String skills, String company
    ) {
        User u = new User();
        u.setEmail(email);
        u.setFullName(name);
        u.setRole(role);
        u.setMustSetPassword(false);
        u.setEmailVerified(true);
        u.setActive(true);
        u.setSkills(skills);
        u.setCompany(company);
        if (rawPassword != null) {
            u.setPasswordHash(encoder.encode(rawPassword));
        }
        return repo.save(u);
    }

    private static JobOffer offer(
            JobOfferRepository repo, User recruiter,
            String title, String description, String skills,
            String location, String contract, int sla, boolean published, Instant createdAt
    ) {
        JobOffer o = new JobOffer();
        o.setRecruiter(recruiter);
        o.setTitle(title);
        o.setDescription(description);
        o.setRequiredSkills(skills);
        o.setLocation(location);
        o.setContractType(contract);
        o.setResponseSlaDays(sla);
        o.setPublished(published);
        o.setCreatedAt(createdAt);
        return repo.save(o);
    }

    private static Application apply(
            ApplicationRepository repo, SkillMatchingService matching,
            User candidate, JobOffer offer, ApplicationStatus status, Instant submittedAt,
            String cover, String cvName, String cvSource
    ) {
        Application a = new Application();
        a.setCandidate(candidate);
        a.setJobOffer(offer);
        a.setStatus(status);
        a.setMatchScore(matching.computeMatchScore(candidate.getSkills(), offer.getRequiredSkills()));
        a.setCoverLetter(cover);
        a.setCvFileName(cvName);
        a.setCvSource(cvSource);
        a.setSubmittedAt(submittedAt);
        a.setUpdatedAt(submittedAt);
        return repo.save(a);
    }

    private static void hist(
            ApplicationStatusHistoryRepository repo,
            Long appId, String from, String to, Long actor, Instant at, String payload
    ) {
        ApplicationStatusHistory h = new ApplicationStatusHistory();
        h.setApplicationId(appId);
        h.setFromStatus(from);
        h.setToStatus(to);
        h.setActorUserId(actor);
        h.setPayloadJson(payload);
        h.setCreatedAt(at);
        repo.save(h);
    }

    private static void matchRow(
            MatchingAnalysisRepository repo, SkillMatchingService matching,
            Application app, User candidate, JobOffer offer
    ) {
        MatchingAnalysis m = new MatchingAnalysis();
        m.setApplicationId(app.getId());
        double score = matching.computeMatchScore(candidate.getSkills(), offer.getRequiredSkills());
        m.setScore(score);
        // covered / missing approximés
        m.setCoveredSkills(candidate.getSkills());
        m.setMissingSkills("");
        repo.save(m);
    }

    private static ScorecardCriterion criterion(
            ScorecardCriterionRepository repo, Long offerId, String label, int weight, boolean veto
    ) {
        ScorecardCriterion c = new ScorecardCriterion();
        c.setJobOfferId(offerId);
        c.setLabel(label);
        c.setWeight(weight);
        c.setVeto(veto);
        return repo.save(c);
    }

    private static void score(
            ScorecardEntryRepository repo, Long appId, Long criterionId, Long evaluator, int sc, String comment
    ) {
        ScorecardEntry e = new ScorecardEntry();
        e.setApplicationId(appId);
        e.setCriterionId(criterionId);
        e.setEvaluatorUserId(evaluator);
        e.setScore(sc);
        e.setComment(comment);
        repo.save(e);
    }

    private static void notif(
            InAppNotificationRepository repo, Long userId, String title, String body, String kind, boolean read
    ) {
        InAppNotification n = new InAppNotification();
        n.setUserId(userId);
        n.setTitle(title);
        n.setBody(body);
        n.setKind(kind);
        n.setReadFlag(read);
        repo.save(n);
    }

    private void logAccounts() {
        log.info("══════════════════════════════════════════════════");
        log.info(" HireLink — comptes (base hirelink)");
        log.info("  admin@hirelink.ma              / Admin123!");
        log.info("  n.bennani@atlasdigital.ma      / Recruteur123!   (Owner RH)");
        log.info("  k.benali@atlasdigital.ma       / Recruteur123!");
        log.info("  m.alaoui@atlasdigital.ma       / Recruteur123!   (Hiring Manager)");
        log.info("  s.elamrani@gmail.com           / Candidat123!");
        log.info("  y.idrissi@outlook.com          / Candidat123!");
        log.info("  a.tazi@gmail.com               / Candidat123!");
        log.info("  o.fassi@gmail.com              / Candidat123!");
        log.info("  i.chraibi@gmail.com            / Candidat123!");
        log.info("  (alias) candidat@hirelink.ma   / Candidat123!");
        log.info("  (alias) recruteur@hirelink.ma  / Recruteur123!");
        log.info("══════════════════════════════════════════════════");
    }
}
