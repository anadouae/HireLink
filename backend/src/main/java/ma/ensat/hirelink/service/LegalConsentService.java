package ma.ensat.hirelink.service;

import ma.ensat.hirelink.model.LegalAcceptance;
import ma.ensat.hirelink.model.User;
import ma.ensat.hirelink.repository.LegalAcceptanceRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class LegalConsentService {

    private final LegalAcceptanceRepository repository;
    private final AuditService audit;

    @Value("${hirelink.legal.terms-version:cgu:2026-11-12}")
    private String termsVersion;

    @Value("${hirelink.legal.privacy-version:privacy:2026-11-12}")
    private String privacyVersion;

    public LegalConsentService(LegalAcceptanceRepository repository, AuditService audit) {
        this.repository = repository;
        this.audit = audit;
    }

    public String currentTermsVersion() {
        return termsVersion;
    }

    public String currentPrivacyVersion() {
        return privacyVersion;
    }

    public Map<String, Object> publicVersions() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("termsVersion", termsVersion);
        m.put("privacyVersion", privacyVersion);
        m.put("termsPath", "legal/cgu.html");
        m.put("privacyPath", "legal/confidentialite.html");
        m.put("marketingEnabled", false);
        m.put("draftNotice", "Documents pédagogiques — textes à valider avant usage juridique définitif.");
        return m;
    }

    /** Refuse la création de compte si les versions obligatoires ne correspondent pas aux versions courantes. */
    public void assertSignupConsents(String acceptTermsVersion, String acceptPrivacyVersion) {
        if (acceptTermsVersion == null || acceptTermsVersion.isBlank()
                || acceptPrivacyVersion == null || acceptPrivacyVersion.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Acceptez les Conditions générales et la Politique de confidentialité pour créer un compte.");
        }
        if (!termsVersion.equals(acceptTermsVersion.trim())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Version des CGU incorrecte ou obsolète. Rouvrez le document et réessayez.");
        }
        if (!privacyVersion.equals(acceptPrivacyVersion.trim())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Version de la Politique de confidentialité incorrecte ou obsolète. Rouvrez le document et réessayez.");
        }
    }

    @Transactional
    public void recordSignupConsents(User user, String acceptTermsVersion, String acceptPrivacyVersion, String source) {
        assertSignupConsents(acceptTermsVersion, acceptPrivacyVersion);
        String src = source == null || source.isBlank() ? "REGISTER" : source.trim().toUpperCase();
        save(user, LegalAcceptance.PURPOSE_TERMS, termsVersion, src);
        save(user, LegalAcceptance.PURPOSE_PRIVACY, privacyVersion, src);
        audit.ok("CONSENT_ACCEPTED", user, "USER", user.getId(),
                "TERMS=" + termsVersion + ";PRIVACY=" + privacyVersion + ";src=" + src);
    }

    private void save(User user, String purpose, String version, String source) {
        if (repository.existsByUserIdAndPurposeAndDocumentVersionAndGrantedTrue(user.getId(), purpose, version)) {
            return;
        }
        LegalAcceptance row = new LegalAcceptance();
        row.setUserId(user.getId());
        row.setPurpose(purpose);
        row.setDocumentVersion(version);
        row.setSource(source);
        row.setGranted(true);
        row.setAcceptedAt(Instant.now());
        repository.save(row);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listFor(User user) {
        return repository.findByUserIdOrderByAcceptedAtDesc(user.getId()).stream().map(a -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("purpose", a.getPurpose());
            m.put("documentVersion", a.getDocumentVersion());
            m.put("source", a.getSource());
            m.put("granted", a.isGranted());
            m.put("acceptedAt", a.getAcceptedAt() == null ? null : a.getAcceptedAt().toString());
            m.put("current", purposeMatchesCurrent(a));
            return m;
        }).toList();
    }

    private boolean purposeMatchesCurrent(LegalAcceptance a) {
        if (LegalAcceptance.PURPOSE_TERMS.equals(a.getPurpose())) {
            return termsVersion.equals(a.getDocumentVersion()) && a.isGranted();
        }
        if (LegalAcceptance.PURPOSE_PRIVACY.equals(a.getPurpose())) {
            return privacyVersion.equals(a.getDocumentVersion()) && a.isGranted();
        }
        return false;
    }

    @Transactional(readOnly = true)
    public boolean hasCurrentMandatory(User user) {
        boolean terms = repository.existsByUserIdAndPurposeAndDocumentVersionAndGrantedTrue(
                user.getId(), LegalAcceptance.PURPOSE_TERMS, termsVersion);
        boolean privacy = repository.existsByUserIdAndPurposeAndDocumentVersionAndGrantedTrue(
                user.getId(), LegalAcceptance.PURPOSE_PRIVACY, privacyVersion);
        return terms && privacy;
    }
}
