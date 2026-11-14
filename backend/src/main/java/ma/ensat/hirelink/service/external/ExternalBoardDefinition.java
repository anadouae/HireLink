package ma.ensat.hirelink.service.external;

/** Definition allowlist d'un board ATS public (pas un partenariat). */
public record ExternalBoardDefinition(
        String provider,
        String boardKey,
        String companyName,
        String referenceUrl,
        String regionHint,
        boolean enabled,
        String attributionNotes,
        String verificationNotes
) {
    public String sourceCode() {
        return provider == null ? "" : provider.trim().toUpperCase();
    }
}