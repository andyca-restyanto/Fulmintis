-- backend/src/main/resources/db/migration/V11__add_archive_columns_to_test_case.sql
-- Database: frontline
-- Catatan: Flyway dinonaktifkan (spring.flyway.enabled=false), file ini HANYA
-- referensi dokumentasi skema. Kolom di bawah otomatis ditambahkan oleh
-- Hibernate (spring.jpa.hibernate.ddl-auto=update) saat aplikasi start,
-- lihat entity TestCase.java.
--
-- Requirement tambahan: edit/delete test case, delete = soft delete/archive
-- (bukan hard delete), dan test case archived hanya boleh dilihat OWNER
-- project.

ALTER TABLE test_case
    ADD COLUMN status      VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',  -- ACTIVE | ARCHIVED
    ADD COLUMN archived_at TIMESTAMP,
    ADD COLUMN archived_by VARCHAR(255);

-- Dipakai TestCaseSpecifications.hasStatus() -- listing biasa (GET
-- /test-cases) SELALU filter status=ACTIVE, listing archived (GET
-- /test-cases/archived, khusus OWNER) SELALU filter status=ARCHIVED.
CREATE INDEX idx_test_case_status ON test_case(status);
