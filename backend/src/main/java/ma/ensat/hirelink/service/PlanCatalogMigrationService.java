package ma.ensat.hirelink.service;

import ma.ensat.hirelink.model.CommercialPlan;
import ma.ensat.hirelink.model.Organization;
import ma.ensat.hirelink.model.PlanTier;
import ma.ensat.hirelink.model.Role;
import ma.ensat.hirelink.model.User;
import ma.ensat.hirelink.repository.CommercialPlanRepository;
import ma.ensat.hirelink.repository.OrganizationRepository;
import ma.ensat.hirelink.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Vague 2 — aligne le catalogue et migre ACCES→ESSENTIEL, PREMIUM→PROFESSIONNEL,
 * ESSENTIEL candidat→GRATUIT. Ne supprime ni utilisateurs ni candidatures.
 * Idempotent. Réservé à l’environnement qui démarre cette application (ISO en recette).
 */
@Service
public class PlanCatalogMigrationService {

    private static final Logger log = LoggerFactory.getLogger(PlanCatalogMigrationService.class);

    private final CommercialPlanRepository plans;
    private final OrganizationRepository organizations;
    private final UserRepository users;

    public PlanCatalogMigrationService(CommercialPlanRepository plans,
                                       OrganizationRepository organizations,
                                       UserRepository users) {
        this.plans = plans;
        this.organizations = organizations;
        this.users = users;
    }

    @Order(50)
    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void onReady() {
        Map<String, Integer> report = migrateAll();
        log.info("Vague 2 catalogue/migration : {}", report);
    }

    @Transactional
    public Map<String, Integer> migrateAll() {
        Map<String, Integer> report = new LinkedHashMap<>();
        report.put("plansUpserted", upsertCatalog());
        report.put("orgsAccesToEssentiel", migrateOrgs(PlanTier.ACCES, PlanTier.ESSENTIEL));
        report.put("orgsPremiumToProfessionnel", migrateOrgs(PlanTier.PREMIUM, PlanTier.PROFESSIONNEL));
        report.put("usersCandidateEssentielToGratuit", migrateCandidateEssentiel());
        report.put("usersAccesAlias", migrateUserAlias(PlanTier.ACCES, PlanTier.ESSENTIEL, Role.RECRUTEUR));
        report.put("usersPremiumAlias", migrateUserAlias(PlanTier.PREMIUM, PlanTier.PROFESSIONNEL, Role.RECRUTEUR));
        return report;
    }

    private int upsertCatalog() {
        int n = 0;
        n += upsert(PlanTier.ESSENTIEL, "Essentiel", "RECRUTEUR", 0, 0, 0,
                "3 offres actives\n1 siège\nPublication et suivi des candidatures\nSans crédits IA inclus");
        n += upsert(PlanTier.PROFESSIONNEL, "Professionnel", "RECRUTEUR", 299, 2990, 100,
                "20 offres actives\n5 sièges\n100 crédits IA / mois\nPaiement simulé TTC");
        n += upsert(PlanTier.ENTREPRISE, "Entreprise", "RECRUTEUR", 699, 6990, 200,
                "Offres actives illimitées (garde-fou technique)\n25 sièges\n200 crédits IA / mois\nPaiement simulé TTC");
        n += upsert(PlanTier.GRATUIT, "Gratuit", "CANDIDAT", 0, 0, 5,
                "Recherche, CV, candidatures et suivi\n5 crédits IA renouvelés tous les 30 jours\nSans plafond de candidatures");
        n += upsert(PlanTier.PERFORMANCE, "Performance", "CANDIDAT", 39, 390, 40,
                "40 crédits IA / mois\nServices IA avancés réellement disponibles\nSans plafond de candidatures");
        // Retire les anciens libellés du catalogue visible sans supprimer l’historique de factures.
        retireLegacy("ACCES");
        retireLegacy("PREMIUM");
        // Ancien code ESSENTIEL était candidat : s’il existe encore comme CANDIDAT, bascule vers GRATUIT déjà upsert.
        plans.findByCode("ESSENTIEL").ifPresent(p -> {
            if ("CANDIDAT".equalsIgnoreCase(p.getAudience())) {
                p.setAudience("RECRUTEUR");
                p.setLabel("Essentiel");
                p.setMonthlyMad(0);
                p.setYearlyMad(0);
                p.setCredits(0);
                p.setFeatures("3 offres actives\n1 siège\nPublication et suivi des candidatures\nSans crédits IA inclus");
                plans.save(p);
            }
        });
        return n;
    }

    private int upsert(PlanTier tier, String label, String audience, int month, int year, int credits, String features) {
        CommercialPlan plan = plans.findByCode(tier.name()).orElseGet(CommercialPlan::new);
        boolean created = plan.getId() == null;
        plan.setCode(tier.name());
        plan.setLabel(label);
        plan.setAudience(audience);
        plan.setMonthlyMad(month);
        plan.setYearlyMad(year > 0 ? year : month * 10);
        plan.setCredits(credits);
        plan.setFeatures(features);
        plans.save(plan);
        return created ? 1 : 0;
    }

    private void retireLegacy(String code) {
        plans.findByCode(code).ifPresent(p -> {
            p.setFeatures((p.getFeatures() == null ? "" : p.getFeatures() + "\n")
                    + "[RETIRÉ Vague 2 — alias migré, ne plus proposer à la vente]");
            p.setMonthlyMad(0);
            p.setYearlyMad(0);
            plans.save(p);
        });
    }

    private int migrateOrgs(PlanTier from, PlanTier to) {
        int n = 0;
        for (Organization org : organizations.findAll()) {
            if (org.getPlanTier() == from) {
                org.setPlanTier(to);
                organizations.save(org);
                n++;
            }
        }
        return n;
    }

    private int migrateCandidateEssentiel() {
        int n = 0;
        List<User> all = users.findAll();
        for (User u : all) {
            if (u.getRole() == Role.CANDIDAT && u.getPlanTier() == PlanTier.ESSENTIEL) {
                u.setPlanTier(PlanTier.GRATUIT);
                users.save(u);
                n++;
            }
        }
        return n;
    }

    private int migrateUserAlias(PlanTier from, PlanTier to, Role role) {
        int n = 0;
        for (User u : users.findAll()) {
            if (u.getRole() == role && u.getPlanTier() == from) {
                u.setPlanTier(to);
                users.save(u);
                n++;
            }
        }
        return n;
    }
}
