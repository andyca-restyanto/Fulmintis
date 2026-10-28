-- backend/src/main/resources/db/migration/V2__add_verification_columns_to_users.sql
-- Catatan: Flyway dinonaktifkan (spring.flyway.enabled=false), file ini HANYA
-- referensi dokumentasi skema. Kolom di bawah otomatis dibuat oleh Hibernate
-- (spring.jpa.hibernate.ddl-auto=update) saat aplikasi start.

ALTER TABLE users
    ADD COLUMN is_verified BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN verification_token VARCHAR(255),
    ADD COLUMN verification_token_expires_at TIMESTAMP;
