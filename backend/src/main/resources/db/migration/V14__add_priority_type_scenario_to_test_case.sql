-- backend/src/main/resources/db/migration/V14__add_priority_type_scenario_to_test_case.sql
-- Database: frontline
-- Catatan: Idempotent. V9 sudah membuat kolom ini di DB baru, sedangkan DB lama
-- (dibuat Hibernate ddl-auto=update lalu baseline) mungkin belum punya. IF NOT
-- EXISTS membuat migrasi aman di kedua kasus. Baris lama diisi default
-- (MEDIUM / MANUAL) agar NOT NULL tidak gagal pada tabel berisi data.
--
-- priority & type dipakai form Create/Edit Test Case (wajib diisi) dan
-- kolom Priority/Type di tab Run Details + Export Excel (modul report).
-- scenario_type opsional (POSITIVE/NEGATIVE).

ALTER TABLE test_case ADD COLUMN IF NOT EXISTS priority VARCHAR(20);
ALTER TABLE test_case ADD COLUMN IF NOT EXISTS type VARCHAR(20);
ALTER TABLE test_case ADD COLUMN IF NOT EXISTS scenario_type VARCHAR(20);

UPDATE test_case SET priority = 'MEDIUM' WHERE priority IS NULL;
UPDATE test_case SET type = 'MANUAL' WHERE type IS NULL;

ALTER TABLE test_case ALTER COLUMN priority SET NOT NULL;
ALTER TABLE test_case ALTER COLUMN type SET NOT NULL;
