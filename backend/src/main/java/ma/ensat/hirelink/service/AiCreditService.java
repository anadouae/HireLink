package ma.ensat.hirelink.service;

import ma.ensat.hirelink.model.PlanTier;
import ma.ensat.hirelink.model.Role;
import ma.ensat.hirelink.model.User;
import ma.ensat.hirelink.repository.AiUsageLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

/**
 * Ledger crédits IA F16 — paliers CDC §9.
 * Compteur durable via ai_usage_logs (pas de ConcurrentHashMap).
 * Matching local (F14) ne consomme rien. F15 consomme 1 crédit après succès (R16).
 */
@Service
public class AiCreditService {

    private final AiUsageLogRepository aiUsageLogRepository;
    private final BillingCatalogService billingCatalogService;

    public AiCreditService(AiUsageLogRepository aiUsageLogRepository, BillingCatalogService billingCatalogService) {
        this.aiUsageLogRepository = aiUsageLogRepository;
        this.billingCatalogService = billingCatalogService;
    }

    public record CreditStatus(
            String plan,
            int limit,
            int used,
            int remaining,
            boolean premiumUnlimited,
            boolean allowed
    ) {}

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
        int limit = limitFor(tier);
        int u = usedThisMonth(user.getId());
        boolean paid = tier == PlanTier.PREMIUM || tier == PlanTier.PERFORMANCE;
        return new CreditStatus(tier.name(), limit, u, Math.max(0, limit - u), paid, u < limit);
    }

    private int usedThisMonth(Long userId) {
        YearMonth ym = YearMonth.now(ZoneOffset.UTC);
        Instant from = ym.atDay(1).atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant to = ym.plusMonths(1).atDay(1).atStartOfDay().toInstant(ZoneOffset.UTC);
        Integer sum = aiUsageLogRepository.sumCreditsSpent(userId, from, to);
        return sum == null ? 0 : sum;
    }

    public List<String> interviewKit(String jobTitle, String skills) {
        String title = jobTitle == null || jobTitle.isBlank() ? "le poste" : jobTitle.trim();
        List<String> skillList = new ArrayList<>();
        if (skills != null) {
            for (String s : skills.split("[,;/|]+")) {
                if (!s.isBlank()) skillList.add(s.trim());
            }
        }
        String sk = skillList.isEmpty() ? "vos compétences clés" : skillList.get(0);
        List<String> q = new ArrayList<>();
        q.add("Ouverture (2 min) — Présentez un résultat concret lié à " + title + ".");
        q.add("Technique — Donnez un exemple où vous avez utilisé " + sk + " sous contrainte de délai.");
        q.add("STAR — Situation / Tâche / Action / Résultat sur un projet similaire.");
        q.add("Collaboration — Comment gérez-vous un désaccord avec un métier ou un manager ?");
        q.add("SLA / engagement — Comment priorisez-vous quand plusieurs demandes arrivent le même jour ?");
        q.add("Clôture — Quelles questions posez-vous pour clarifier le succès à 90 jours ?");
        return q;
    }
}
