-- backend/src/main/resources/db/migration/V7__add_audit_columns_to_project_tables.sql
-- Database: frontline
-- Catatan: Flyway dinonaktifkan (spring.flyway.enabled=false), file ini HANYA
-- referensi dokumentasi skema. Kolom di bawah otomatis dibuat oleh Hibernate
-- (spring.jpa.hibernate.ddl-auto=update) saat aplikasi start.

ALTER TABLE project
    ADD COLUMN IF NOT EXISTS created_by VARCHAR(255) NOT NULL DEFAULT 'system',
    ADD COLUMN IF NOT EXISTS updated_by VARCHAR(255);

ALTER TABLE project_collaboration
    ADD COLUMN IF NOT EXISTS created_by VARCHAR(255) NOT NULL DEFAULT 'system',
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS updated_by VARCHAR(255);

-- Catatan operasional: kalau tabel ini SUDAH ada isinya (dari testing
-- sebelumnya), Hibernate ddl-auto=update TIDAK otomatis mem-backfill
-- created_by untuk baris lama (kolom NOT NULL). Jalankan salah satu:
--   TRUNCATE TABLE project_collaboration, project CASCADE;
--   -- atau backfill manual:
--   UPDATE project SET created_by = 'system' WHERE created_by IS NULL;
--   UPDATE project_collaboration SET created_by = 'system' WHERE created_by IS NULL;

ALTER TABLE project ALTER COLUMN created_by DROP DEFAULT;
ALTER TABLE project_collaboration ALTER COLUMN created_by DROP DEFAULT;
