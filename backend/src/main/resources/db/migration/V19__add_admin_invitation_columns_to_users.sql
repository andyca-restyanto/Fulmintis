-- Undangan admin: token + masa berlaku, terpisah dari kolom verifikasi email
-- dan reset password (lihat User.adminInvitationToken). NULL untuk semua user
-- yang bukan hasil undangan.
ALTER TABLE users
    ADD COLUMN admin_invitation_token VARCHAR(255),
    ADD COLUMN admin_invitation_expires_at TIMESTAMP;

CREATE INDEX idx_users_admin_invitation_token
    ON users (admin_invitation_token)
    WHERE admin_invitation_token IS NOT NULL;
