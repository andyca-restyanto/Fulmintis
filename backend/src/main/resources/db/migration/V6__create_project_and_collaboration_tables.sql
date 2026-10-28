-- backend/src/main/resources/db/migration/V6__create_project_and_collaboration_tables.sql
-- Database: frontline
-- Catatan: Flyway dinonaktifkan (spring.flyway.enabled=false), file ini HANYA
-- referensi dokumentasi skema. Tabel di bawah otomatis dibuat oleh Hibernate
-- (spring.jpa.hibernate.ddl-auto=update) saat aplikasi start.
--
-- project & project_collaboration SAMA-SAMA di database "frontline" (beda
-- dengan user_type yang di database terpisah), jadi FK asli antar keduanya
-- valid dan ditegakkan oleh database.

CREATE TABLE project (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_name VARCHAR(255) NOT NULL,
    description TEXT,
    created_at  TIMESTAMP NOT NULL DEFAULT now(),
    updated_at  TIMESTAMP
);

CREATE TABLE project_collaboration (
    id           BIGSERIAL PRIMARY KEY,
    project_id   UUID NOT NULL REFERENCES project(id),
    user_id      UUID NOT NULL REFERENCES users(id),
    project_team VARCHAR(50) NOT NULL,  -- code dari project_team.description (database master_data, TIDAK ada FK asli krn beda database)
    created_at   TIMESTAMP NOT NULL DEFAULT now()
);
