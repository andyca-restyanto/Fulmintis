-- backend/src/main/resources/db/master-data-migration/V2__create_project_team_table.sql
-- Database: master_data (TERPISAH dari "frontline")
-- Referensi dokumentasi -- tabel & data otomatis dibuat oleh Hibernate +
-- ProjectTeamSeeder.java saat aplikasi start.

CREATE TABLE project_team (
    id          BIGSERIAL PRIMARY KEY,
    description VARCHAR(255) NOT NULL UNIQUE
);

INSERT INTO project_team (description) VALUES
    ('OWNER'),
    ('COLLABORATOR');
