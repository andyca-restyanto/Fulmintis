-- backend/src/main/resources/db/migration/V12__create_test_run_and_test_result_tables.sql
-- Database: frontline
-- Catatan: Flyway dinonaktifkan (spring.flyway.enabled=false), file ini HANYA
-- referensi dokumentasi skema. Kedua tabel di bawah otomatis dibuat oleh
-- Hibernate (spring.jpa.hibernate.ddl-auto=update) saat aplikasi start,
-- lihat entity TestRun.java & TestResult.java.
--
-- Requirement tambahan: create test run (#1), mapping test case ke test run
-- (#2), search test case saat mapping (#3).

-- Requirement #4
CREATE TABLE test_run (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id  UUID        NOT NULL REFERENCES project(id),
    title       VARCHAR(255) NOT NULL,
    description TEXT,
    status      VARCHAR(20) NOT NULL DEFAULT 'PENDING', -- PENDING | RUNNING | FINISHED
    created_at  TIMESTAMP   NOT NULL,
    created_by  VARCHAR(255) NOT NULL,
    updated_at  TIMESTAMP,
    updated_by  VARCHAR(255)
);

CREATE INDEX idx_test_run_project_id ON test_run(project_id);

-- Requirement #5 -- mapping test case <-> test run, sekaligus nyimpen hasil
-- eksekusinya (status/evidence/comment).
CREATE TABLE test_result (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    test_run_id  UUID        NOT NULL REFERENCES test_run(id),
    test_case_id UUID        NOT NULL REFERENCES test_case(id),
    status       VARCHAR(20) NOT NULL DEFAULT 'NEW', -- NEW | PASSED | FAILED | PENDING | BLOCKED
    evidence     TEXT, -- nama file yang tersimpan di disk (uploads/test-evidence/), bukan path absolut
    comment      TEXT,
    created_at   TIMESTAMP   NOT NULL,
    created_by   VARCHAR(255) NOT NULL,
    updated_at   TIMESTAMP,
    updated_by   VARCHAR(255),

    CONSTRAINT uk_test_result_run_case UNIQUE (test_run_id, test_case_id)
);

CREATE INDEX idx_test_result_test_run_id ON test_result(test_run_id);
