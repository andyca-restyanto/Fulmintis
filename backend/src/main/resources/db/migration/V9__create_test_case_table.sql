-- backend/src/main/resources/db/migration/V9__create_test_case_table.sql
-- Database: frontline
-- Catatan: Flyway dinonaktifkan (spring.flyway.enabled=false), file ini HANYA
-- referensi dokumentasi skema. Tabel di bawah otomatis dibuat oleh Hibernate
-- (spring.jpa.hibernate.ddl-auto=update) saat aplikasi start, lihat entity
-- TestCase.java.
--
-- test_case ada di database "frontline" (SAMA dengan project & test_folder),
-- jadi FK asli ke project(id) dan test_folder(id) valid.

CREATE TABLE test_case (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id      UUID NOT NULL REFERENCES project(id),
    folder_id       UUID NOT NULL REFERENCES test_folder(id),
    title           VARCHAR(255) NOT NULL,
    priority        VARCHAR(20) NOT NULL,  -- HIGHEST | HIGH | MEDIUM | LOW
    type            VARCHAR(20) NOT NULL,  -- MANUAL | AUTOMATION
    scenario_type   VARCHAR(20),           -- POSITIVE | NEGATIVE
    description     TEXT,
    objective       TEXT,
    precondition    TEXT,
    test_step       TEXT,
    expected_result TEXT,
    created_at      TIMESTAMP NOT NULL DEFAULT now(),
    created_by      VARCHAR(255) NOT NULL,
    updated_at      TIMESTAMP,
    updated_by      VARCHAR(255)
);

CREATE INDEX idx_test_case_project_id ON test_case(project_id);
CREATE INDEX idx_test_case_folder_id ON test_case(folder_id);
