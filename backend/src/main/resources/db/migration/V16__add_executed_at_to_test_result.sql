-- backend/src/main/resources/db/migration/V16__add_executed_at_to_test_result.sql
-- Database: frontline
-- Catatan: pada profil "local" Flyway dinonaktifkan; kolom dibuat Hibernate
-- (ddl-auto=update) dari TestResult.executedAt. Query dashboard memakai
-- COALESCE(executed_at, updated_at, created_at), jadi data lama tetap terbaca
-- walau backfill di bawah belum dijalankan manual di DB lokal.
--
-- Waktu eksekusi TERAKHIR sebuah hasil (status berubah ke selain NEW). Dipakai
-- Execution Timeline di dashboard project. (Current Project Health tidak
-- memakainya: dihitung sama dengan Report dari semua hasil per status.)
ALTER TABLE test_result ADD COLUMN IF NOT EXISTS executed_at TIMESTAMP;

-- Backfill data lama: perkiraan dari updated_at (baris yang bukan NEW pasti
-- pernah dieksekusi). PERKIRAAN -- kalau komentar/evidence diedit setelah
-- dieksekusi, tanggalnya bergeser; hanya mempengaruhi titik historis lama.
UPDATE test_result
   SET executed_at = COALESCE(updated_at, created_at)
 WHERE status <> 'NEW'
   AND executed_at IS NULL;
