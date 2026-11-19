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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Catalogue des formules au démarrage (idempotent) :
 * <ul>
 *   <li>crée les formules manquantes avec leurs valeurs par défaut ;</li>
 *   <li>ne réécrase jamais une formule existante (prix, crédits, libellé et lignes modifiés depuis
 *       l'administration sont conservés) ;</li>
 *   <li>nettoie les anciennes lignes techniques (marqueurs de retrait, mentions internes) ;</li>
 *   <li>migre les alias ACCES→ESSENTIEL, PREMIUM→PROFESSIONNEL et l'ancien ESSENTIEL candidat→GRATUIT.</li>
 * </ul>
 * Les lignes ACCES et PREMIUM restent en base (historique des factures) mais ne sont plus proposées :
 * voir {@link BillingCatalogService#catalog()}.
 */
@Service
public class PlanCatalogMigrationService {

    private static final Logger log = LoggerFactory.getLogger(PlanCatalogMigrationService.class);

    private final CommercialPlanRepository plans;
    private final OrganizationRepository organizations;
    private final UserRepository users;

    @Value("${hirelink.startup-migrations.enabled:false}")
    private boolean startupMigrationsEnabled;

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
        if (!startupMigrationsEnabled) {
            log.info("Migration du catalogue au démarrage désactivée.");
            return;
        }
        Map<String, Integer> report = migrateAll();
        log.info("Catalogue des formules vérifié : {}", report);
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

    static final String ESSENTIEL_FEATURES =
            "3 offres actives\n1 siège\nPublication et suivi des candidatures\nSans crédits IA inclus";
    static final String PROFESSIONNEL_FEATURES = "20 offres actives\n5 sièges\n100 crédits IA / mois";
    static final String ENTREPRISE_FEATURES = "Offres actives illimitées\n25 sièges\n200 crédits IA / mois";
    static final String GRATUIT_FEATURES =
            "Recherche, CV, candidatures et suivi\n5 crédits IA renouvelés tous les 30 jours\nSans plafond de candidatures";
    static final String PERFORMANCE_FEATURES =
            "40 crédits IA / mois\nOutils IA avancés pour le CV et les entretiens\nSans plafond de candidatures";

    private int upsertCatalog() {
        int n = 0;
        n += ensure(PlanTier.ESSENTIEL, "Essentiel", "RECRUTEUR", 0, 0, 0, ESSENTIEL_FEATURES);
        n += ensure(PlanTier.PROFESSIONNEL, "Professionnel", "RECRUTEUR", 299, 2990, 100, PROFESSIONNEL_FEATURES);
        n += ensure(PlanTier.ENTREPRISE, "Entreprise", "RECRUTEUR", 699, 6990, 200, ENTREPRISE_FEATURES);
        n += ensure(PlanTier.GRATUIT, "Gratuit", "CANDIDAT", 0, 0, 5, GRATUIT_FEATURES);
        n += ensure(PlanTier.PERFORMANCE, "Performance", "CANDIDAT", 39, 390, 40, PERFORMANCE_FEATURES);
        // Anciennes formules : conservées pour l'historique des factures, jamais modifiées ligne à ligne.
        retireLegacy("ACCES");
        retireLegacy("PREMIUM");
        // Ancien code ESSENTIEL était candidat : s’il existe encore comme CANDIDAT, il redevient la formule entreprise.
        plans.findByCode("ESSENTIEL").ifPresent(p -> {
            if ("CANDIDAT".equalsIgnoreCase(p.getAudience())) {
                p.setAudience("RECRUTEUR");
                p.setLabel("Essentiel");
                p.setMonthlyMad(0);
                p.setYearlyMad(0);
                p.setCredits(0);
                p.setFeatures(ESSENTIEL_FEATURES);
                plans.save(p);
            }
        });
        return n;
    }

    /** Crée la formule si elle manque ; sinon ne fait que nettoyer ses lignes. Retourne 1 si créée. */
    private int ensure(PlanTier tier, String label, String audience, int month, int year, int credits, String features) {
        CommercialPlan plan = plans.findByCode(tier.name()).orElse(null);
        if (plan == null) {
            plan = new CommercialPlan();
            plan.setCode(tier.name());
            plan.setLabel(label);
            plan.setAudience(audience);
            plan.setMonthlyMad(month);
            plan.setYearlyMad(year > 0 ? year : month * 10);
            plan.setCredits(credits);
            plan.setFeatures(features);
            plans.save(plan);
            return 1;
        }
        boolean dirty = false;
        if (plan.getAudience() == null || plan.getAudience().isBlank()) {
            plan.setAudience(audience);
            dirty = true;
        }
        String cleaned = cleanFeatures(plan.getFeatures());
        if (cleaned.isBlank()) cleaned = features;
        if (!cleaned.equals(plan.getFeatures())) {
            plan.setFeatures(cleaned);
            dirty = true;
        }
        if (dirty) plans.save(plan);
        return 0;
    }

    /** Ancienne formule : prix à zéro et lignes nettoyées. Ne rajoute jamais rien (idempotent). */
    private void retireLegacy(String code) {
        plans.findByCode(code).ifPresent(p -> {
            String cleaned = cleanFeatures(p.getFeatures());
            boolean dirty = !cleaned.equals(p.getFeatures() == null ? "" : p.getFeatures())
                    || p.getMonthlyMad() != 0 || p.getYearlyMad() != 0;
            if (!dirty) return;
            p.setFeatures(cleaned);
            p.setMonthlyMad(0);
            p.setYearlyMad(0);
            plans.save(p);
        });
    }

    /**
     * Retire des lignes de formule tout ce qui relève de l'interne : marqueurs « [RETIRÉ …] » écrits
     * par les anciennes versions (parfois en dizaines d'exemplaires), mentions de paiement de test,
     * précisions techniques entre parenthèses, et doublons.
     */
    static String cleanFeatures(String raw) {
        if (raw == null || raw.isBlank()) return "";
        java.util.LinkedHashSet<String> kept = new java.util.LinkedHashSet<>();
        for (String line : raw.split("\\R")) {
            String l = line.trim();
            if (l.isEmpty()) continue;
            String folded = java.text.Normalizer.normalize(l, java.text.Normalizer.Form.NFD)
                    .replaceAll("\\p{M}", "").toLowerCase(java.util.Locale.ROOT);
            if (folded.startsWith("[retire")) continue;
            if (folded.contains("paiement simule")) continue;
            if (folded.contains("garde-fou") || folded.contains("garde fou")) {
                l = l.replaceAll("\\s*\\((?i:garde[- ]fou)[^)]*\\)", "").trim();
                if (l.isEmpty()) continue;
            }
            if (folded.equals("services ia avances reellement disponibles")) {
                l = "Outils IA avancés pour le CV et les entretiens";
            }
            kept.add(l);
        }
        return String.join("\n", kept);
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
