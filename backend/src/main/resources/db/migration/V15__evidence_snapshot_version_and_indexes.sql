-- backend/src/main/resources/db/migration/V15__evidence_snapshot_version_and_indexes.sql
-- Database: frontline
-- Catatan: pada profil "local" Flyway dinonaktifkan dan kolom/index di bawah dibuat
-- otomatis oleh Hibernate (ddl-auto=update) dari entity TestResult/TestRun/TestCase/
-- TestFolder/ProjectCollaboration. Pada profil "prod" file ini dijalankan Flyway.
--
-- 1) Optimistic locking test_result (@Version). DEFAULT 0 wajib supaya baris lama
--    tidak berversi NULL (NULL akan membuat setiap UPDATE gagal).
ALTER TABLE test_result ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;

-- 2) Snapshot step & expected result saat test case di-mapping ke run, supaya hasil
--    per-step (step_results, berbasis urutan) tidak bergeser kalau test case diedit
--    setelah dieksekusi. NULL = baris lama -> API fallback ke test case live.
ALTER TABLE test_result ADD COLUMN IF NOT EXISTS test_step_snapshot TEXT;
ALTER TABLE test_result ADD COLUMN IF NOT EXISTS expected_result_snapshot TEXT;

-- 3) Index (tidak ada @Index sebelumnya, index di file V* lama tidak pernah dibuat
--    karena skema dibuat Hibernate).
CREATE INDEX IF NOT EXISTS idx_test_run_project_id ON test_run(project_id);
CREATE INDEX IF NOT EXISTS idx_test_result_test_case_id ON test_result(test_case_id);
CREATE INDEX IF NOT EXISTS idx_test_result_test_run_id ON test_result(test_run_id);
CREATE INDEX IF NOT EXISTS idx_test_case_project_status ON test_case(project_id, status);
CREATE INDEX IF NOT EXISTS idx_test_case_folder_id ON test_case(folder_id);
CREATE INDEX IF NOT EXISTS idx_test_folder_project_id ON test_folder(project_id);
CREATE INDEX IF NOT EXISTS idx_project_collab_user_id ON project_collaboration(user_id);
CREATE INDEX IF NOT EXISTS idx_project_collab_project_id ON project_collaboration(project_id);
