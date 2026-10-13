package ma.ensat.hirelink.service;

import ma.ensat.hirelink.model.PlanTier;
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

    public AiCreditService(AiUsageLogRepository aiUsageLogRepository) {
        this.aiUsageLogRepository = aiUsageLogRepository;
    }

    public record CreditStatus(
            String plan,
            int limit,
            int used,
            int remaining,
            boolean premiumUnlimited,
            boolean allowed
    ) {}

    public int limitFor(PlanTier tier) {
        if (tier == null) return 5;
        return switch (tier) {
            case ESSENTIEL -> 5;
            case PERFORMANCE -> 50;
            case ACCES -> 20;
            case PREMIUM -> 300;
        };
    }

    public PlanTier resolveTier(User user, boolean premiumHeader) {
        if (premiumHeader) {
            return user.getRole().name().contains("CANDIDAT") ? PlanTier.PERFORMANCE : PlanTier.PREMIUM;
        }
        if (user.getPlanTier() != null) return user.getPlanTier();
        return user.getRole().name().contains("CANDIDAT") ? PlanTier.ESSENTIEL : PlanTier.ACCES;
    }

    @Transactional(readOnly = true)
    public CreditStatus status(User user, boolean premiumHeader) {
        PlanTier tier = resolveTier(user, premiumHeader);
        int limit = limitFor(tier);
        int u = usedThisMonth(user.getId());
        return new CreditStatus(tier.name(), limit, u, Math.max(0, limit - u), false, u < limit);
    }

    /** Ne pas appeler avant succès LLM (R16). */
    @Transactional(readOnly = true)
    public CreditStatus consumeAfterSuccess(User user, boolean premiumHeader) {
        // Le débit réel est l'insertion AiUsageLog (creditsSpent=1) faite par le controller.
        // Ici on recalcule le statut après écriture.
        return status(user, premiumHeader);
    }

    /** @deprecated use status(User, boolean) */
    @Transactional(readOnly = true)
    public CreditStatus status(Long userId, String role, boolean premium) {
        int limit = premium ? 300 : ("RECRUTEUR".equalsIgnoreCase(role) || "ADMINISTRATEUR".equalsIgnoreCase(role) ? 20 : 5);
        int u = usedThisMonth(userId);
        return new CreditStatus(premium ? "PREMIUM" : "ESSENTIEL", limit, u, Math.max(0, limit - u), false, u < limit);
    }

    /** @deprecated */
    @Transactional(readOnly = true)
    public CreditStatus consume(Long userId, String role, boolean premium) {
        return status(userId, role, premium);
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
