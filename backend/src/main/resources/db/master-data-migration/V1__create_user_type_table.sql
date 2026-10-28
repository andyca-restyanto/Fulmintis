-- backend/src/main/resources/db/master-data-migration/V1__create_user_type_table.sql
-- Database: master_data (TERPISAH dari "frontline")
--
-- PENTING: file ini TIDAK otomatis dijalankan oleh aplikasi (Flyway off,
-- dan lokasinya sengaja di luar folder db/migration standar). Jalankan
-- MANUAL sebelum start aplikasi untuk pertama kali:
--
--   psql -U postgres
--   CREATE DATABASE master_data;
--   \c master_data
--   GRANT ALL PRIVILEGES ON DATABASE master_data TO "andyca.pratama";
--   GRANT USAGE, CREATE ON SCHEMA public TO "andyca.pratama";
--   ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL PRIVILEGES ON TABLES TO "andyca.pratama";
--   ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL PRIVILEGES ON SEQUENCES TO "andyca.pratama";
--
-- Setelah database "master_data" ada, TABEL di bawah dan datanya otomatis
-- dibuat oleh Hibernate (ddl-auto=update) + UserTypeSeeder.java saat
-- aplikasi start -- jadi CREATE TABLE & INSERT di bawah ini murni referensi
-- dokumentasi, bukan langkah yang perlu dijalankan manual.

CREATE TABLE user_type (
    id          BIGSERIAL PRIMARY KEY,
    code        VARCHAR(255) NOT NULL UNIQUE,
    label       VARCHAR(255) NOT NULL,
    sort_order  INTEGER DEFAULT 0
);

INSERT INTO user_type (code, label, sort_order) VALUES
    ('FREE', 'Free', 1),
    ('VIP_MONTHLY', 'VIP Monthly', 2),
    ('VIP_YEARLY', 'VIP Yearly', 3);
