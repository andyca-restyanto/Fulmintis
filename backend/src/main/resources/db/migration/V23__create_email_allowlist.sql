-- backend/src/main/resources/db/migration/V23__create_email_allowlist.sql
-- Database: frontline
-- Pembatasan email yang boleh register (menu admin "Email Diizinkan").
-- Aman dijalankan ulang (IF NOT EXISTS / ON CONFLICT). Default: fitur MATI, jadi migrasi ini tidak mengubah perilaku.
CREATE TABLE IF NOT EXISTS allowed_email_rule (
    id         BIGSERIAL    PRIMARY KEY,
    rule_type  VARCHAR(10)  NOT NULL,          -- EMAIL | DOMAIN
    value      VARCHAR(255) NOT NULL,          -- huruf kecil; EMAIL = alamat lengkap, DOMAIN = "@perusahaan.com"
    created_at TIMESTAMP    NOT NULL,
    created_by VARCHAR(255) NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_allowed_email_rule_type_value
    ON allowed_email_rule (rule_type, value);

CREATE TABLE IF NOT EXISTS registration_allowlist_setting (
    id         INTEGER      PRIMARY KEY,       -- selalu 1 (satu baris)
    enabled    BOOLEAN      NOT NULL,
    updated_by VARCHAR(255) NOT NULL,
    updated_at TIMESTAMP    NOT NULL
);

INSERT INTO registration_allowlist_setting (id, enabled, updated_by, updated_at)
VALUES (1, FALSE, 'system', now())
ON CONFLICT (id) DO NOTHING;
