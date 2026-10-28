-- backend/src/main/resources/db/migration/V5__add_reset_password_columns_to_users.sql
-- Database: frontline
-- Catatan: Flyway dinonaktifkan (spring.flyway.enabled=false), file ini HANYA
-- referensi dokumentasi skema. Kolom di bawah otomatis dibuat oleh Hibernate
-- (spring.jpa.hibernate.ddl-auto=update) saat aplikasi start.

ALTER TABLE users
    ADD COLUMN reset_password_token VARCHAR(255),
    ADD COLUMN reset_password_token_expires_at TIMESTAMP;
