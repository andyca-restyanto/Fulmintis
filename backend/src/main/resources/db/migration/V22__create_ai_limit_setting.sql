-- backend/src/main/resources/db/migration/V22__create_ai_limit_setting.sql
-- Batas AI yang bisa diubah admin tanpa deploy (menu admin "Batas AI").
-- Tidak ada baris = nilai bawaan dari konfigurasi (app.ai.*) dipakai, jadi migrasi ini tidak mengubah perilaku.
CREATE TABLE IF NOT EXISTS ai_limit_setting (
    setting_key   VARCHAR(80)  PRIMARY KEY,   -- mis. FREE.AUTOMATION_DAILY_LIMIT, GLOBAL.GLOBAL_DAILY_LIMIT
    setting_value INTEGER      NOT NULL,
    updated_by    VARCHAR(255) NOT NULL,
    updated_at    TIMESTAMP    NOT NULL
);

-- Tier yang tercatat di baris generate kini bisa FREE / VIP_MONTHLY / VIP_YEARLY (sebelumnya FREE / VIP).
-- "VIP_MONTHLY" 11 karakter > VARCHAR(10), jadi dilebarkan. Baris lama ("VIP") tetap sah.
ALTER TABLE automation_generation ALTER COLUMN tier TYPE VARCHAR(20);
ALTER TABLE testcase_ai_generation ALTER COLUMN tier TYPE VARCHAR(20);
