-- backend/src/main/resources/db/migration/V3__create_activity_log_table.sql
-- Catatan: Flyway dinonaktifkan (spring.flyway.enabled=false), file ini HANYA
-- referensi dokumentasi skema. Tabel di bawah otomatis dibuat oleh Hibernate
-- (spring.jpa.hibernate.ddl-auto=update) saat aplikasi start.

CREATE TABLE activity_log (
    id              BIGSERIAL PRIMARY KEY,
    user_email      VARCHAR(255) NOT NULL,
    activity        VARCHAR(255) NOT NULL,
    created_date    TIMESTAMP NOT NULL DEFAULT now(),
    created_by      VARCHAR(255) NOT NULL,
    updated_date    TIMESTAMP,
    updated_by      VARCHAR(255)
);
