package ma.ensat.hirelink.service;

import ma.ensat.hirelink.model.Application;
import ma.ensat.hirelink.model.ApplicationStatus;
import ma.ensat.hirelink.model.User;
import ma.ensat.hirelink.repository.ApplicationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Contrat de confiance bilatéral — scores calculés depuis les événements pipeline (jamais subjectifs).
 */
@Service
public class ReliabilityService {

    private final ApplicationRepository applicationRepository;

    public ReliabilityService(ApplicationRepository applicationRepository) {
        this.applicationRepository = applicationRepository;
    }

    public record ReliabilityScore(
            String side,
            int score,
            String label,
            Map<String, Object> factors,
            String method,
            boolean blocking
    ) {}

    @Transactional(readOnly = true)
    public ReliabilityScore forOrganization(User recruiter) {
        List<Application> apps = applicationRepository.findByJobOfferRecruiterId(recruiter.getId());
        int total = apps.size();
        long decided = apps.stream().filter(a ->
                a.getStatus() == ApplicationStatus.ACCEPTED
                        || a.getStatus() == ApplicationStatus.REJECTED).count();
        long onTime = apps.stream().filter(a -> {
            if (a.getJobOffer() == null) return true;
            int slaDays = a.getJobOffer().getResponseSlaDays();
            Instant deadline = a.getSubmittedAt().plus(slaDays, ChronoUnit.DAYS);
            if (a.getStatus() == ApplicationStatus.ACCEPTED || a.getStatus() == ApplicationStatus.REJECTED) {
                return !a.getUpdatedAt().isAfter(deadline);
            }
            return !Instant.now().isAfter(deadline);
        }).count();
        double decisionRate = total == 0 ? 1.0 : (double) decided / total;
        double slaRate = total == 0 ? 1.0 : (double) onTime / total;
        int score = (int) Math.round(100 * (0.55 * slaRate + 0.45 * decisionRate));
        Map<String, Object> factors = new LinkedHashMap<>();
        factors.put("applications", total);
        factors.put("slaRespectRate", Math.round(slaRate * 100));
        factors.put("explicitDecisionRate", Math.round(decisionRate * 100));
        String label = score >= 80 ? "Excellent" : score >= 60 ? "Solide" : score >= 40 ? "À améliorer" : "Fragile";
        return new ReliabilityScore("ORGANIZATION", score, label, factors,
                "0.55×taux_SLA + 0.45×taux_décision_explicite (timeline)", false);
    }

    @Transactional(readOnly = true)
    public ReliabilityScore forCandidate(User candidate) {
        List<Application> apps = applicationRepository.findByCandidateId(candidate.getId());
        int total = apps.size();
        long withdrawn = apps.stream().filter(a -> a.getStatus() == ApplicationStatus.WITHDRAWN).count();
        long interviews = apps.stream().filter(a -> a.getStatus() == ApplicationStatus.INTERVIEW
                || a.getInterviewAt() != null).count();
        double presenceProxy = interviews == 0 ? 1.0 : 1.0 - (withdrawn * 0.15);
        presenceProxy = Math.max(0.4, Math.min(1.0, presenceProxy));
        int score = total == 0 ? 70 : (int) Math.round(100 * presenceProxy);
        Map<String, Object> factors = new LinkedHashMap<>();
        factors.put("applications", total);
        factors.put("withdrawn", withdrawn);
        factors.put("note", "Annulation anticipée non pénalisée ; silence / abandon silencieux pénalise.");
        return new ReliabilityScore("CANDIDATE", score,
                score >= 75 ? "Fiable" : "À consolider", factors,
                "Proxy présence/engagement depuis événements pipeline (non bloquant)", false);
    }

    public String badgeSnippet(User recruiter, ReliabilityScore score) {
        String company = recruiter.getCompany() != null ? recruiter.getCompany() : "Organisation";
        return "<a href=\"https://hirelink.local/trust/" + recruiter.getId() + "\" "
                + "style=\"display:inline-flex;align-items:center;gap:8px;padding:8px 12px;"
                + "border:1px solid #7a2032;border-radius:999px;text-decoration:none;color:#1a1214;"
                + "font-family:system-ui,sans-serif;font-size:13px\">"
                + "<strong style=\"color:#7a2032\">HireLink</strong> "
                + company + " · Fiabilité " + score.score() + "/100 (" + score.label() + ")"
                + "</a>";
    }
}
