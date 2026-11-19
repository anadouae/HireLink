package ma.ensat.hirelink.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Réparation idempotente au démarrage : d'anciennes bases ont créé user_avatars.data en TINYBLOB
 * (255 octets), ce qui fait échouer tout envoi de photo. ddl-auto=update ne change jamais le type
 * d'une colonne existante ; cette passe l'élargit en MEDIUMBLOB une seule fois. Même correctif en SQL :
 * database/migrations/V010__user_avatar_mediumblob.sql. Aucune ligne n'est modifiée ni supprimée.
 */
@Component
public class AvatarColumnRepair {

    private static final Logger log = LoggerFactory.getLogger(AvatarColumnRepair.class);
    private static final List<String> TOO_SMALL = List.of("tinyblob", "blob");

    private final JdbcTemplate jdbc;

    @Value("${hirelink.startup-migrations.enabled:false}")
    private boolean startupMigrationsEnabled;

    public AvatarColumnRepair(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void widenAvatarColumn() {
        if (!startupMigrationsEnabled) return;
        try {
            List<String> types = jdbc.queryForList(
                    "SELECT LOWER(DATA_TYPE) FROM information_schema.COLUMNS "
                            + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user_avatars' AND COLUMN_NAME = 'data'",
                    String.class);
            if (types.isEmpty() || !TOO_SMALL.contains(types.get(0))) return;
            jdbc.execute("ALTER TABLE user_avatars MODIFY COLUMN data MEDIUMBLOB NOT NULL");
            log.info("Colonne user_avatars.data élargie ({} → mediumblob).", types.get(0));
        } catch (Exception e) {
            log.warn("Vérification de la colonne des photos de profil ignorée ({})", e.getMessage());
        }
    }
}
