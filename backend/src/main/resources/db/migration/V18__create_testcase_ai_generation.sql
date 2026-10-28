-- backend/src/main/resources/db/migration/V18__create_testcase_ai_generation.sql
-- Database: frontline
-- Catatan: pada profil "local" Flyway dinonaktifkan dan tabel dibuat Hibernate (ddl-auto=update) dari entity TestCaseAiGeneration.
-- Indeks unik PARSIAL di bawah tidak bisa dideklarasikan lewat JPA, jadi hanya ada lewat file ini (profil prod); aplikasi tetap
-- menegakkan aturan yang sama di kode.

-- Satu baris per job "generate test case dgn AI". Menyimpan DRAFT hasil AI (drafts_json) -- BUKAN test case. Teks requirement
-- SENGAJA tidak disimpan (hanya panjangnya). Baris tidak dihapus karena menjadi dasar hitungan kuota harian; hanya drafts_json
-- yang dikosongkan setelah di-commit atau lewat masa simpan.
CREATE TABLE IF NOT EXISTS testcase_ai_generation (
    id                  UUID PRIMARY KEY,
    project_id          UUID         NOT NULL REFERENCES project (id),
    folder_id           UUID         NOT NULL,
    requested_by        VARCHAR(255) NOT NULL,
    tier                VARCHAR(10)  NOT NULL,
    status              VARCHAR(20)  NOT NULL,
    requested_count     INTEGER      NOT NULL,
    include_negative    BOOLEAN      NOT NULL,
    requirement_length  INTEGER      NOT NULL,
    draft_count         INTEGER      NOT NULL,
    drafts_json         TEXT,
    truncated           BOOLEAN      NOT NULL,
    committed           BOOLEAN      NOT NULL,
    committed_count     INTEGER,
    committed_at        TIMESTAMP,
    error_code          VARCHAR(40),
    error_message       VARCHAR(500),
    prompt_version      VARCHAR(40),
    model               VARCHAR(100),
    input_tokens        INTEGER,
    output_tokens       INTEGER,
    created_at          TIMESTAMP    NOT NULL,
    started_at          TIMESTAMP,
    finished_at         TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_testcase_ai_generation_user_created ON testcase_ai_generation (requested_by, created_at);
CREATE INDEX IF NOT EXISTS idx_testcase_ai_generation_project_user ON testcase_ai_generation (project_id, requested_by);

-- Satu job AKTIF per user, dijamin database (menutup celah dua permintaan bersamaan yang lolos pengecekan di kode).
CREATE UNIQUE INDEX IF NOT EXISTS uq_testcase_ai_generation_one_active_per_user
    ON testcase_ai_generation (requested_by)
    WHERE status IN ('QUEUED', 'RUNNING');
