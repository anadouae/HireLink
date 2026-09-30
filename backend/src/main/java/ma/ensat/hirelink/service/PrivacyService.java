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

    public PrivacyService(UserRepository userRepository,
                          ApplicationRepository applicationRepository,
                          ConsentRecordRepository consentRecordRepository) {
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

    @Transactional
    public ConsentRecord recordConsent(User user, String purpose, boolean granted) {
        ConsentRecord c = new ConsentRecord();
        c.setUserId(user.getId());
        c.setPurpose(purpose);
        c.setGranted(granted);
        return consentRecordRepository.save(c);
    }
}
