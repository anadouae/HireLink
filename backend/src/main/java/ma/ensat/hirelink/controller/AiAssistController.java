package ma.ensat.hirelink.controller;

import ma.ensat.hirelink.model.Role;
import ma.ensat.hirelink.model.User;
import ma.ensat.hirelink.model.AiUsageLog;
import ma.ensat.hirelink.service.AiCreditService;
import ma.ensat.hirelink.service.HttpLlmProvider;
import ma.ensat.hirelink.service.LanguageCheckService;
import ma.ensat.hirelink.service.LlmProvider;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/ai")
public class AiAssistController {

    private final AiCreditService aiCreditService;
    private final LanguageCheckService languageCheckService;
    private final HttpLlmProvider llmProvider;

    public AiAssistController(
            AiCreditService aiCreditService,
            LanguageCheckService languageCheckService,
            HttpLlmProvider llmProvider
    ) {
        this.aiCreditService = aiCreditService;
        this.languageCheckService = languageCheckService;
        this.llmProvider = llmProvider;
    }

    public record ProfileRewriteRequest(
            @jakarta.validation.constraints.Size(max = 3000) String skills,
            @jakarta.validation.constraints.Size(max = 300) String roles,
            @jakarta.validation.constraints.Size(max = 40) String level,
            @jakarta.validation.constraints.Size(max = 2000) String experienceDraft
    ) {}
    public record InterviewPrepRequest(@jakarta.validation.constraints.Size(max = 160) String jobTitle, @jakarta.validation.constraints.Size(max = 3000) String skills) {}
    public record InterviewKitRequest(@jakarta.validation.constraints.Size(max = 160) String jobTitle, @jakarta.validation.constraints.Size(max = 3000) String skills) {}
    public record LanguageCheckRequest(@jakarta.validation.constraints.Size(max = 160) String title, @jakarta.validation.constraints.Size(max = 10000) String description) {}
    public record RefusalDraftRequest(@jakarta.validation.constraints.Size(max = 40) String reasonCode, @jakarta.validation.constraints.Size(max = 200) String reasonLabel, @jakarta.validation.constraints.Size(max = 160) String jobTitle) {}
    public record CoachRequest(@jakarta.validation.constraints.Size(max = 2000) String message, @jakarta.validation.constraints.Size(max = 20000) String cvText) {}
    public record CvSummaryRequest(@jakarta.validation.constraints.Size(max = 20000) String cvText) {}
    public record MatchExplainRequest(@jakarta.validation.constraints.Size(max = 3000) String skills, @jakarta.validation.constraints.Size(max = 3000) String requiredSkills) {}

    @GetMapping("/credits")
    public Map<String, Object> credits(@AuthenticationPrincipal User user) {
        return creditMap(user, aiCreditService.status(user));
    }

    /**
     * Suggestions métier : reformulation claire des compétences / d’une expérience fournie.
     * Ne remplit jamais le profil automatiquement — le candidat valide.
     */
    @PostMapping("/profile-rewrite")
    public Map<String, Object> profileRewrite(
            @AuthenticationPrincipal User user,
            @jakarta.validation.Valid @RequestBody ProfileRewriteRequest body
    ) {
        AiUsageLog reservation = aiCreditService.reserve(user, "profile-rewrite");
        try {
            String skills = body.skills() != null ? body.skills() : user.getSkills();
            String roles = body.roles() != null ? body.roles() : user.getSoughtRoles();
            String level = body.level() != null ? body.level() : user.getExperienceLevel();
            String draft = body.experienceDraft() == null ? "" : body.experienceDraft().trim();
            String userPrompt;
            if (!draft.isBlank()) {
                userPrompt = "Voici une description d'expérience fournie par le candidat (ne rien inventer hors de ce texte) :\n"
                        + draft
                        + "\n\nPropose une version plus claire et concrète (missions + résultats mesurables si déjà présents). "
                        + "Si le texte est trop vague, dis précisément quoi ajouter. Pas de markdown.";
            } else {
                userPrompt = "À partir UNIQUEMENT de : skills=" + nullSafe(skills)
                        + " ; métiers=" + nullSafe(roles) + " ; niveau=" + nullSafe(level)
                        + ".\nPropose 3 améliorations concrètes du profil (formulation, lacunes visibles, ordre). "
                        + "N'invente aucune compétence absente. Pas de markdown.";
            }
            LlmProvider.LlmResult llm = llmProvider.complete(
                    "Tu es un conseiller carrière. Français. N'invente aucune expérience, diplôme ni outil absent du texte. Pas de markdown.",
                    userPrompt
            );
            if (llm == null || llm.text() == null || llm.text().isBlank()) {
                throw new ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT,
                        "L’assistant n’a pas répondu à temps. Aucun crédit n’a été débité.");
            }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("title", draft.isBlank() ? "Suggestions d’amélioration" : "Proposition de reformulation");
            m.put("content", llm.text());
            m.put("provider", llm.provider());
            m.put("success", true);
            m.put("tips", List.of(
                    "Relisez avant d’enregistrer — rien n’est écrit à votre place.",
                    "Refusez toute suggestion qui invente un outil ou un résultat."
            ));
            m.put("credits", settle(user, reservation, llm));
            return m;
        } catch (ResponseStatusException e) {
            aiCreditService.release(reservation);
            throw e;
        } catch (Exception e) {
            aiCreditService.release(reservation);
            throw new ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT,
                    "Analyse indisponible pour le moment. Aucun crédit n’a été débité.", e);
        }
    }

    @PostMapping("/interview-prep")
    public Map<String, Object> interviewPrep(
            @AuthenticationPrincipal User user,
            @jakarta.validation.Valid @RequestBody InterviewPrepRequest body
    ) {
        AiUsageLog reservation = aiCreditService.reserve(user, "interview-prep");
        try {
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
            m.put("success", true);
            m.put("tips", List.of("Adaptez les réponses à votre vécu réel."));
            m.put("credits", settle(user, reservation, llm));
            return m;
        } catch (ResponseStatusException e) {
            aiCreditService.release(reservation);
            throw e;
        } catch (Exception e) {
            aiCreditService.release(reservation);
            throw new ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT,
                    "Préparation indisponible. Aucun crédit n’a été débité.", e);
        }
    }

    @PostMapping("/interview-kit")
    public Map<String, Object> interviewKit(
            @AuthenticationPrincipal User user,
            @jakarta.validation.Valid @RequestBody InterviewKitRequest body
    ) {
        if (user.getRole() != Role.RECRUTEUR && user.getRole() != Role.ADMINISTRATEUR) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Réservé aux recruteurs");
        }
        AiUsageLog reservation = aiCreditService.reserve(user, "interview-kit");
        try {
            LlmProvider.LlmResult llm = llmProvider.complete(
                    "Génère une grille d'entretien structurée en français. Pas d'identité candidat. Pas de markdown : aucun dièse, aucun astérisque.",
                    "Poste=" + nullSafe(body.jobTitle()) + " ; compétences=" + nullSafe(body.skills())
                            + ". 5 à 7 questions discriminantes."
            );
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("title", "Grille d’entretien");
            m.put("questions", aiCreditService.interviewKit(body.jobTitle(), body.skills()));
            m.put("note", "Cliquez une question pour voir ce qu’il faut écouter. À adapter avant l’entretien.");
            m.put("provider", llm.provider());
            m.put("success", true);
            m.put("credits", settle(user, reservation, llm));
            return m;
        } catch (ResponseStatusException e) {
            aiCreditService.release(reservation);
            throw e;
        } catch (Exception e) {
            aiCreditService.release(reservation);
            throw new ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT,
                    "Grille indisponible. Aucun crédit n’a été débité.", e);
        }
    }

    @PostMapping("/refusal-draft")
    public Map<String, Object> refusalDraft(
            @AuthenticationPrincipal User user,
            @jakarta.validation.Valid @RequestBody RefusalDraftRequest body
    ) {
        if (user.getRole() != Role.RECRUTEUR && user.getRole() != Role.ADMINISTRATEUR) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Réservé aux recruteurs");
        }
        AiUsageLog reservation = aiCreditService.reserve(user, "refusal-draft");
        try {
            LlmProvider.LlmResult llm = llmProvider.complete(
                    "Rédige un brouillon de message de refus professionnel en français. Pas de nom ni email. Pas de markdown : aucun dièse, aucun astérisque.",
                    "Motif catalogue=" + nullSafe(body.reasonCode()) + " (" + nullSafe(body.reasonLabel())
                            + ") ; poste=" + nullSafe(body.jobTitle())
                            + ". Message respectueux, court, sans promesse."
            );
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("title", "Brouillon de refus");
            m.put("content", llm.text());
            m.put("provider", llm.provider());
            m.put("success", true);
            m.put("note", "Proposition — le recruteur valide avant envoi.");
            m.put("credits", settle(user, reservation, llm));
            return m;
        } catch (ResponseStatusException e) {
            aiCreditService.release(reservation);
            throw e;
        } catch (Exception e) {
            aiCreditService.release(reservation);
            throw new ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT,
                    "Brouillon indisponible. Aucun crédit n’a été débité.", e);
        }
    }

    /**
     * Synthèse fidèle du CV. Échec / timeout → libération du crédit, jamais de succès factice.
     */
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
        AiUsageLog reservation = aiCreditService.reserve(user, "cv-summary");
        try {
            boolean recruiter = user.getRole() == Role.RECRUTEUR;
            LlmProvider.LlmResult llm = llmProvider.complete(
                    recruiter
                            ? "Tu résumes un CV pour un recruteur. Français, 6 lignes maximum, sans inventer. Structure : métier probable, expériences, compétences, point d'attention."
                            : "Tu résumes le CV de la personne qui te parle. Français, tutoiement, 6 lignes maximum, sans inventer. Termine par une seule amélioration concrète.",
                    cv
            );
            if (llm == null || llm.text() == null || llm.text().isBlank()) {
                throw new ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT,
                        "La synthèse a expiré ou est vide. Aucun crédit n’a été débité.");
            }
            // Fallback local explicite : ne pas faire passer pour une analyse réussie
            String provider = llm.provider() == null ? "local" : llm.provider();
            if ("local".equalsIgnoreCase(provider) || provider.toLowerCase().startsWith("local")) {
                aiCreditService.release(reservation);
                Map<String, Object> degraded = new LinkedHashMap<>();
                degraded.put("title", "Synthèse indisponible");
                degraded.put("content", "");
                degraded.put("provider", provider);
                degraded.put("success", false);
                degraded.put("degraded", true);
                degraded.put("message",
                        "Le fournisseur IA n’a pas pu produire de synthèse. Aucun crédit n’a été débité. "
                                + "Réessayez plus tard ou vérifiez que le texte du CV est lisible.");
                degraded.put("credits", creditMap(user, aiCreditService.status(user)));
                return degraded;
            }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("title", "Synthèse du CV");
            m.put("content", llm.text());
            m.put("provider", provider);
            m.put("success", true);
            m.put("credits", settle(user, reservation, llm));
            return m;
        } catch (ResponseStatusException e) {
            aiCreditService.release(reservation);
            throw e;
        } catch (Exception e) {
            aiCreditService.release(reservation);
            throw new ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT,
                    "La synthèse a échoué ou a expiré. Aucun crédit n’a été débité.", e);
        }
    }

    @PostMapping("/coach")
    public Map<String, Object> coach(
            @AuthenticationPrincipal User user,
            @jakarta.validation.Valid @RequestBody CoachRequest body
    ) {
        if (user.getRole() != Role.CANDIDAT && user.getRole() != Role.ADMINISTRATEUR) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Réservé aux candidats");
        }
        AiUsageLog reservation = aiCreditService.reserve(user, "coach");
        try {
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
            m.put("success", true);
            m.put("credits", settle(user, reservation, llm));
            return m;
        } catch (ResponseStatusException e) {
            aiCreditService.release(reservation);
            throw e;
        } catch (Exception e) {
            aiCreditService.release(reservation);
            throw new ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT,
                    "Coach indisponible. Aucun crédit n’a été débité.", e);
        }
    }

    @PostMapping("/match-explain")
    public Map<String, Object> matchExplain(
            @AuthenticationPrincipal User user,
            @jakarta.validation.Valid @RequestBody MatchExplainRequest body
    ) {
        AiUsageLog reservation = aiCreditService.reserve(user, "match-explain");
        try {
            LlmProvider.LlmResult llm = llmProvider.complete(
                    "Explique un écart de matching en français clair. Pas d'identité. Pas de markdown : aucun dièse, aucun astérisque.",
                    "Compétences candidat=" + nullSafe(body.skills())
                            + " ; compétences offre=" + nullSafe(body.requiredSkills())
                            + ". Explique les forces et manques, suggère améliorations."
            );
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("title", "Explication du matching");
            m.put("content", llm.text());
            m.put("provider", llm.provider());
            m.put("success", true);
            m.put("credits", settle(user, reservation, llm));
            return m;
        } catch (ResponseStatusException e) {
            aiCreditService.release(reservation);
            throw e;
        } catch (Exception e) {
            aiCreditService.release(reservation);
            throw new ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT,
                    "Explication indisponible. Aucun crédit n’a été débité.", e);
        }
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

    /**
     * B2 : le crédit est réservé (débité) avant l'appel, puis liquidé après. Un appel qui échoue rend son
     * crédit ; deux appels simultanés sur le dernier crédit ne laissent qu'un gagnant (402 pour l'autre).
     */
    private Map<String, Object> settle(User user, AiUsageLog reservation, LlmProvider.LlmResult llm) {
        aiCreditService.settle(reservation, llm);
        return creditMap(user, aiCreditService.status(user));
    }

    private Map<String, Object> creditMap(User user, AiCreditService.CreditStatus s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("plan", s.plan());
        m.put("planLabel", s.planLabel());
        m.put("monthlyMad", s.monthlyMad());
        m.put("limit", s.limit());
        m.put("used", s.used());
        m.put("remaining", s.remaining());
        m.put("costPerAction", s.costPerAction());
        m.put("daysToReset", s.daysToReset());
        m.put("premium", s.premiumUnlimited());
        m.put("allowed", s.allowed());
        List<Map<String, Object>> history = new ArrayList<>();
        for (AiCreditService.FeatureUse use : aiCreditService.monthlyByFeature(user)) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("feature", use.feature());
            row.put("actions", use.actions());
            row.put("credits", use.credits());
            history.add(row);
        }
        m.put("byFeature", history);
        return m;
    }

    private static String nullSafe(String s) {
        return s == null ? "" : s.trim();
    }
}
