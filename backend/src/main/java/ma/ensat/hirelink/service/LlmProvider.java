package ma.ensat.hirelink.service;

/** Interface CDC Annexe C — le LLM est isolé derrière cette porte. */
public interface LlmProvider {
    record LlmResult(String text, String provider, String model, int charsIn, int charsOut, boolean success) {}

    /** Prompt sans PII (R15). */
    LlmResult complete(String systemPrompt, String userPrompt);
}
