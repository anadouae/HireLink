package ma.ensat.hirelink.service;

import ma.ensat.hirelink.model.AiUsageLog;
import ma.ensat.hirelink.model.PlanTier;
import ma.ensat.hirelink.model.Role;
import ma.ensat.hirelink.model.User;
import ma.ensat.hirelink.repository.AiUsageLogRepository;
import ma.ensat.hirelink.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Ledger crédits IA F16 — paliers CDC §9.
 * Compteur durable via ai_usage_logs (pas de ConcurrentHashMap).
 * Matching local (F14) ne consomme rien. F15 : le crédit est réservé avant l'appel (B2) puis liquidé —
 * un appel qui échoue rend son crédit, jamais l'inverse.
 */
@Service
public class AiCreditService {

    private static final Logger log = LoggerFactory.getLogger(AiCreditService.class);

    private final AiUsageLogRepository aiUsageLogRepository;
    private final BillingCatalogService billingCatalogService;
    private final UserRepository userRepository;

    @Value("${hirelink.ai.reservation-stale-minutes:15}")
    private int staleReservationMinutes = 15;

    public AiCreditService(AiUsageLogRepository aiUsageLogRepository,
                           BillingCatalogService billingCatalogService,
                           UserRepository userRepository) {
        this.aiUsageLogRepository = aiUsageLogRepository;
        this.billingCatalogService = billingCatalogService;
        this.userRepository = userRepository;
    }

    /** Une action IA réussie = un crédit. Le coût n'est pas une variable d'ajustement invisible. */
    public static final int COST_PER_ACTION = 1;

    public record CreditStatus(
            String plan,
            String planLabel,
            int monthlyMad,
            int limit,
            int used,
            int remaining,
            int costPerAction,
            int daysToReset,
            boolean premiumUnlimited,
            boolean allowed
    ) {}

    /** Ce qu'une formule a consommé, fonctionnalité par fonctionnalité, depuis le début du mois. */
    public record FeatureUse(String feature, int actions, int credits) {}

    public PlanTier resolveTier(User user) {
        if (user.getPlanTier() != null) return user.getPlanTier();
        return user.getRole() == Role.CANDIDAT ? PlanTier.ESSENTIEL : PlanTier.ACCES;
    }

    /** Le plafond mensuel vient du catalogue de plans (modifiable par l'administration). */
    public int limitFor(PlanTier tier) {
        return billingCatalogService.require(tier).getCredits();
    }

    @Transactional(readOnly = true)
    public CreditStatus status(User user) {
        PlanTier tier = resolveTier(user);
        ma.ensat.hirelink.model.CommercialPlan plan = billingCatalogService.require(tier);
        int limit = plan.getCredits();
        int u = usedThisMonth(user.getId());
        boolean paid = tier == PlanTier.PREMIUM || tier == PlanTier.PERFORMANCE;
        return new CreditStatus(
                tier.name(),
                plan.getLabel(),
                plan.getMonthlyMad(),
                limit,
                u,
                Math.max(0, limit - u),
                COST_PER_ACTION,
                daysToReset(),
                paid,
                u < limit
        );
    }

    @Transactional(readOnly = true)
    public List<FeatureUse> monthlyByFeature(User user) {
        YearMonth ym = YearMonth.now(ZoneOffset.UTC);
        Instant from = ym.atDay(1).atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant to = ym.plusMonths(1).atDay(1).atStartOfDay().toInstant(ZoneOffset.UTC);
        List<FeatureUse> out = new ArrayList<>();
        for (Object[] row : aiUsageLogRepository.sumByFeature(user.getId(), from, to)) {
            String feature = String.valueOf(row[0]);
            int actions = row[1] == null ? 0 : ((Number) row[1]).intValue();
            int credits = row[2] == null ? 0 : ((Number) row[2]).intValue();
            out.add(new FeatureUse(feature, actions, credits));
        }
        return out;
    }

    /** Le compteur repart le 1er : le dire évite la question « quand est-ce que j'aurai de nouveau des crédits ? ». */
    private static int daysToReset() {
        java.time.LocalDate today = java.time.LocalDate.now(ZoneOffset.UTC);
        return (int) java.time.temporal.ChronoUnit.DAYS.between(
                today, YearMonth.from(today).atEndOfMonth().plusDays(1));
    }

    private int usedThisMonth(Long userId) {
        YearMonth ym = YearMonth.now(ZoneOffset.UTC);
        Instant from = ym.atDay(1).atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant to = ym.plusMonths(1).atDay(1).atStartOfDay().toInstant(ZoneOffset.UTC);
        Integer sum = aiUsageLogRepository.sumCreditsSpent(userId, from, to);
        return sum == null ? 0 : sum;
    }

    /**
     * Réservation atomique d'un crédit AVANT l'appel IA (B2) : verrou de ligne sur le compte, contrôle du
     * solde, écriture d'une ligne déjà débitée. N appels simultanés sur le dernier crédit ne laissent donc
     * qu'un gagnant ; les autres reçoivent une 402 sans avoir payé d'appel.
     * La ligne reste « non liquidée » jusqu'à {@link #settle} ou {@link #release}.
     */
    @Transactional
    public AiUsageLog reserve(User user, String feature) {
        userRepository.findByIdForUpdate(user.getId())
                .orElseThrow(() -> new java.util.NoSuchElementException("Compte inconnu"));
        CreditStatus st = status(user);
        if (!st.allowed()) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.PAYMENT_REQUIRED,
                    "Crédits IA épuisés (" + st.used() + "/" + st.limit() + " ce mois). Le compteur repart dans "
                            + st.daysToReset() + " jour" + (st.daysToReset() > 1 ? "s" : "") + ".");
        }
        AiUsageLog row = new AiUsageLog();
        row.setUserId(user.getId());
        row.setOrganizationId(user.getAssignedOrgId());
        row.setFeature(feature);
        row.setProvider("reservation");
        row.setCreditsSpent(COST_PER_ACTION);
        row.setSuccess(false);
        return aiUsageLogRepository.save(row);
    }

    /** Liquidation après l'appel : succès = le débit reste ; échec = 0 crédit, aucune perte injustifiée. */
    @Transactional
    public void settle(AiUsageLog reservation, LlmProvider.LlmResult result) {
        if (reservation == null) return;
        reservation.setProvider(result.provider());
        reservation.setModel(result.model());
        reservation.setCharsIn(result.charsIn());
        reservation.setCharsOut(result.charsOut());
        boolean external = result.success()
                && result.provider() != null
                && !result.provider().toLowerCase(java.util.Locale.ROOT).startsWith("local");
        reservation.setSuccess(external);
        reservation.setCreditsSpent(external ? COST_PER_ACTION : 0);
        aiUsageLogRepository.save(reservation);
    }

    /** Libération quand l'appel n'aboutit pas du tout (exception) : le crédit est rendu. */
    @Transactional
    public void release(AiUsageLog reservation) {
        if (reservation == null) return;
        reservation.setCreditsSpent(0);
        reservation.setSuccess(false);
        aiUsageLogRepository.save(reservation);
    }

    /** Une réservation orpheline (processus arrêté en plein appel) ne doit pas immobiliser un crédit. */
    @Transactional
    public int releaseStaleReservations() {
        Instant before = Instant.now().minus(Duration.ofMinutes(staleReservationMinutes));
        List<AiUsageLog> stale = aiUsageLogRepository.findUnsettledReservations(before);
        for (AiUsageLog row : stale) {
            row.setCreditsSpent(0);
        }
        aiUsageLogRepository.saveAll(stale);
        if (!stale.isEmpty()) {
            log.info("{} réservation(s) de crédit IA rendue(s) faute de liquidation", stale.size());
        }
        return stale.size();
    }

    /** Balaye les réservations non liquidées : le crédit réservé par un appel interrompu revient à son titulaire. */
    @Scheduled(fixedDelayString = "${hirelink.ai.reservation-sweep-ms:900000}",
            initialDelayString = "${hirelink.ai.reservation-sweep-initial-delay-ms:120000}")
    @Transactional
    public void sweepStaleReservations() {
        releaseStaleReservations();
    }

    /**
     * Grille structurée : catégorie + question claire + guide « ce qu'il faut écouter »
     * (affiché au clic côté front).
     */
    public List<Map<String, String>> interviewKit(String jobTitle, String skills) {
        String title = jobTitle == null || jobTitle.isBlank() ? "ce poste" : jobTitle.trim();
        List<String> skillList = new ArrayList<>();
        if (skills != null) {
            for (String s : skills.split("[,;/|]+")) {
                if (!s.isBlank()) skillList.add(s.trim());
            }
        }
        String sk = skillList.isEmpty() ? "les compétences clés du poste" : skillList.get(0);
        String sk2 = skillList.size() > 1 ? skillList.get(1) : sk;
        List<Map<String, String>> q = new ArrayList<>();
        q.add(kitItem(
                "Ouverture",
                "Parlez-moi d’un résultat concret dont vous êtes fier·e, en lien avec « " + title + " ».",
                "Un fait mesurable (délai, volume, qualité). Pas seulement le contexte : le résultat et le rôle personnel."
        ));
        q.add(kitItem(
                "Technique",
                "Donnez un exemple où vous avez utilisé " + sk + " sous contrainte de délai.",
                "Maîtrise réelle de " + sk + " : choix techniques, compromis, ce qui a été livré à temps."
        ));
        q.add(kitItem(
                "Méthode STAR",
                "Décrivez un projet proche de ce poste : situation, tâche, actions, résultat.",
                "Structure claire STAR. Actions personnelles (pas « on a… »). Résultat vérifiable."
        ));
        q.add(kitItem(
                "Collaboration",
                "Comment gérez-vous un désaccord avec un métier, un collègue ou un manager ?",
                "Écoute, reformulation, proposition, escalade propre. Pas de blâme ni d’évitement."
        ));
        q.add(kitItem(
                "Priorités",
                "Plusieurs demandes urgentes arrivent le même jour : comment priorisez-vous ?",
                "Critères explicites (impact, échéance, risque). Communication aux parties prenantes."
        ));
        q.add(kitItem(
                "Compétence clé",
                "Où vous situez-vous sur " + sk2 + " — et que feriez-vous pour progresser en 90 jours ?",
                "Honnêteté sur le niveau actuel + plan concret d’apprentissage (pas de jargon creux)."
        ));
        q.add(kitItem(
                "Clôture",
                "Quelles questions posez-vous pour clarifier le succès à 90 jours sur ce poste ?",
                "Curiosité utile : objectifs, rituels d’équipe, critères de réussite. Pas seulement le salaire."
        ));
        return q;
    }

    private static Map<String, String> kitItem(String category, String question, String lookFor) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("category", category);
        m.put("question", question);
        m.put("lookFor", lookFor);
        return m;
    }
}
