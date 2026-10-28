-- backend/src/main/resources/db/migration/V13__add_step_results_to_test_result.sql
-- Database: frontline
-- Catatan: Flyway dinonaktifkan (spring.flyway.enabled=false), file ini HANYA
-- referensi dokumentasi skema. Kolom di bawah otomatis ditambahkan oleh
-- Hibernate (spring.jpa.hibernate.ddl-auto=update) saat aplikasi start,
-- lihat entity TestResult.java.
--
-- Hasil per-step di halaman Execute (tombol centang/silang tiap step).
-- Daftar nama enum TestStepResultStatus dipisah koma, urutan = urutan step
-- di test case, mis. 'PASSED,FAILED,NEW'. NULL = belum ada step yang ditandai.

ALTER TABLE test_result ADD COLUMN step_results TEXT;
