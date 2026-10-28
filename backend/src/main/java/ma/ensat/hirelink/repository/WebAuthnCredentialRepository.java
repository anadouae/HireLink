package ma.ensat.hirelink.repository;

import ma.ensat.hirelink.model.WebAuthnCredential;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WebAuthnCredentialRepository extends JpaRepository<WebAuthnCredential, Long> {
    List<WebAuthnCredential> findByUserIdOrderByCreatedAtDesc(Long userId);
    List<WebAuthnCredential> findByUserHandle(String userHandle);
    Optional<WebAuthnCredential> findByCredentialId(String credentialId);
    void deleteByUserId(Long userId);
}
