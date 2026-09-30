package ma.ensat.hirelink.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.*;

/**
 * Appel optionnel Gemini (ou compatible) si hirelink.llm.api-key est défini.
 * En cas d'échec / clé absente : LocalLlmProvider.
 */
@Primary
@Component
public class HttpLlmProvider implements LlmProvider {
    private final LocalLlmProvider local;
    private final RestTemplate http = new RestTemplate();

    @Value("${hirelink.llm.api-key:}")
    private String apiKey;

    @Value("${hirelink.llm.provider:gemini}")
    private String provider;

    @Value("${hirelink.llm.model:gemini-1.5-flash}")
    private String model;

    public HttpLlmProvider(LocalLlmProvider local) {
        this.local = local;
    }

    @Override
    public LlmResult complete(String systemPrompt, String userPrompt) {
        if (apiKey == null || apiKey.isBlank()) {
            return local.complete(systemPrompt, userPrompt);
        }
        try {
            if ("mistral".equalsIgnoreCase(provider)) {
                return callMistral(systemPrompt, userPrompt);
            }
            return callGemini(systemPrompt, userPrompt);
        } catch (Exception e) {
            LlmResult fb = local.complete(systemPrompt, userPrompt);
            return new LlmResult(fb.text() + "\n\n[Fallback local — fournisseur indisponible]",
                    "local-fallback", "rules-fr", fb.charsIn(), fb.charsOut(), true);
        }
    }

    private LlmResult callGemini(String systemPrompt, String userPrompt) {
        String url = "https://generativelanguage.googleapis.com/v1beta/models/"
                + model + ":generateContent?key=" + apiKey;
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("contents", List.of(Map.of("parts", List.of(Map.of("text",
                (systemPrompt == null ? "" : systemPrompt) + "\n\n" + (userPrompt == null ? "" : userPrompt))))));
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<Map> res = http.exchange(url, HttpMethod.POST, new HttpEntity<>(body, headers), Map.class);
        String text = extractGemini(res.getBody());
        int cin = (systemPrompt == null ? 0 : systemPrompt.length()) + (userPrompt == null ? 0 : userPrompt.length());
        return new LlmResult(text, "gemini", model, cin, text.length(), true);
    }

    @SuppressWarnings("unchecked")
    private String extractGemini(Map body) {
        if (body == null) return "";
        List cands = (List) body.get("candidates");
        if (cands == null || cands.isEmpty()) return "";
        Map first = (Map) cands.get(0);
        Map content = (Map) first.get("content");
        List parts = (List) content.get("parts");
        Map part0 = (Map) parts.get(0);
        return String.valueOf(part0.get("text"));
    }

    private LlmResult callMistral(String systemPrompt, String userPrompt) {
        String url = "https://api.mistral.ai/v1/chat/completions";
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model == null || model.isBlank() ? "mistral-small-latest" : model);
        body.put("messages", List.of(
                Map.of("role", "system", "content", systemPrompt == null ? "" : systemPrompt),
                Map.of("role", "user", "content", userPrompt == null ? "" : userPrompt)
        ));
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);
        ResponseEntity<Map> res = http.exchange(url, HttpMethod.POST, new HttpEntity<>(body, headers), Map.class);
        String text = extractMistral(res.getBody());
        int cin = (systemPrompt == null ? 0 : systemPrompt.length()) + (userPrompt == null ? 0 : userPrompt.length());
        return new LlmResult(text, "mistral", String.valueOf(body.get("model")), cin, text.length(), true);
    }

    @SuppressWarnings("unchecked")
    private String extractMistral(Map body) {
        if (body == null) return "";
        List choices = (List) body.get("choices");
        if (choices == null || choices.isEmpty()) return "";
        Map c0 = (Map) choices.get(0);
        Map msg = (Map) c0.get("message");
        return String.valueOf(msg.get("content"));
    }
}
