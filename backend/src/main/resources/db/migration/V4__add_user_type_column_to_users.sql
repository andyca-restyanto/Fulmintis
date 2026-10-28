-- backend/src/main/resources/db/migration/V4__add_user_type_column_to_users.sql
-- Database: frontline
-- Catatan: Flyway dinonaktifkan (spring.flyway.enabled=false), file ini HANYA
-- referensi dokumentasi skema. Kolom di bawah otomatis dibuat oleh Hibernate
-- (spring.jpa.hibernate.ddl-auto=update) saat aplikasi start.
--
-- PENTING: kolom ini menyimpan STRING code (misal 'FREE', 'VIP_MONTHLY'),
-- BUKAN foreign key ke tabel master_data -- karena master_data ada di
-- database TERPISAH ("master_data"), dan PostgreSQL tidak mendukung foreign
-- key lintas database.

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS user_type VARCHAR(50) NOT NULL DEFAULT 'Free';
ALTER TABLE users
    ALTER COLUMN user_type DROP DEFAULT;

-- Catatan operasional: kalau tabel users SUDAH ada isinya sebelum kolom ini
-- ditambahkan, Hibernate ddl-auto=update TIDAK otomatis mem-backfill nilai
-- untuk baris lama. Jalankan salah satu setelah aplikasi start (lihat README
-- untuk detail):
--   TRUNCATE TABLE users CASCADE;              -- opsi cepat untuk dev/testing
--   UPDATE users SET user_type = 'FREE' WHERE user_type IS NULL;  -- backfill manual
