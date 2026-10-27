package ma.ensat.hirelink.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.*;

/**
 * Multi-provider F15 : Gemini · Groq · Mistral.
 * Chaîne de secours si un fournisseur échoue. Sans aucune clé → LocalLlmProvider.
 * R15 : prompts sans PII (identité / email / téléphone).
 */
@Primary
@Component
public class HttpLlmProvider implements LlmProvider {

    private final LocalLlmProvider local;
    private final RestTemplate http = new RestTemplate();

    /** Provider principal : gemini | groq | mistral */
    @Value("${hirelink.llm.provider:gemini}")
    private String primaryProvider;

    /** Clé legacy (utilisée pour le provider principal si clé dédiée vide). */
    @Value("${hirelink.llm.api-key:}")
    private String legacyApiKey;

    @Value("${hirelink.llm.gemini.api-key:}")
    private String geminiKey;

    @Value("${hirelink.llm.groq.api-key:}")
    private String groqKey;

    @Value("${hirelink.llm.mistral.api-key:}")
    private String mistralKey;

    @Value("${hirelink.llm.gemini.model:gemini-3.5-flash-lite}")
    private String geminiModel;

    @Value("${hirelink.llm.groq.model:qwen/qwen3.8-27b}")
    private String groqModel;

    @Value("${hirelink.llm.mistral.model:mistral-small-latest}")
    private String mistralModel;

    /** Ex. gemini,groq,mistral */
    @Value("${hirelink.llm.fallback-chain:gemini,groq,mistral}")
    private String fallbackChain;

    public HttpLlmProvider(LocalLlmProvider local) {
        this.local = local;
    }

    @Override
    public LlmResult complete(String systemPrompt, String userPrompt) {
        List<String> chain = buildChain();
        if (chain.isEmpty()) {
            return local.complete(systemPrompt, userPrompt);
        }
        Exception last = null;
        for (String p : chain) {
            try {
                return switch (p) {
                    case "gemini" -> callGemini(systemPrompt, userPrompt);
                    case "groq" -> callGroq(systemPrompt, userPrompt);
                    case "mistral" -> callMistral(systemPrompt, userPrompt);
                    default -> throw new IllegalArgumentException("Provider inconnu: " + p);
                };
            } catch (Exception e) {
                last = e;
            }
        }
            LlmResult fb = local.complete(systemPrompt, userPrompt);
        String note = last == null ? "" : " (" + last.getClass().getSimpleName() + ")";
        return new LlmResult(
                fb.text() + "\n\n[Fallback local — cloud indisponible" + note + "]",
                "local-fallback",
                "rules-fr",
                fb.charsIn(),
                fb.charsOut(),
                true
        );
    }

    /** Statut masqué pour /ai/status — jamais la clé en clair. */
    public Map<String, Object> statusMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("primary", primaryProvider);
        m.put("fallbackChain", buildChain());
        m.put("gemini", Map.of(
                "configured", hasKey(resolveGeminiKey()),
                "model", geminiModel,
                "hint", mask(resolveGeminiKey())
        ));
        m.put("groq", Map.of(
                "configured", hasKey(resolveGroqKey()),
                "model", groqModel,
                "hint", mask(resolveGroqKey())
        ));
        m.put("mistral", Map.of(
                "configured", hasKey(resolveMistralKey()),
                "model", mistralModel,
                "hint", mask(resolveMistralKey())
        ));
        m.put("mode", buildChain().isEmpty() ? "LOCAL_FALLBACK" : "CLOUD");
        m.put("howTo", "Colle tes clés dans application-local.properties (gitignored) ou dis-les en chat.");
        return m;
    }

    private List<String> buildChain() {
        LinkedHashSet<String> ordered = new LinkedHashSet<>();
        String primary = primaryProvider == null ? "gemini" : primaryProvider.trim().toLowerCase();
        if (hasKeyFor(primary)) ordered.add(primary);
        for (String part : fallbackChain.split("[,;\\s]+")) {
            if (part.isBlank()) continue;
            String p = part.trim().toLowerCase();
            if (hasKeyFor(p)) ordered.add(p);
        }
        return new ArrayList<>(ordered);
    }

    private boolean hasKeyFor(String provider) {
        return switch (provider) {
            case "gemini" -> hasKey(resolveGeminiKey());
            case "groq" -> hasKey(resolveGroqKey());
            case "mistral" -> hasKey(resolveMistralKey());
            default -> false;
        };
    }

    private String resolveGeminiKey() {
        return firstNonBlank(geminiKey, "gemini".equalsIgnoreCase(primaryProvider) ? legacyApiKey : "",
                System.getenv("HIRELINK_GEMINI_API_KEY"), System.getenv("GEMINI_API_KEY"));
    }

    private String resolveGroqKey() {
        return firstNonBlank(groqKey, "groq".equalsIgnoreCase(primaryProvider) ? legacyApiKey : "",
                System.getenv("HIRELINK_GROQ_API_KEY"), System.getenv("GROQ_API_KEY"));
    }

    private String resolveMistralKey() {
        return firstNonBlank(mistralKey, "mistral".equalsIgnoreCase(primaryProvider) ? legacyApiKey : "",
                System.getenv("HIRELINK_MISTRAL_API_KEY"), System.getenv("MISTRAL_API_KEY"));
    }

    private static String firstNonBlank(String... vals) {
        if (vals == null) return "";
        for (String v : vals) {
            if (v != null && !v.isBlank()) return v.trim();
        }
        return "";
    }

    private static boolean hasKey(String k) {
        return k != null && !k.isBlank();
    }

    private static String mask(String k) {
        if (!hasKey(k)) return "(vide)";
        if (k.length() <= 8) return "****";
        return k.substring(0, 4) + "…" + k.substring(k.length() - 4);
    }

    private LlmResult callGemini(String systemPrompt, String userPrompt) {
        String key = resolveGeminiKey();
        if (!hasKey(key)) throw new IllegalStateException("Clé Gemini absente");
        String url = "https://generativelanguage.googleapis.com/v1beta/models/"
                + geminiModel + ":generateContent?key=" + key;
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("contents", List.of(Map.of("parts", List.of(Map.of("text",
                (systemPrompt == null ? "" : systemPrompt) + "\n\n" + (userPrompt == null ? "" : userPrompt))))));
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        @SuppressWarnings("rawtypes")
        ResponseEntity<Map> res = http.exchange(url, HttpMethod.POST, new HttpEntity<>(body, headers), Map.class);
        String text = extractGemini(res.getBody());
        if (text == null || text.isBlank()) throw new IllegalStateException("Réponse Gemini vide");
        int cin = len(systemPrompt) + len(userPrompt);
        return new LlmResult(text, "gemini", geminiModel, cin, text.length(), true);
    }

    @SuppressWarnings("unchecked")
    private String extractGemini(Map<?, ?> body) {
        if (body == null) return "";
        List<?> cands = (List<?>) body.get("candidates");
        if (cands == null || cands.isEmpty()) return "";
        Map<?, ?> first = (Map<?, ?>) cands.get(0);
        Map<?, ?> content = (Map<?, ?>) first.get("content");
        if (content == null) return "";
        List<?> parts = (List<?>) content.get("parts");
        if (parts == null || parts.isEmpty()) return "";
        Map<?, ?> part0 = (Map<?, ?>) parts.get(0);
        return String.valueOf(part0.get("text"));
    }

    /** Groq = API compatible OpenAI, free tier généreux. */
    private LlmResult callGroq(String systemPrompt, String userPrompt) {
        String key = resolveGroqKey();
        if (!hasKey(key)) throw new IllegalStateException("Clé Groq absente");
        return callOpenAiCompatible(
                "https://api.groq.com/openai/v1/chat/completions",
                key,
                groqModel,
                "groq",
                systemPrompt,
                userPrompt
        );
    }

    private LlmResult callMistral(String systemPrompt, String userPrompt) {
        String key = resolveMistralKey();
        if (!hasKey(key)) throw new IllegalStateException("Clé Mistral absente");
        return callOpenAiCompatible(
                "https://api.mistral.ai/v1/chat/completions",
                key,
                mistralModel,
                "mistral",
                systemPrompt,
                userPrompt
        );
    }

    private LlmResult callOpenAiCompatible(
            String url, String key, String model, String providerName,
            String systemPrompt, String userPrompt
    ) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("temperature", 0.4);
        body.put("messages", List.of(
                Map.of("role", "system", "content", systemPrompt == null ? "" : systemPrompt),
                Map.of("role", "user", "content", userPrompt == null ? "" : userPrompt)
        ));
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(key);
        @SuppressWarnings("rawtypes")
        ResponseEntity<Map> res = http.exchange(url, HttpMethod.POST, new HttpEntity<>(body, headers), Map.class);
        String text = extractOpenAi(res.getBody());
        if (text == null || text.isBlank()) throw new IllegalStateException("Réponse " + providerName + " vide");
        int cin = len(systemPrompt) + len(userPrompt);
        return new LlmResult(text, providerName, model, cin, text.length(), true);
    }

    @SuppressWarnings("unchecked")
    private String extractOpenAi(Map<?, ?> body) {
        if (body == null) return "";
        List<?> choices = (List<?>) body.get("choices");
        if (choices == null || choices.isEmpty()) return "";
        Map<?, ?> c0 = (Map<?, ?>) choices.get(0);
        Map<?, ?> msg = (Map<?, ?>) c0.get("message");
        if (msg == null) return "";
        return String.valueOf(msg.get("content"));
    }

    private static int len(String s) {
        return s == null ? 0 : s.length();
    }
}
