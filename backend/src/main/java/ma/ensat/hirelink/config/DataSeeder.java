package ma.ensat.hirelink.config;

import ma.ensat.hirelink.model.*;
import ma.ensat.hirelink.repository.*;
import ma.ensat.hirelink.service.OrganizationService;
import ma.ensat.hirelink.service.SkillMatchingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class DataSeeder {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    @Bean
    CommandLineRunner seedData(
            UserRepository users,
            JobOfferRepository offers,
            ApplicationRepository applications,
            RefusalReasonRepository refusalReasons,
            OrganizationService organizationService,
            PasswordEncoder encoder,
            SkillMatchingService matching
    ) {
        return args -> {
            seedRefusalCatalog(refusalReasons);
            if (users.count() == 0) {
                seedFresh(users, offers, applications, organizationService, encoder, matching);
            } else if (offers.count() < 5) {
                log.info("Enrichissement seed — offres insuffisantes ({})", offers.count());
                enrichIfNeeded(users, offers, applications, matching);
                ensureOrgs(users, organizationService);
            } else {
                ensureOrgs(users, organizationService);
                log.info("Seed OK — {} users, {} offres, {} candidatures, {} motifs",
                        users.count(), offers.count(), applications.count(), refusalReasons.count());
            }
            logAccounts();
        };
    }

    private void seedRefusalCatalog(RefusalReasonRepository repo) {
        String[][] rows = {
                {"PROFILE_MISMATCH", "Profil moins aligné sur les compétences du poste"},
                {"EXPERIENCE_LEVEL", "Niveau d'expérience insuffisant pour ce poste"},
                {"LANGUAGE_GAP", "Compétences linguistiques insuffisantes"},
                {"LOCATION_UNAVAILABLE", "Mobilité / localisation incompatible"},
                {"SALARY_EXPECTATION", "Attentes salariales hors budget"},
                {"PROCESS_WITHDRAW_ORG", "Poste pourvu / process clôturé"},
                {"OTHER_CATALOG", "Autre motif (catalogue)"}
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
        users.findByEmailIgnoreCase("recruteur@hirelink.ma").ifPresent(r -> {
            Organization org = organizationService.ensureForRecruiter(r);
            r.setPlanTier(PlanTier.ACCES);
            r.setAssignedOrgId(org.getId());
            users.save(r);
            users.findByEmailIgnoreCase("cabinet@atlasdigital.ma").ifPresent(r2 -> {
                try {
                    organizationService.invite(r, r2.getEmail(), OrgRole.RECRUTEUR);
                } catch (Exception ignored) {
                    // déjà membre
                }
                r2.setPlanTier(PlanTier.ACCES);
                r2.setAssignedOrgId(org.getId());
                users.save(r2);
            });
        });
        users.findByEmailIgnoreCase("candidat@hirelink.ma").ifPresent(c -> {
            if (c.getPlanTier() == null) {
                c.setPlanTier(PlanTier.ESSENTIEL);
                users.save(c);
            }
        });
    }

    private void seedFresh(
            UserRepository users,
            JobOfferRepository offers,
            ApplicationRepository applications,
            OrganizationService organizationService,
            PasswordEncoder encoder,
            SkillMatchingService matching
    ) {
        User admin = user(users, encoder, "admin@hirelink.ma", "Admin HireLink",
                Role.ADMINISTRATEUR, "Admin123!", false, null, null);

        User recruiter = user(users, encoder, "recruteur@hirelink.ma", "Nadia Recruteuse",
                Role.RECRUTEUR, "Recruteur123!", false, null, "Atlas Digital");
        recruiter.setCompanyIce("001544289000035");
        recruiter.setCompanyVerified(true);
        recruiter.setProfileComplete(true);
        recruiter.setPlanTier(PlanTier.ACCES);
        users.save(recruiter);

        User recruiter2 = user(users, encoder, "cabinet@atlasdigital.ma", "Karim Benali",
                Role.RECRUTEUR, "Recruteur123!", false, null, "Atlas Digital");
        recruiter2.setCompanyIce("001544289000036");
        recruiter2.setCompanyVerified(true);
        recruiter2.setProfileComplete(true);
        recruiter2.setPlanTier(PlanTier.ACCES);
        users.save(recruiter2);

        Organization org = organizationService.ensureForRecruiter(recruiter);
        recruiter.setAssignedOrgId(org.getId());
        users.save(recruiter);
        try {
            organizationService.invite(recruiter, recruiter2.getEmail(), OrgRole.RECRUTEUR);
        } catch (Exception ignored) {}
        recruiter2.setAssignedOrgId(org.getId());
        users.save(recruiter2);

        User candidate = user(users, encoder, "candidat@hirelink.ma", "Sara El Amrani",
                Role.CANDIDAT, "Candidat123!", false,
                "Java,Spring Boot,MySQL,REST,Git,HTML,CSS", null);
        candidate.setSoughtRoles("Développeur Full-Stack,Ingénieur logiciel");
        candidate.setPreferredLocations("Tétouan,Tanger,Hybride,Remote");
        candidate.setPreferredContracts("CDI,Stage");
        candidate.setExperienceLevel("Junior");
        candidate.setProfileComplete(true);
        candidate.setPlanTier(PlanTier.ESSENTIEL);
        users.save(candidate);

        User candidate2 = user(users, encoder, "youssef.data@hirelink.ma", "Youssef Alaoui",
                Role.CANDIDAT, "Candidat123!", false,
                "Python,SQL,ETL,Pandas,Power BI", null);
        candidate2.setSoughtRoles("Data Analyst,Data Engineer");
        candidate2.setPreferredLocations("Tanger,Casablanca");
        candidate2.setPreferredContracts("CDI,Alternance");
        candidate2.setExperienceLevel("Confirmé");
        candidate2.setProfileComplete(true);
        candidate2.setPlanTier(PlanTier.ESSENTIEL);
        users.save(candidate2);

        User candidate3 = user(users, encoder, "amina.qa@hirelink.ma", "Amina Tazi",
                Role.CANDIDAT, "Candidat123!", false,
                "Selenium,JUnit,Postman,Cypress,API testing", null);
        candidate3.setSoughtRoles("QA Engineer,Testeur");
        candidate3.setPreferredLocations("Remote Maroc,Rabat");
        candidate3.setPreferredContracts("Alternance,CDI");
        candidate3.setExperienceLevel("Junior");
        candidate3.setProfileComplete(true);
        candidate3.setPlanTier(PlanTier.ESSENTIEL);
        users.save(candidate3);

        user(users, encoder, "nouveau@hirelink.ma", "Nouveau Candidat",
                Role.CANDIDAT, null, true, "Python,SQL", null);

        JobOffer o1 = offer(offers, recruiter,
                "Développeur Full-Stack Java",
                "Conception d'APIs REST Spring Boot et interfaces web pour un ATS bilatéral.",
                "Java,Spring Boot,MySQL,REST,Git",
                "Tétouan · Hybride", "CDI", 5, true);
        JobOffer o2 = offer(offers, recruiter,
                "Stage PFE — Data & Matching",
                "Amélioration du score d'appariement local (Jaccard) et tableaux de bord SLA.",
                "Python,SQL,ETL,Pandas",
                "Tanger · Présentiel", "Stage", 7, true);
        JobOffer o3 = offer(offers, recruiter,
                "Alternance QA",
                "Tests fonctionnels du pipeline de candidature et non-régression API.",
                "Selenium,JUnit,Postman,Cypress",
                "Remote Maroc", "Alternance", 4, true);
        JobOffer o4 = offer(offers, recruiter2,
                "Ingénieur DevOps junior",
                "CI/CD, Docker, monitoring des services HireLink en staging.",
                "Docker,Linux,CI/CD,Git",
                "Casablanca · Hybride", "CDI", 6, true);
        JobOffer o5 = offer(offers, recruiter,
                "Product Designer UX",
                "Parcours candidat / recruteur, maquettes Figma, design system.",
                "Figma,UX,UI,Design system",
                "Rabat · Remote", "CDI", 5, true);
        JobOffer o6 = offer(offers, recruiter,
                "Recruteur IT (junior)",
                "Sourcing, screening et respect des SLA de réponse candidats.",
                "Recrutement,ATS,Communication",
                "Tétouan", "CDI", 5, false);

        apply(applications, matching, candidate, o1, ApplicationStatus.INTERVIEW);
        apply(applications, matching, candidate, o2, ApplicationStatus.SCREENING);
        apply(applications, matching, candidate, o5, ApplicationStatus.SUBMITTED);
        apply(applications, matching, candidate2, o2, ApplicationStatus.SHORTLISTED);
        apply(applications, matching, candidate2, o4, ApplicationStatus.SUBMITTED);
        apply(applications, matching, candidate3, o3, ApplicationStatus.INTERVIEW);
        apply(applications, matching, candidate3, o1, ApplicationStatus.REJECTED);

        log.info("Seed frais terminé — admin={}, org={}, offres={}, apps={}",
                admin.getEmail(), org.getName(), offers.count(), applications.count());
    }

    private void enrichIfNeeded(
            UserRepository users,
            JobOfferRepository offers,
            ApplicationRepository applications,
            SkillMatchingService matching
    ) {
        User recruiter = users.findByEmailIgnoreCase("recruteur@hirelink.ma").orElse(null);
        User candidate = users.findByEmailIgnoreCase("candidat@hirelink.ma").orElse(null);
        if (recruiter == null || candidate == null) {
            return;
        }
        if (candidate.getSoughtRoles() == null) {
            candidate.setSoughtRoles("Développeur Full-Stack,Ingénieur logiciel");
            candidate.setPreferredLocations("Tétouan,Tanger,Hybride,Remote");
            candidate.setPreferredContracts("CDI,Stage");
            candidate.setExperienceLevel("Junior");
            users.save(candidate);
        }
        while (offers.count() < 5) {
            offer(offers, recruiter,
                    "Offre enrichie #" + (offers.count() + 1),
                    "Offre générée pour compléter le jeu de données démo.",
                    "Java,Spring Boot,MySQL",
                    "Maroc · Hybride", "CDI", 5, true);
        }
        if (applications.count() < 3) {
            offers.findByPublishedTrue().stream().limit(2).forEach(o -> {
                if (applications.findByCandidateIdAndJobOfferId(candidate.getId(), o.getId()).isEmpty()) {
                    apply(applications, matching, candidate, o, ApplicationStatus.SUBMITTED);
                }
            });
        }
    }

    private void logAccounts() {
        log.info("══════════════════════════════════════════════════");
        log.info(" HireLink — comptes MySQL (hirelink)");
        log.info("  admin@hirelink.ma           / Admin123!");
        log.info("  recruteur@hirelink.ma       / Recruteur123!");
        log.info("  cabinet@atlasdigital.ma     / Recruteur123!");
        log.info("  candidat@hirelink.ma        / Candidat123!");
        log.info("  youssef.data@hirelink.ma    / Candidat123!");
        log.info("  amina.qa@hirelink.ma        / Candidat123!");
        log.info("  nouveau@hirelink.ma         / (OTP only)");
        log.info("══════════════════════════════════════════════════");
    }

    private static User user(
            UserRepository repo,
            PasswordEncoder encoder,
            String email,
            String name,
            Role role,
            String rawPassword,
            boolean mustSet,
            String skills,
            String company
    ) {
        User u = new User();
        u.setEmail(email);
        u.setFullName(name);
        u.setRole(role);
        u.setMustSetPassword(mustSet);
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
            JobOfferRepository repo,
            User recruiter,
            String title,
            String description,
            String skills,
            String location,
            String contract,
            int sla,
            boolean published
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
        return repo.save(o);
    }

    private static Application apply(
            ApplicationRepository repo,
            SkillMatchingService matching,
            User candidate,
            JobOffer offer,
            ApplicationStatus status
    ) {
        Application a = new Application();
        a.setCandidate(candidate);
        a.setJobOffer(offer);
        a.setStatus(status);
        a.setMatchScore(matching.computeMatchScore(candidate.getSkills(), offer.getRequiredSkills()));
        if (status == ApplicationStatus.REJECTED) {
            a.setRejectionReason("Profil moins aligné sur les compétences du poste");
            a.setRefusalReasonCode("PROFILE_MISMATCH");
        }
        return repo.save(a);
    }
}
