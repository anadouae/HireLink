package ma.ensat.hirelink.service;

import com.yubico.webauthn.*;
import com.yubico.webauthn.data.*;
import ma.ensat.hirelink.model.User;
import ma.ensat.hirelink.model.WebAuthnCredential;
import ma.ensat.hirelink.repository.UserRepository;
import ma.ensat.hirelink.repository.WebAuthnCredentialRepository;
import ma.ensat.hirelink.security.JwtService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Passkeys WebAuthn / FIDO2 (bibliothèque Yubico java-webauthn-server).
 * RP ID = « localhost » en développement (aucun HTTPS requis) ; en production : domaine réel via
 * HIRELINK_WEBAUTHN_RP_ID et HIRELINK_WEBAUTHN_ORIGINS. La vérification de l'utilisateur (biométrie / code PIN)
 * est exigée : une passkey vaut donc authentification à deux facteurs.
 */
@Service
public class WebAuthnService implements CredentialRepository {

    private record Pending<T>(T request, Long userId, Instant expiresAt) {}

    private final WebAuthnCredentialRepository repo;
    private final UserRepository users;
    private final byte[] handleKey;
    private final RelyingParty rp;
    private final SecureRandom random = new SecureRandom();
    private final Map<String, Pending<PublicKeyCredentialCreationOptions>> registrations = new ConcurrentHashMap<>();
    private final Map<String, Pending<AssertionRequest>> assertions = new ConcurrentHashMap<>();

    public WebAuthnService(
            WebAuthnCredentialRepository repo, UserRepository users, JwtService jwt,
            @Value("${hirelink.webauthn.rp-id:localhost}") String rpId,
            @Value("${hirelink.webauthn.rp-name:HireLink}") String rpName,
            @Value("${hirelink.webauthn.origins:http://localhost:5500,http://localhost:63342}") String origins
    ) {
        this.repo = repo;
        this.users = users;
        this.handleKey = jwt.deriveKey("webauthn-user-handle");
        this.rp = RelyingParty.builder()
                .identity(RelyingPartyIdentity.builder().id(rpId).name(rpName).build())
                .credentialRepository(this)
                .origins(Arrays.stream(origins.split(",")).map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toSet()))
                .build();
    }

    // ---------- inscription d'une passkey (utilisateur connecté) ----------

    public Map<String, Object> startRegistration(User user) {
        purge();
        UserIdentity identity = UserIdentity.builder()
                .name(user.getEmail()).displayName(user.getFullName() == null ? user.getEmail() : user.getFullName())
                .id(handleFor(user.getId())).build();
        PublicKeyCredentialCreationOptions options = rp.startRegistration(StartRegistrationOptions.builder()
                .user(identity)
                .authenticatorSelection(AuthenticatorSelectionCriteria.builder()
                        .residentKey(ResidentKeyRequirement.REQUIRED)
                        .userVerification(UserVerificationRequirement.REQUIRED).build())
                .build());
        String id = newId();
        registrations.put(id, new Pending<>(options, user.getId(), Instant.now().plusSeconds(300)));
        try {
            return Map.of("requestId", id, "publicKey", options.toCredentialsCreateJson());
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Passkey indisponible");
        }
    }

    @Transactional
    public WebAuthnCredential finishRegistration(User user, String requestId, String credentialJson, String name) {
        Pending<PublicKeyCredentialCreationOptions> p = registrations.remove(requestId == null ? "" : requestId);
        if (p == null || !p.userId().equals(user.getId()) || p.expiresAt().isBefore(Instant.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Demande expirée. Recommencez l'ajout de la passkey.");
        }
        try {
            RegistrationResult result = rp.finishRegistration(FinishRegistrationOptions.builder()
                    .request(p.request())
                    .response(PublicKeyCredential.parseRegistrationResponseJson(credentialJson))
                    .build());
            WebAuthnCredential c = new WebAuthnCredential();
            c.setUserId(user.getId());
            c.setCredentialId(result.getKeyId().getId().getBase64Url());
            c.setPublicKeyCose(result.getPublicKeyCose().getBase64Url());
            c.setSignatureCount(result.getSignatureCount());
            c.setUserHandle(handleFor(user.getId()).getBase64Url());
            String label = name == null || name.isBlank() ? "Passkey" : name.strip();
            c.setName(label.length() > 80 ? label.substring(0, 80) : label);
            return repo.save(c);
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Passkey refusée par le navigateur ou invalide.");
        }
    }

    // ---------- connexion par passkey (sans identifiant : « usernameless ») ----------

    public Map<String, Object> startLogin() {
        purge();
        AssertionRequest request = rp.startAssertion(StartAssertionOptions.builder()
                .userVerification(UserVerificationRequirement.REQUIRED).build());
        String id = newId();
        assertions.put(id, new Pending<>(request, null, Instant.now().plusSeconds(300)));
        try {
            return Map.of("requestId", id, "publicKey", request.toCredentialsGetJson());
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Passkey indisponible");
        }
    }

    /** Retourne l'utilisateur authentifié ; lève 401 (message générique) sinon. */
    @Transactional
    public User finishLogin(String requestId, String credentialJson) {
        Pending<AssertionRequest> p = assertions.remove(requestId == null ? "" : requestId);
        if (p == null || p.expiresAt().isBefore(Instant.now())) throw denied();
        try {
            AssertionResult result = rp.finishAssertion(FinishAssertionOptions.builder()
                    .request(p.request())
                    .response(PublicKeyCredential.parseAssertionResponseJson(credentialJson))
                    .build());
            if (!result.isSuccess() || !result.isSignatureCounterValid()) throw denied();
            WebAuthnCredential c = repo.findByCredentialId(result.getCredential().getCredentialId().getBase64Url()).orElseThrow(this::denied);
            c.setSignatureCount(result.getSignatureCount());
            c.setLastUsedAt(Instant.now());
            repo.save(c);
            return users.findById(c.getUserId()).orElseThrow(this::denied);
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw denied();
        }
    }

    @Transactional(readOnly = true)
    public List<WebAuthnCredential> list(Long userId) {
        return repo.findByUserIdOrderByCreatedAtDesc(userId);
    }

    @Transactional
    public boolean remove(Long userId, Long credentialRowId) {
        return repo.findById(credentialRowId).filter(c -> c.getUserId().equals(userId)).map(c -> {
            repo.delete(c);
            return true;
        }).orElse(false);
    }

    private ResponseStatusException denied() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Connexion par passkey refusée.");
    }

    private void purge() {
        Instant now = Instant.now();
        registrations.values().removeIf(p -> p.expiresAt().isBefore(now));
        assertions.values().removeIf(p -> p.expiresAt().isBefore(now));
    }

    private String newId() {
        byte[] raw = new byte[24];
        random.nextBytes(raw);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
    }

    private ByteArray handleFor(Long userId) {
        try {
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            mac.init(new javax.crypto.spec.SecretKeySpec(handleKey, "HmacSHA256"));
            return new ByteArray(mac.doFinal(("user:" + userId).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    // ---------- CredentialRepository (Yubico) ----------

    @Override
    public Set<PublicKeyCredentialDescriptor> getCredentialIdsForUsername(String username) {
        return users.findByEmailIgnoreCase(username).map(u -> repo.findByUserIdOrderByCreatedAtDesc(u.getId()).stream()
                .map(c -> PublicKeyCredentialDescriptor.builder().id(fromB64(c.getCredentialId())).build())
                .collect(Collectors.toSet())).orElseGet(Set::of);
    }

    @Override
    public Optional<ByteArray> getUserHandleForUsername(String username) {
        return users.findByEmailIgnoreCase(username).map(u -> handleFor(u.getId()));
    }

    @Override
    public Optional<String> getUsernameForUserHandle(ByteArray userHandle) {
        return repo.findByUserHandle(userHandle.getBase64Url()).stream().findFirst()
                .flatMap(c -> users.findById(c.getUserId())).map(User::getEmail);
    }

    @Override
    public Optional<RegisteredCredential> lookup(ByteArray credentialId, ByteArray userHandle) {
        return repo.findByCredentialId(credentialId.getBase64Url())
                .filter(c -> c.getUserHandle().equals(userHandle.getBase64Url()))
                .map(this::toRegistered);
    }

    @Override
    public Set<RegisteredCredential> lookupAll(ByteArray credentialId) {
        return repo.findByCredentialId(credentialId.getBase64Url()).map(c -> Set.of(toRegistered(c))).orElseGet(Set::of);
    }

    private RegisteredCredential toRegistered(WebAuthnCredential c) {
        return RegisteredCredential.builder()
                .credentialId(fromB64(c.getCredentialId()))
                .userHandle(fromB64(c.getUserHandle()))
                .publicKeyCose(fromB64(c.getPublicKeyCose()))
                .signatureCount(c.getSignatureCount())
                .build();
    }

    private static ByteArray fromB64(String s) {
        try {
            return ByteArray.fromBase64Url(s);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
