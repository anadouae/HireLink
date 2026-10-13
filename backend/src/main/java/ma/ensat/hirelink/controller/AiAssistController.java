package ma.ensat.hirelink.controller;

import ma.ensat.hirelink.model.Role;
import ma.ensat.hirelink.model.User;
import ma.ensat.hirelink.repository.AiUsageLogRepository;
import ma.ensat.hirelink.model.AiUsageLog;
import ma.ensat.hirelink.service.AiAssistService;
import ma.ensat.hirelink.service.AiCreditService;
import ma.ensat.hirelink.service.HttpLlmProvider;
import ma.ensat.hirelink.service.LanguageCheckService;
import ma.ensat.hirelink.service.LlmProvider;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/ai")
public class AiAssistController {

    private final AiAssistService aiAssistService;
    private final AiCreditService aiCreditService;
    private final LanguageCheckService languageCheckService;
    private final HttpLlmProvider llmProvider;
    private final AiUsageLogRepository aiUsageLogRepository;

    public AiAssistController(
            AiAssistService aiAssistService,
            AiCreditService aiCreditService,
            LanguageCheckService languageCheckService,
            HttpLlmProvider llmProvider,
            AiUsageLogRepository aiUsageLogRepository
    ) {
        this.aiAssistService = aiAssistService;
        this.aiCreditService = aiCreditService;
        this.languageCheckService = languageCheckService;
        this.llmProvider = llmProvider;
        this.aiUsageLogRepository = aiUsageLogRepository;
    }

    public record ProfileRewriteRequest(String skills, String roles, String level, Boolean premium) {}
    public record InterviewPrepRequest(String jobTitle, String skills, Boolean premium) {}
    public record InterviewKitRequest(String jobTitle, String skills, Boolean premium) {}
    public record LanguageCheckRequest(String title, String description) {}
    public record RefusalDraftRequest(String reasonCode, String reasonLabel, String jobTitle, Boolean premium) {}
    public record MatchExplainRequest(String skills, String requiredSkills, Boolean premium) {}

    @GetMapping("/credits")
    public Map<String, Object> credits(
            @AuthenticationPrincipal User user,
            @RequestHeader(value = "X-HireLink-Premium", required = false) String premiumHeader
    ) {
        boolean premium = "1".equals(premiumHeader);
        return creditMap(aiCreditService.status(user, premium));
    }

    /** Quels providers cloud sont configurés (clés masquées). */
    @GetMapping("/status")
    public Map<String, Object> status(@AuthenticationPrincipal User user) {
        Map<String, Object> m = new LinkedHashMap<>(llmProvider.statusMap());
        m.put("user", user.getEmail());
        m.put("features", List.of(
                "profile-rewrite", "interview-prep", "interview-kit",
                "refusal-draft", "match-explain", "language-check"
        ));
        return m;
    }

    @PostMapping("/profile-rewrite")
    public Map<String, Object> profileRewrite(
            @AuthenticationPrincipal User user,
            @RequestBody ProfileRewriteRequest body,
            @RequestHeader(value = "X-HireLink-Premium", required = false) String premiumHeader
    ) {
        boolean premium = Boolean.TRUE.equals(body.premium()) || "1".equals(premiumHeader);
        requireCredit(user, premium);
        // R15 : compétences / rôles / niveau uniquement — pas de nom/email
        String skills = body.skills() != null ? body.skills() : user.getSkills();
        String roles = body.roles() != null ? body.roles() : user.getSoughtRoles();
        String level = body.level() != null ? body.level() : user.getExperienceLevel();
        LlmProvider.LlmResult llm = llmProvider.complete(
                "Tu es un assistant recrutement. Réponds en français. N'invente pas d'identité.",
                "Améliore ce profil (compétences uniquement) : skills=" + nullSafe(skills)
                        + " ; métiers=" + nullSafe(roles) + " ; niveau=" + nullSafe(level)
        );
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("title", "Suggestions de profil (F15)");
        m.put("content", llm.text());
        m.put("provider", llm.provider());
        m.put("premiumLocked", false);
        m.put("tips", List.of("Relisez avant d'enregistrer — l'IA ne réécrit jamais à votre place."));
        m.put("credits", debitIfSuccess(user, premium, "profile-rewrite", llm));
        return m;
    }

    @PostMapping("/interview-prep")
    public Map<String, Object> interviewPrep(
            @AuthenticationPrincipal User user,
            @RequestBody InterviewPrepRequest body,
            @RequestHeader(value = "X-HireLink-Premium", required = false) String premiumHeader
    ) {
        boolean premium = Boolean.TRUE.equals(body.premium()) || "1".equals(premiumHeader);
        requireCredit(user, premium);
        String skills = body.skills() != null ? body.skills() : user.getSkills();
        LlmProvider.LlmResult llm = llmProvider.complete(
                "Assistant préparation entretien. Français. Pas de données personnelles.",
                "Poste=" + nullSafe(body.jobTitle()) + " ; compétences=" + nullSafe(skills)
                        + ". Produis une préparation d'entretien claire."
        );
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("title", "Préparation entretien (F15)");
        m.put("content", llm.text());
        m.put("provider", llm.provider());
        m.put("premiumLocked", false);
        m.put("tips", List.of("Adaptez les réponses à votre vécu réel."));
        m.put("credits", debitIfSuccess(user, premium, "interview-prep", llm));
        return m;
    }

    @PostMapping("/interview-kit")
    public Map<String, Object> interviewKit(
            @AuthenticationPrincipal User user,
            @RequestBody InterviewKitRequest body,
            @RequestHeader(value = "X-HireLink-Premium", required = false) String premiumHeader
    ) {
        if (user.getRole() != Role.RECRUTEUR && user.getRole() != Role.ADMINISTRATEUR) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Réservé aux recruteurs");
        }
        boolean premium = Boolean.TRUE.equals(body.premium()) || "1".equals(premiumHeader);
        requireCredit(user, premium);
        LlmProvider.LlmResult llm = llmProvider.complete(
                "Génère une grille d'entretien structurée en français. Pas d'identité candidat.",
                "Poste=" + nullSafe(body.jobTitle()) + " ; compétences=" + nullSafe(body.skills())
                        + ". 5 à 7 questions discriminantes."
        );
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("title", "Grille d’entretien structurée (F15)");
        m.put("content", llm.text());
        m.put("questions", aiCreditService.interviewKit(body.jobTitle(), body.skills()));
        m.put("note", "Proposition — le manager édite et mène l’entretien (R07).");
        m.put("provider", llm.provider());
        m.put("credits", debitIfSuccess(user, premium, "interview-kit", llm));
        return m;
    }

    @PostMapping("/refusal-draft")
    public Map<String, Object> refusalDraft(
            @AuthenticationPrincipal User user,
            @RequestBody RefusalDraftRequest body,
            @RequestHeader(value = "X-HireLink-Premium", required = false) String premiumHeader
    ) {
        if (user.getRole() != Role.RECRUTEUR && user.getRole() != Role.ADMINISTRATEUR) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Réservé aux recruteurs");
        }
        boolean premium = Boolean.TRUE.equals(body.premium()) || "1".equals(premiumHeader);
        requireCredit(user, premium);
        // R15 : motif catalogue + intitulé poste uniquement
        LlmProvider.LlmResult llm = llmProvider.complete(
                "Rédige un brouillon de message de refus professionnel en français. Pas de nom ni email.",
                "Motif catalogue=" + nullSafe(body.reasonCode()) + " (" + nullSafe(body.reasonLabel())
                        + ") ; poste=" + nullSafe(body.jobTitle())
                        + ". Message respectueux, court, sans promesse."
        );
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("title", "Brouillon de refus (F15)");
        m.put("content", llm.text());
        m.put("provider", llm.provider());
        m.put("note", "Proposition — le recruteur valide avant envoi.");
        m.put("credits", debitIfSuccess(user, premium, "refusal-draft", llm));
        return m;
    }

    @PostMapping("/match-explain")
    public Map<String, Object> matchExplain(
            @AuthenticationPrincipal User user,
            @RequestBody MatchExplainRequest body,
            @RequestHeader(value = "X-HireLink-Premium", required = false) String premiumHeader
    ) {
        boolean premium = Boolean.TRUE.equals(body.premium()) || "1".equals(premiumHeader);
        requireCredit(user, premium);
        LlmProvider.LlmResult llm = llmProvider.complete(
                "Explique un écart de matching en français clair. Pas d'identité.",
                "Compétences candidat=" + nullSafe(body.skills())
                        + " ; compétences offre=" + nullSafe(body.requiredSkills())
                        + ". Explique les forces et manques, suggère améliorations."
        );
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("title", "Explication matching (F15)");
        m.put("content", llm.text());
        m.put("provider", llm.provider());
        m.put("credits", debitIfSuccess(user, premium, "match-explain", llm));
        return m;
    }

    @PostMapping("/language-check")
    public Map<String, Object> languageCheck(
            @AuthenticationPrincipal User user,
            @RequestBody LanguageCheckRequest body
    ) {
        if (user.getRole() != Role.RECRUTEUR && user.getRole() != Role.ADMINISTRATEUR) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Réservé aux recruteurs");
        }
        var r = languageCheckService.check(body.title(), body.description());
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("flagged", r.flagged());
        m.put("markersFound", r.markersFound());
        m.put("suggestions", r.suggestions());
        m.put("summary", r.summary());
        return m;
    }

    private void requireCredit(User user, boolean premium) {
        var st = aiCreditService.status(user, premium);
        if (!st.allowed()) {
            throw new ResponseStatusException(HttpStatus.PAYMENT_REQUIRED,
                    "Crédits IA épuisés (" + st.used() + "/" + st.limit()
                            + ") — palier=" + st.plan() + " (F16)");
        }
    }

    /** R16 : débit seulement si succès — ledger durable = ai_usage_logs. */
    private Map<String, Object> debitIfSuccess(User user, boolean premium, String feature, LlmProvider.LlmResult llm) {
        AiUsageLog log = new AiUsageLog();
        log.setUserId(user.getId());
        log.setFeature(feature);
        log.setProvider(llm.provider());
        log.setModel(llm.model());
        log.setCharsIn(llm.charsIn());
        log.setCharsOut(llm.charsOut());
        log.setSuccess(llm.success());
        if (llm.success()) {
            log.setCreditsSpent(1);
            aiUsageLogRepository.save(log);
            return creditMap(aiCreditService.status(user, premium));
        }
        log.setCreditsSpent(0);
        aiUsageLogRepository.save(log);
        return creditMap(aiCreditService.status(user, premium));
    }

    private Map<String, Object> creditMap(AiCreditService.CreditStatus s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("plan", s.plan());
        m.put("limit", s.limit());
        m.put("used", s.used());
        m.put("remaining", s.remaining());
        m.put("premium", s.premiumUnlimited());
        m.put("allowed", s.allowed());
        return m;
    }

    private static String nullSafe(String s) {
        return s == null ? "" : s.trim();
    }
}
