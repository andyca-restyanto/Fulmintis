-- backend/src/main/resources/db/migration/V21__add_users_user_type_created_at_index.sql
-- Daftar user di menu User admin: filter user_type, urut created_at DESC (lalu id DESC).
-- Pencarian email/nama memakai LIKE '%kata%' yang tidak bisa memanfaatkan index btree;
-- bila jumlah user mencapai puluhan ribu, pertimbangkan index pg_trgm (butuh ekstensi di PostgreSQL).
CREATE INDEX IF NOT EXISTS idx_users_user_type_created_at ON users (user_type, created_at DESC);
