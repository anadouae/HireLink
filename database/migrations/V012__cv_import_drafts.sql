-- HireLink V012 — brouillons d’import CV, éléments détectés et versions restaurables
-- Cette migration ne crée aucune base et ne touche qu'à la base sélectionnée par le client MySQL.

CREATE TABLE IF NOT EXISTS cv_import_drafts (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    cv_file_name VARCHAR(255) NULL,
    cv_storage_name VARCHAR(255) NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    step VARCHAR(16) NOT NULL DEFAULT 'IMPORT',
    payload_json LONGTEXT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    expires_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    KEY idx_cv_drafts_user_status (user_id, status),
    KEY idx_cv_drafts_expires (expires_at),
    CONSTRAINT fk_cv_drafts_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS cv_import_items (
    id BIGINT NOT NULL AUTO_INCREMENT,
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
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_cv_items_draft_state (draft_id, state),
    CONSTRAINT fk_cv_items_draft FOREIGN KEY (draft_id) REFERENCES cv_import_drafts(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS profile_versions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    reason VARCHAR(64) NOT NULL DEFAULT 'CV_IMPORT',
    snapshot_json LONGTEXT NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    expires_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_profile_versions_user_created (user_id, created_at),
    KEY idx_profile_versions_expires (expires_at),
    CONSTRAINT fk_profile_versions_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
