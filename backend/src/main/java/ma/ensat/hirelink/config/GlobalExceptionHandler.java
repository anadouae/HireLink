package ma.ensat.hirelink.config;

import jakarta.validation.ConstraintViolationException;
import ma.ensat.hirelink.security.TooManyRequestsException;
import ma.ensat.hirelink.service.AuditService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Gestion globale des erreurs (OWASP A05/A09) : jamais de trace d'exécution ni de détail interne dans la réponse.
 * Les erreurs 5xx reçoivent un identifiant de corrélation à communiquer au support ; le détail reste dans les logs serveur.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private final AuditService audit;

    public GlobalExceptionHandler(AuditService audit) {
        this.audit = audit;
    }

    private static ResponseEntity<Map<String, Object>> body(HttpStatus status, String message, String ref) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("status", status.value());
        m.put("error", status.getReasonPhrase());
        m.put("message", message);
        if (ref != null) m.put("reference", ref);
        m.put("timestamp", Instant.now().toString());
        return ResponseEntity.status(status).body(m);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> status(ResponseStatusException e) {
        HttpStatus st = HttpStatus.resolve(e.getStatusCode().value());
        if (st == null) st = HttpStatus.INTERNAL_SERVER_ERROR;
        if (st.is5xxServerError()) return unexpected(e);
        if (st == HttpStatus.FORBIDDEN) {
            audit.record("ACCESS_DENIED", null, null, null, null, false, e.getReason());
        }
        return body(st, e.getReason() == null ? st.getReasonPhrase() : e.getReason(), null);
    }

    @ExceptionHandler(TooManyRequestsException.class)
    public ResponseEntity<Map<String, Object>> tooMany(TooManyRequestsException e) {
        ResponseEntity<Map<String, Object>> r = body(HttpStatus.TOO_MANY_REQUESTS, e.getMessage(), null);
        return ResponseEntity.status(r.getStatusCode()).header(HttpHeaders.RETRY_AFTER, String.valueOf(e.getRetryAfterSeconds())).body(r.getBody());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> invalid(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream().findFirst()
                .map(f -> "Champ « " + f.getField() + " » invalide" + (f.getDefaultMessage() == null ? "" : " : " + f.getDefaultMessage()))
                .orElse("Requête invalide");
        return body(HttpStatus.BAD_REQUEST, msg, null);
    }

    @ExceptionHandler({ConstraintViolationException.class, HttpMessageNotReadableException.class, MissingServletRequestParameterException.class,
            MissingServletRequestPartException.class, MethodArgumentTypeMismatchException.class, IllegalArgumentException.class})
    public ResponseEntity<Map<String, Object>> badRequest(Exception e) {
        return body(HttpStatus.BAD_REQUEST, "Requête invalide.", null);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, Object>> tooLarge(MaxUploadSizeExceededException e) {
        return body(HttpStatus.PAYLOAD_TOO_LARGE, "Fichier trop volumineux.", null);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Map<String, Object>> method(HttpRequestMethodNotSupportedException e) {
        return body(HttpStatus.METHOD_NOT_ALLOWED, "Méthode non autorisée.", null);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<Map<String, Object>> media(HttpMediaTypeNotSupportedException e) {
        return body(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Type de contenu non pris en charge.", null);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Map<String, Object>> notFound(NoResourceFoundException e) {
        return body(HttpStatus.NOT_FOUND, "Ressource introuvable.", null);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> denied(AccessDeniedException e) {
        audit.record("ACCESS_DENIED", null, null, null, null, false, "Rôle insuffisant");
        return body(HttpStatus.FORBIDDEN, "Accès refusé.", null);
    }

    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> integrity(org.springframework.dao.DataIntegrityViolationException e) {
        log.warn("Contrainte de données refusée : {}", e.getMostSpecificCause().getMessage());
        return body(HttpStatus.BAD_REQUEST, "Une valeur est trop longue ou déjà utilisée.", null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> unexpected(Exception e) {
        String ref = UUID.randomUUID().toString().substring(0, 8);
        log.error("Erreur interne [{}] : {}", ref, e.toString(), e);
        return body(HttpStatus.INTERNAL_SERVER_ERROR, "Une erreur interne est survenue. Communiquez la référence au support.", ref);
    }
}
