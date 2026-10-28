-- Status aktif/nonaktif akun (lihat User.active). Admin yang dinonaktifkan tidak bisa login
-- dan token lamanya langsung ditolak. Semua akun yang sudah ada tetap aktif (DEFAULT TRUE).
ALTER TABLE users
    ADD COLUMN IF NOT EXISTS is_active BOOLEAN NOT NULL DEFAULT TRUE;
