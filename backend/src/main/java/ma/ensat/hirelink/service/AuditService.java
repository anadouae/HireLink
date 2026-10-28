package ma.ensat.hirelink.service;

import ma.ensat.hirelink.model.AuditLog;
import ma.ensat.hirelink.model.User;
import ma.ensat.hirelink.repository.AuditLogRepository;
import ma.ensat.hirelink.security.ClientInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Journal d'audit (OWASP A09). Ne reçoit jamais de secret : l'appelant ne passe que des identifiants et des libellés.
 * Écrit dans sa propre transaction pour que l'évènement survive à un rollback de l'action (ex. échec de connexion).
 */
@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger("hirelink.audit");
    private final AuditLogRepository repository;

    private final org.springframework.transaction.support.TransactionTemplate tx;

    public AuditService(AuditLogRepository repository, org.springframework.transaction.PlatformTransactionManager tm) {
        this.repository = repository;
        // transaction indépendante : une trace d'échec (connexion refusée, accès interdit) survit au rollback de l'appelant
        this.tx = new org.springframework.transaction.support.TransactionTemplate(tm);
        this.tx.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public void log(String action, User actor, String targetType, Object targetId, boolean success, String detail) {
        record(action, actor == null ? null : actor.getId(), actor == null ? null : actor.getEmail(), targetType, targetId, success, detail);
    }

    public void ok(String action, User actor, String targetType, Object targetId, String detail) {
        log(action, actor, targetType, targetId, true, detail);
    }

    public void fail(String action, String actorEmail, String detail) {
        record(action, null, actorEmail, null, null, false, detail);
    }

    public void record(String action, Long actorId, String actorEmail, String targetType, Object targetId, boolean success, String detail) {
        try {
            AuditLog a = new AuditLog();
            a.setAction(action);
            a.setActorUserId(actorId);
            a.setActorEmail(clip(actorEmail, 160));
            a.setTargetType(targetType);
            a.setTargetId(targetId == null ? null : clip(String.valueOf(targetId), 64));
            a.setIp(clip(ClientInfo.current().ip(), 64));
            a.setSuccess(success);
            a.setDetail(clip(detail, 500));
            tx.executeWithoutResult(st -> repository.save(a));
            log.info("{} actor={} target={}:{} success={}", action, actorId, targetType, targetId, success);
        } catch (Exception e) {
            log.warn("Audit non enregistré ({}) : {}", action, e.getClass().getSimpleName());
        }
    }

    private static String clip(String s, int max) {
        if (s == null) return null;
        String t = s.replaceAll("[\\r\\n\\t]", " ");
        return t.length() > max ? t.substring(0, max) : t;
    }
}
