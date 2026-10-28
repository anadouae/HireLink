package ma.ensat.hirelink.controller;

import ma.ensat.hirelink.model.Role;
import ma.ensat.hirelink.model.User;
import ma.ensat.hirelink.repository.AiUsageLogRepository;
import ma.ensat.hirelink.model.AiUsageLog;
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

    private final AiCreditService aiCreditService;
    private final LanguageCheckService languageCheckService;
    private final HttpLlmProvider llmProvider;
    private final AiUsageLogRepository aiUsageLogRepository;

    public AiAssistController(
            AiCreditService aiCreditService,
            LanguageCheckService languageCheckService,
            HttpLlmProvider llmProvider,
            AiUsageLogRepository aiUsageLogRepository
    ) {
        this.aiCreditService = aiCreditService;
        this.languageCheckService = languageCheckService;
        this.llmProvider = llmProvider;
        this.aiUsageLogRepository = aiUsageLogRepository;
    }

    public record ProfileRewriteRequest(@jakarta.validation.constraints.Size(max = 3000) String skills, @jakarta.validation.constraints.Size(max = 300) String roles, @jakarta.validation.constraints.Size(max = 40) String level) {}
    public record InterviewPrepRequest(@jakarta.validation.constraints.Size(max = 160) String jobTitle, @jakarta.validation.constraints.Size(max = 3000) String skills) {}
    public record InterviewKitRequest(@jakarta.validation.constraints.Size(max = 160) String jobTitle, @jakarta.validation.constraints.Size(max = 3000) String skills) {}
    public record LanguageCheckRequest(@jakarta.validation.constraints.Size(max = 160) String title, @jakarta.validation.constraints.Size(max = 10000) String description) {}
    public record RefusalDraftRequest(@jakarta.validation.constraints.Size(max = 40) String reasonCode, @jakarta.validation.constraints.Size(max = 200) String reasonLabel, @jakarta.validation.constraints.Size(max = 160) String jobTitle) {}
    public record CoachRequest(@jakarta.validation.constraints.Size(max = 2000) String message, @jakarta.validation.constraints.Size(max = 20000) String cvText) {}
    public record CvSummaryRequest(@jakarta.validation.constraints.Size(max = 20000) String cvText) {}
    public record MatchExplainRequest(@jakarta.validation.constraints.Size(max = 3000) String skills, @jakarta.validation.constraints.Size(max = 3000) String requiredSkills) {}

    @GetMapping("/credits")
    public Map<String, Object> credits(
            @AuthenticationPrincipal User user
    ) {
        return creditMap(aiCreditService.status(user));
    }

    /** Quels providers cloud sont configurés (clés masquées). */
    @GetMapping("/status")
    public Map<String, Object> status(@AuthenticationPrincipal User user) {
        Map<String, Object> m = new LinkedHashMap<>(llmProvider.statusMap());
        m.put("user", user.getEmail());
        m.put("features", List.of(
                "profile-rewrite", "interview-prep", "interview-kit",
                "refusal-draft", "match-explain", "language-check", "coach"
        ));
        return m;
    }

    @PostMapping("/profile-rewrite")
    public Map<String, Object> profileRewrite(
            @AuthenticationPrincipal User user,
            @jakarta.validation.Valid @RequestBody ProfileRewriteRequest body
    ) {
        requireCredit(user);
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
        m.put("title", "Suggestions de profil");
        m.put("content", llm.text());
        m.put("provider", llm.provider());
        m.put("tips", List.of("Relisez avant d'enregistrer — l'IA ne réécrit jamais à votre place."));
        m.put("credits", debitIfSuccess(user, "profile-rewrite", llm));
        return m;
    }

    @PostMapping("/interview-prep")
    public Map<String, Object> interviewPrep(
            @AuthenticationPrincipal User user,
            @jakarta.validation.Valid @RequestBody InterviewPrepRequest body
    ) {
        requireCredit(user);
        String skills = body.skills() != null ? body.skills() : user.getSkills();
        LlmProvider.LlmResult llm = llmProvider.complete(
                "Assistant préparation entretien. Français. Pas de données personnelles. Pas de markdown : aucun dièse, aucun astérisque, aucun tableau. Un titre court par ligne, puis des phrases.",
                "Poste=" + nullSafe(body.jobTitle()) + " ; compétences=" + nullSafe(skills)
                        + ". Préparation d'entretien claire, en texte simple."
        );
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("title", "Préparation entretien");
        m.put("content", llm.text());
        m.put("provider", llm.provider());
        m.put("tips", List.of("Adaptez les réponses à votre vécu réel."));
        m.put("credits", debitIfSuccess(user, "interview-prep", llm));
        return m;
    }

    @PostMapping("/interview-kit")
    public Map<String, Object> interviewKit(
            @AuthenticationPrincipal User user,
            @jakarta.validation.Valid @RequestBody InterviewKitRequest body
    ) {
        if (user.getRole() != Role.RECRUTEUR && user.getRole() != Role.ADMINISTRATEUR) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Réservé aux recruteurs");
        }
        requireCredit(user);
        LlmProvider.LlmResult llm = llmProvider.complete(
                "Génère une grille d'entretien structurée en français. Pas d'identité candidat.",
                "Poste=" + nullSafe(body.jobTitle()) + " ; compétences=" + nullSafe(body.skills())
                        + ". 5 à 7 questions discriminantes."
        );
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("title", "Questions d’entretien");
        m.put("content", llm.text());
        m.put("questions", aiCreditService.interviewKit(body.jobTitle(), body.skills()));
        m.put("note", "À relire avant l’entretien.");
        m.put("provider", llm.provider());
        m.put("credits", debitIfSuccess(user, "interview-kit", llm));
        return m;
    }

    @PostMapping("/refusal-draft")
    public Map<String, Object> refusalDraft(
            @AuthenticationPrincipal User user,
            @jakarta.validation.Valid @RequestBody RefusalDraftRequest body
    ) {
        if (user.getRole() != Role.RECRUTEUR && user.getRole() != Role.ADMINISTRATEUR) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Réservé aux recruteurs");
        }
        requireCredit(user);
        // R15 : motif catalogue + intitulé poste uniquement
        LlmProvider.LlmResult llm = llmProvider.complete(
                "Rédige un brouillon de message de refus professionnel en français. Pas de nom ni email.",
                "Motif catalogue=" + nullSafe(body.reasonCode()) + " (" + nullSafe(body.reasonLabel())
                        + ") ; poste=" + nullSafe(body.jobTitle())
                        + ". Message respectueux, court, sans promesse."
        );
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("title", "Brouillon de refus");
        m.put("content", llm.text());
        m.put("provider", llm.provider());
        m.put("note", "Proposition — le recruteur valide avant envoi.");
        m.put("credits", debitIfSuccess(user, "refusal-draft", llm));
        return m;
    }

    @PostMapping("/cv-summary")
    public Map<String, Object> cvSummary(
            @AuthenticationPrincipal User user,
            @jakarta.validation.Valid @RequestBody CvSummaryRequest body
    ) {
        String cv = body == null ? "" : nullSafe(body.cvText());
        if (cv.length() > 4000) cv = cv.substring(0, 4000);
        if (cv.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Aucun texte de CV à résumer");
        }
        String text;
        String provider = "local";
        try {
            requireCredit(user);
            boolean recruiter = user.getRole() == Role.RECRUTEUR;
            LlmProvider.LlmResult llm = llmProvider.complete(
                    recruiter
                            ? "Tu résumes un CV pour un recruteur. Français, 6 lignes maximum, sans inventer. Structure : métier probable, expériences, compétences, point d'attention."
                            : "Tu résumes le CV de la personne qui te parle. Français, tutoiement, 6 lignes maximum, sans inventer. Termine par une seule amélioration concrète.",
                    cv
            );
            text = llm.text();
            provider = llm.provider();
            debitIfSuccess(user, "cv-summary", llm);
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            text = localSummary(cv);
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("title", "Synthèse du CV");
        m.put("content", text);
        m.put("provider", provider);
        return m;
    }

    private String localSummary(String cv) {
        String flat = cv.replace('\n', ' ').replaceAll("\\s+", " ").trim();
        String head = flat.length() > 420 ? flat.substring(0, 420).trim() + "…" : flat;
        return "Synthèse à partir du texte déposé :\n" + head
                + "\n\nLe recruteur voit ce texte, pas une décision. Relisez les sections avant de confirmer.";
    }

    @PostMapping("/coach")
    public Map<String, Object> coach(
            @AuthenticationPrincipal User user,
            @jakarta.validation.Valid @RequestBody CoachRequest body
    ) {
        if (user.getRole() != Role.CANDIDAT && user.getRole() != Role.ADMINISTRATEUR) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Réservé aux candidats");
        }
        requireCredit(user);
        String cv = nullSafe(body.cvText());
        if (cv.length() > 4000) cv = cv.substring(0, 4000);
        String question = nullSafe(body.message());
        if (question.isEmpty()) question = "Quelles améliorations concrètes apporter à ce CV ?";
        LlmProvider.LlmResult llm = llmProvider.complete(
                "Tu es un coach carrière. Français simple, tutoiement. Quatre recommandations maximum, numérotées. Pas de markdown. N'invente aucune expérience absente du CV.",
                "CV du candidat :\n" + cv + "\n\nQuestion :\n" + question
        );
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("title", "Coach CV");
        m.put("content", llm.text());
        m.put("provider", llm.provider());
        m.put("credits", debitIfSuccess(user, "coach", llm));
        return m;
    }

    @PostMapping("/match-explain")
    public Map<String, Object> matchExplain(
            @AuthenticationPrincipal User user,
            @jakarta.validation.Valid @RequestBody MatchExplainRequest body
    ) {
        requireCredit(user);
        LlmProvider.LlmResult llm = llmProvider.complete(
                "Explique un écart de matching en français clair. Pas d'identité.",
                "Compétences candidat=" + nullSafe(body.skills())
                        + " ; compétences offre=" + nullSafe(body.requiredSkills())
                        + ". Explique les forces et manques, suggère améliorations."
        );
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("title", "Explication du matching");
        m.put("content", llm.text());
        m.put("provider", llm.provider());
        m.put("credits", debitIfSuccess(user, "match-explain", llm));
        return m;
    }

    @PostMapping("/language-check")
    public Map<String, Object> languageCheck(
            @AuthenticationPrincipal User user,
            @jakarta.validation.Valid @RequestBody LanguageCheckRequest body
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

    private void requireCredit(User user) {
        var st = aiCreditService.status(user);
        if (!st.allowed()) {
            throw new ResponseStatusException(HttpStatus.PAYMENT_REQUIRED,
                    "Crédits IA épuisés (" + st.used() + "/" + st.limit() + ")");
        }
    }

    /** R16 : débit seulement si succès — ledger durable = ai_usage_logs. */
    private Map<String, Object> debitIfSuccess(User user, String feature, LlmProvider.LlmResult llm) {
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
            return creditMap(aiCreditService.status(user));
        }
        log.setCreditsSpent(0);
        aiUsageLogRepository.save(log);
        return creditMap(aiCreditService.status(user));
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
