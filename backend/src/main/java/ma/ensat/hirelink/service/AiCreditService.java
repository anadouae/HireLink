package ma.ensat.hirelink.service;

import ma.ensat.hirelink.model.PlanTier;
import ma.ensat.hirelink.model.User;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Ledger crédits IA F16 — paliers CDC §9.
 * Matching local (F14) ne consomme rien. F15 consomme 1 crédit après succès (R16).
 */
@Service
public class AiCreditService {

    private final ConcurrentHashMap<String, AtomicInteger> used = new ConcurrentHashMap<>();

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

    public CreditStatus status(User user, boolean premiumHeader) {
        PlanTier tier = resolveTier(user, premiumHeader);
        int limit = limitFor(tier);
        // Premium org / Performance candidate still have finite credits per CDC; "illimité" n'est pas dans le CDC v2
        int u = used.computeIfAbsent(key(user.getId()), k -> new AtomicInteger(0)).get();
        return new CreditStatus(tier.name(), limit, u, Math.max(0, limit - u), false, u < limit);
    }

    /** Ne pas appeler avant succès LLM (R16). */
    public CreditStatus consumeAfterSuccess(User user, boolean premiumHeader) {
        CreditStatus before = status(user, premiumHeader);
        if (!before.allowed()) return before;
        used.computeIfAbsent(key(user.getId()), k -> new AtomicInteger(0)).incrementAndGet();
        return status(user, premiumHeader);
    }

    /** @deprecated use status(User, boolean) */
    public CreditStatus status(Long userId, String role, boolean premium) {
        int limit = premium ? 300 : ("RECRUTEUR".equalsIgnoreCase(role) || "ADMINISTRATEUR".equalsIgnoreCase(role) ? 20 : 5);
        int u = used.computeIfAbsent(key(userId), k -> new AtomicInteger(0)).get();
        return new CreditStatus(premium ? "PREMIUM" : "ESSENTIEL", limit, u, Math.max(0, limit - u), false, u < limit);
    }

    /** @deprecated */
    public CreditStatus consume(Long userId, String role, boolean premium) {
        CreditStatus before = status(userId, role, premium);
        if (!before.allowed()) return before;
        used.computeIfAbsent(key(userId), k -> new AtomicInteger(0)).incrementAndGet();
        return status(userId, role, premium);
    }

    private String key(Long userId) {
        return userId + "-" + java.time.YearMonth.now();
    }

    public java.util.List<String> interviewKit(String jobTitle, String skills) {
        String title = jobTitle == null || jobTitle.isBlank() ? "le poste" : jobTitle.trim();
        java.util.List<String> skillList = new java.util.ArrayList<>();
        if (skills != null) {
            for (String s : skills.split("[,;/|]+")) {
                if (!s.isBlank()) skillList.add(s.trim());
            }
        }
        String sk = skillList.isEmpty() ? "vos compétences clés" : skillList.get(0);
        java.util.List<String> q = new java.util.ArrayList<>();
        q.add("Ouverture (2 min) — Présentez un résultat concret lié à " + title + ".");
        q.add("Technique — Donnez un exemple où vous avez utilisé " + sk + " sous contrainte de délai.");
        q.add("STAR — Situation / Tâche / Action / Résultat sur un projet similaire.");
        q.add("Collaboration — Comment gérez-vous un désaccord avec un métier ou un manager ?");
        q.add("SLA / engagement — Comment priorisez-vous quand plusieurs demandes arrivent le même jour ?");
        q.add("Clôture — Quelles questions posez-vous pour clarifier le succès à 90 jours ?");
        return q;
    }
}
