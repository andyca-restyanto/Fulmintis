-- backend/src/main/resources/db/migration/V8__create_test_folder_table.sql
-- Database: frontline
-- Catatan: Flyway dinonaktifkan (spring.flyway.enabled=false), file ini HANYA
-- referensi dokumentasi skema. Tabel di bawah otomatis dibuat oleh Hibernate
-- (spring.jpa.hibernate.ddl-auto=update) saat aplikasi start, lihat entity
-- TestFolder.java.
--
-- test_folder ada di database "frontline" (SAMA dengan project), jadi FK
-- asli ke project(id) valid. parent_id self-reference ke test_folder(id)
-- sendiri -- NULL berarti folder ini root (requirement #2).

CREATE TABLE test_folder (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    folder_name VARCHAR(255) NOT NULL,
    project_id  UUID NOT NULL REFERENCES project(id),
    parent_id   UUID REFERENCES test_folder(id),
    created_at  TIMESTAMP NOT NULL DEFAULT now(),
    created_by  VARCHAR(255) NOT NULL,
    updated_at  TIMESTAMP,
    updated_by  VARCHAR(255)
);
