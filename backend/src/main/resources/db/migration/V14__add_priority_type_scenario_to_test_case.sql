-- backend/src/main/resources/db/migration/V14__add_priority_type_scenario_to_test_case.sql
-- Database: frontline
-- Catatan: Flyway dinonaktifkan (spring.flyway.enabled=false), file ini HANYA
-- referensi dokumentasi skema. Kolom di bawah otomatis ditambahkan oleh
-- Hibernate (spring.jpa.hibernate.ddl-auto=update) saat aplikasi start,
-- lihat entity TestCase.java.
--
-- priority & type dipakai form Create/Edit Test Case (wajib diisi) dan
-- kolom Priority/Type di tab Run Details + Export Excel (modul report).
-- scenario_type opsional (POSITIVE/NEGATIVE).
--
-- PERINGATAN OPERASIONAL: priority & type NOT NULL. Kalau tabel test_case
-- SUDAH ADA ISINYA sebelum kolom ini ditambahkan, Hibernate ddl-auto=update
-- bisa gagal ALTER TABLE (constraint NOT NULL tanpa DEFAULT pada tabel yang
-- tidak kosong) tergantung versi Hibernate & PostgreSQL. Kalau startup
-- gagal karena ini, isi manual dulu nilai default utk baris lama sebelum
-- restart, mis.:
--   ALTER TABLE test_case ADD COLUMN priority VARCHAR(20);
--   ALTER TABLE test_case ADD COLUMN type VARCHAR(20);
--   UPDATE test_case SET priority = 'MEDIUM', type = 'MANUAL' WHERE priority IS NULL;
--   ALTER TABLE test_case ALTER COLUMN priority SET NOT NULL;
--   ALTER TABLE test_case ALTER COLUMN type SET NOT NULL;
--   ALTER TABLE test_case ADD COLUMN scenario_type VARCHAR(20);

ALTER TABLE test_case ADD COLUMN priority VARCHAR(20) NOT NULL;
ALTER TABLE test_case ADD COLUMN type VARCHAR(20) NOT NULL;
ALTER TABLE test_case ADD COLUMN scenario_type VARCHAR(20);
