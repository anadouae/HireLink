package ma.ensat.hirelink.integration;

import ma.ensat.hirelink.model.PlanTier;
import ma.ensat.hirelink.model.Role;
import ma.ensat.hirelink.model.User;
import ma.ensat.hirelink.repository.UserRepository;
import ma.ensat.hirelink.service.CvImportCommitService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CvImportTransactionProofTest {

    @Autowired UserRepository users;
    @Autowired JdbcTemplate jdbc;
    @Autowired CvImportCommitService commitService;

    @Test
    void failedMidCommitLeavesAllDataAndDraftTablesUnchanged() throws Exception {
        User candidate = new User();
        candidate.setEmail("partie-a-" + System.nanoTime() + "@test.hirelink.local");
        candidate.setFullName("Avant Partie A");
        candidate.setFirstName("Avant");
        candidate.setLastName("Partie A");
        candidate.setRole(Role.CANDIDAT);
        candidate.setPlanTier(PlanTier.GRATUIT);
        candidate.setEmailVerified(true);
        candidate.setPasswordHash("$2a$04$unused");
        candidate.setSkills("Java");
        candidate.setProfileExperiences("[{\"title\":\"Java Developer\",\"company\":\"Atlas\",\"startDate\":\"2022\",\"endDate\":\"2023\"}]");
        candidate.setProfileEducations("[{\"school\":\"ENSA Tétouan\",\"degree\":\"Cycle ingénieur\",\"domain\":\"Informatique\",\"startDate\":\"2020\",\"endDate\":\"2023\"}]");
        candidate.setCvSections("{\"skills\":\"Java\"}");
        candidate.setCvFileName("avant.pdf");
        candidate.setCvStorageName("profiles/avant.pdf");
        candidate = users.saveAndFlush(candidate);
        final User candidateRef = candidate;

        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS cv_import_drafts (
                    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
                    user_id BIGINT NOT NULL,
                    cv_file_name VARCHAR(255) NULL,
                    cv_storage_name VARCHAR(255) NULL,
                    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
                    step VARCHAR(16) NOT NULL DEFAULT 'IMPORT',
                    payload_json LONGTEXT NULL,
                    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
                    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
                    expires_at DATETIME(6) NULL
                ) ENGINE=InnoDB
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS cv_import_items (
                    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
                    draft_id BIGINT NOT NULL,
                    section_type VARCHAR(32) NOT NULL,
                    item_key VARCHAR(120) NULL,
                    source_text TEXT NULL,
                    target_field VARCHAR(120) NULL,
                    proposed_value_json LONGTEXT NULL,
                    current_value_json LONGTEXT NULL,
                    confidence DECIMAL(5,4) NULL,
                    state VARCHAR(20) NOT NULL DEFAULT 'DETECTED',
                    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
                    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
                ) ENGINE=InnoDB
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS profile_versions (
                    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
                    user_id BIGINT NOT NULL,
                    reason VARCHAR(64) NOT NULL DEFAULT 'CV_IMPORT',
                    snapshot_json LONGTEXT NOT NULL,
                    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
                    expires_at DATETIME(6) NOT NULL
                ) ENGINE=InnoDB
                """);

        jdbc.update("""
                INSERT INTO cv_import_drafts
                (user_id, cv_file_name, cv_storage_name, status, step, payload_json)
                VALUES (?, 'ancien.pdf', 'drafts/ancien.pdf', 'DRAFT', 'REVIEW', '{}')
                """, candidate.getId());
        Number draftId = jdbc.queryForObject(
                "SELECT id FROM cv_import_drafts WHERE user_id = ? ORDER BY id DESC LIMIT 1",
                Number.class, candidate.getId());
        jdbc.update("""
                INSERT INTO cv_import_items
                (draft_id, section_type, item_key, proposed_value_json, current_value_json, state)
                VALUES (?, 'skills', 'skills', '\"Docker\"', '\"Java\"', 'ACCEPTED')
                """, draftId);
        jdbc.update("""
                INSERT INTO profile_versions
                (user_id, reason, snapshot_json, expires_at)
                VALUES (?, 'PREVIOUS_TEST', '{}', DATE_ADD(CURRENT_TIMESTAMP(6), INTERVAL 7 DAY))
                """, candidate.getId());

        Map<String, Object> before = snapshot(candidate.getId(), draftId);
        String experiences = IntStream.range(0, 31)
                .mapToObj(i -> "{\"title\":\"X" + i + "\",\"company\":\"C\",\"startDate\":\"2020\",\"endDate\":\"2021\"}")
                .collect(Collectors.joining(",", "[", "]"));
        String payload = "{\"profile\":{\"firstName\":\"Après\",\"skills\":\"Java, Docker\"},"
                + "\"career\":{\"experiences\":" + experiences + ",\"educations\":[]},"
                + "\"sections\":{\"skills\":\"Java, Docker\"},"
                + "\"items\":[{\"id\":\"skills\",\"status\":\"ACCEPTED\",\"proposed\":\"Docker\",\"current\":\"Java\"}],"
                + "\"draftId\":" + draftId + "}";
        MockMultipartFile file = new MockMultipartFile(
                "file", "nouveau.pdf", "application/pdf",
                "%PDF-1.4\n1 0 obj\n<<>>\nendobj\n%%EOF".getBytes(StandardCharsets.US_ASCII));

        assertThrows(ResponseStatusException.class, () -> commitService.commit(candidateRef, file, payload));

        Map<String, Object> after = snapshot(candidate.getId(), draftId);
        assertEquals(before, after);

        System.out.println("A1_BEFORE=" + before);
        System.out.println("A1_AFTER=" + after);
        System.out.println("A1_IDENTICAL=true");
    }

    private Map<String, Object> snapshot(Long userId, Number draftId) {
        users.flush();
        Map<String, Object> profile = jdbc.queryForMap("""
                SELECT id,skills,profile_experiences,profile_educations,cv_sections,
                       cv_file_name,cv_storage_name,first_name,last_name
                FROM users WHERE id=?
                """, userId);
        List<Map<String, Object>> drafts = jdbc.queryForList("""
                SELECT id,cv_file_name,cv_storage_name,status,step,payload_json
                FROM cv_import_drafts WHERE user_id=? ORDER BY id
                """, userId);
        List<Map<String, Object>> items = jdbc.queryForList("""
                SELECT id,section_type,item_key,proposed_value_json,current_value_json,state
                FROM cv_import_items WHERE draft_id=? ORDER BY id
                """, draftId);
        List<Map<String, Object>> versions = jdbc.queryForList("""
                SELECT id,reason,snapshot_json,expires_at
                FROM profile_versions WHERE user_id=? ORDER BY id
                """, userId);
        return Map.of(
                "profile", profile,
                "experiences", profile.get("profile_experiences"),
                "formations", profile.get("profile_educations"),
                "drafts", drafts,
                "items", items,
                "versions", versions
        );
    }
}
