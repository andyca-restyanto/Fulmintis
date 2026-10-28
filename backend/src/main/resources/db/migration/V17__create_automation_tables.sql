-- backend/src/main/resources/db/migration/V17__create_automation_tables.sql
-- Database: frontline
-- Catatan: pada profil "local" Flyway dinonaktifkan dan tabel dibuat Hibernate (ddl-auto=update) dari entity
-- AutomationSetup / AutomationGeneration. Indeks unik PARSIAL di bawah tidak bisa dideklarasikan lewat JPA,
-- jadi hanya ada lewat file ini (profil prod); aplikasi tetap menegakkan aturan yang sama di kode.

-- Struktur automation per project: MAKSIMAL SATU (unique di database).
CREATE TABLE IF NOT EXISTS automation_setup (
    id               UUID PRIMARY KEY,
    project_id       UUID         NOT NULL REFERENCES project (id),
    framework        VARCHAR(20)  NOT NULL,
    language         VARCHAR(20)  NOT NULL,
    pattern          VARCHAR(30)  NOT NULL,
    structure_notes  TEXT,
    created_at       TIMESTAMP    NOT NULL,
    created_by       VARCHAR(255) NOT NULL,
    updated_at       TIMESTAMP,
    updated_by       VARCHAR(255),
    CONSTRAINT uq_automation_setup_project UNIQUE (project_id)
);

-- Satu baris per job generate (termasuk snapshot setup, hasil, dan pemakaian token).
CREATE TABLE IF NOT EXISTS automation_generation (
    id               UUID PRIMARY KEY,
    project_id       UUID         NOT NULL REFERENCES project (id),
    requested_by     VARCHAR(255) NOT NULL,
    tier             VARCHAR(10)  NOT NULL,
    status           VARCHAR(20)  NOT NULL,
    framework        VARCHAR(20)  NOT NULL,
    language         VARCHAR(20)  NOT NULL,
    pattern          VARCHAR(30)  NOT NULL,
    structure_notes  TEXT,
    test_case_ids    TEXT         NOT NULL,
    test_case_count  INTEGER      NOT NULL,
    result_files     TEXT,
    notes            TEXT,
    error_code       VARCHAR(40),
    error_message    VARCHAR(500),
    prompt_version   VARCHAR(40),
    model            VARCHAR(100),
    input_tokens     INTEGER,
    output_tokens    INTEGER,
    created_at       TIMESTAMP    NOT NULL,
    started_at       TIMESTAMP,
    finished_at      TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_automation_generation_project_created ON automation_generation (project_id, created_at);
CREATE INDEX IF NOT EXISTS idx_automation_generation_user_created ON automation_generation (requested_by, created_at);

-- Satu job AKTIF per user, dijamin database (menutup celah dua permintaan bersamaan yang lolos pengecekan di kode).
CREATE UNIQUE INDEX IF NOT EXISTS uq_automation_generation_one_active_per_user
    ON automation_generation (requested_by)
    WHERE status IN ('QUEUED', 'RUNNING');
