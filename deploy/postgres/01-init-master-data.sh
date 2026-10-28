#!/bin/sh
# filepath: /deploy/postgres/01-init-master-data.sh
# Dijalankan otomatis oleh image postgres HANYA saat volume data masih kosong.
# Membuat database "master_data" dan tabelnya. Profil prod memakai ddl-auto=validate,
# jadi tabel HARUS sudah ada sebelum backend start. Isi datanya diurus seeder di aplikasi.
set -eu

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname postgres <<SQL
SELECT 'CREATE DATABASE master_data OWNER "$POSTGRES_USER"'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'master_data')\gexec
SQL

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname master_data <<'SQL'
CREATE TABLE IF NOT EXISTS user_type (
    id          BIGSERIAL PRIMARY KEY,
    code        VARCHAR(255) NOT NULL UNIQUE,
    label       VARCHAR(255) NOT NULL,
    sort_order  INTEGER DEFAULT 0
);
CREATE TABLE IF NOT EXISTS project_team (
    id          BIGSERIAL PRIMARY KEY,
    description VARCHAR(255) NOT NULL UNIQUE
);
SQL
