-- backend/src/main/resources/db/migration/V10__add_name_column_to_users.sql
-- Database: frontline
-- Catatan: Flyway dinonaktifkan (spring.flyway.enabled=false), file ini HANYA
-- referensi dokumentasi skema. Kolom di bawah otomatis ditambahkan oleh
-- Hibernate (spring.jpa.hibernate.ddl-auto=update) saat aplikasi start,
-- lihat entity User.java.
--
-- requirement #4: nama tampilan custom, OPSIONAL (nullable) -- user yang
-- sudah terdaftar sebelum kolom ini ada belum tentu sudah set nama, dan
-- pencarian "tambah team member" tetap bisa pakai email kalau nama kosong.

ALTER TABLE users ADD COLUMN name VARCHAR(255);
