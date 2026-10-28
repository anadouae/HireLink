package ma.ensat.hirelink.service;

import ma.ensat.hirelink.model.*;
import ma.ensat.hirelink.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/** F21 — export des données personnelles. */
@Service
public class PrivacyService {
    private final UserRepository userRepository;
    private final ApplicationRepository applicationRepository;
    private final ConsentRecordRepository consentRecordRepository;
    private final MembershipRepository membershipRepository;
    private final CvStorage cvStorage;
    private final WebAuthnCredentialRepository webAuthnCredentials;
    private final InAppNotificationRepository notificationRepository;

    public PrivacyService(UserRepository userRepository,
                          ApplicationRepository applicationRepository,
                          ConsentRecordRepository consentRecordRepository,
                          MembershipRepository membershipRepository,
                          CvStorage cvStorage,
                          WebAuthnCredentialRepository webAuthnCredentials,
                          InAppNotificationRepository notificationRepository) {
        this.membershipRepository = membershipRepository;
        this.cvStorage = cvStorage;
        this.webAuthnCredentials = webAuthnCredentials;
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
        this.applicationRepository = applicationRepository;
        this.consentRecordRepository = consentRecordRepository;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> exportFor(User user) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("email", user.getEmail());
        m.put("fullName", user.getFullName());
        m.put("role", user.getRole().name());
        m.put("skills", user.getSkills());
        m.put("soughtRoles", user.getSoughtRoles());
        m.put("preferredLocations", user.getPreferredLocations());
        m.put("preferredContracts", user.getPreferredContracts());
        m.put("experienceLevel", user.getExperienceLevel());
        m.put("company", user.getCompany());
        m.put("companyIce", user.getCompanyIce());
        List<Map<String, Object>> apps = new ArrayList<>();
        for (Application a : applicationRepository.findByCandidateId(user.getId())) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", a.getId());
            row.put("offer", a.getJobOffer().getTitle());
            row.put("status", a.getStatus().name());
            row.put("submittedAt", a.getSubmittedAt().toString());
            row.put("matchScore", a.getMatchScore());
            apps.add(row);
        }
        m.put("applications", apps);
        List<Map<String, Object>> consents = new ArrayList<>();
        for (ConsentRecord c : consentRecordRepository.findByUserId(user.getId())) {
            consents.add(Map.of("purpose", c.getPurpose(), "granted", c.isGranted(), "at", c.getCreatedAt().toString()));
        }
        m.put("consents", consents);
        m.put("note", "Export F21 — loi 09-08 / droits d'accès");
        return m;
    }

    /**
     * Droit à l'effacement : anonymise le compte (les candidatures restent pour l'historique des recruteurs mais ne sont
     * plus rattachées à une personne identifiable), supprime CV, passkeys, notifications, consentements et secrets MFA.
     */
    @Transactional
    public void erase(User user) {
        var memberships = membershipRepository.findByUserIdAndActiveTrue(user.getId());
        for (var m : memberships) {
            boolean owner = m.getOrgRole() == ma.ensat.hirelink.model.OrgRole.OWNER;
            long others = membershipRepository.findByOrganizationIdAndActiveTrue(m.getOrganization().getId()).stream()
                    .filter(x -> !x.getUser().getId().equals(user.getId())).count();
            if (owner && others > 0) {
                throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT,
                        "Vous êtes Owner d'une organisation avec d'autres membres : retirez-les ou transférez la propriété avant d'effacer votre compte.");
            }
        }
        for (var m : memberships) {
            m.setActive(false);
            membershipRepository.save(m);
        }
        if (user.getCvStorageName() != null) cvStorage.delete(user.getCvStorageName());
        webAuthnCredentials.deleteByUserId(user.getId());
        consentRecordRepository.findByUserId(user.getId()).forEach(consentRecordRepository::delete);
        notificationRepository.findByUserIdOrderByCreatedAtDescIdDesc(user.getId()).forEach(notificationRepository::delete);
        user.setEmail("effacé-" + user.getId() + "@anonymise.invalid");
        user.setFullName("Compte supprimé");
        user.setFirstName(null);
        user.setLastName(null);
        user.setPhone(null);
        user.setPhoneCountry(null);
        user.setCity(null);
        user.setNationality(null);
        user.setBirthDate(null);
        user.setGender(null);
        user.setSchool(null);
        user.setSkills(null);
        user.setSoughtRoles(null);
        user.setPreferredLocations(null);
        user.setCvFileName(null);
        user.setCvStorageName(null);
        user.setCvSections(null);
        user.setGoogleSub(null);
        user.setPasswordHash(null);
        user.setMfaEnabled(false);
        user.setMfaSecretEnc(null);
        user.setMfaBackupHashes(null);
        user.setActive(false);
        userRepository.save(user);
    }

    @Transactional
    public ConsentRecord recordConsent(User user, String purpose, boolean granted) {
        ConsentRecord c = new ConsentRecord();
        c.setUserId(user.getId());
        c.setPurpose(purpose);
        c.setGranted(granted);
        return consentRecordRepository.save(c);
    }
}
