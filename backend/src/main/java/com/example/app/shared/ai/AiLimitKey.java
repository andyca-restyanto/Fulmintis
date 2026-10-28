// filepath: /backend/src/main/java/com/example/app/shared/ai/AiLimitKey.java
package com.example.app.shared.ai;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Daftar batasan AI yang boleh diubah admin lewat database (tabel ai_limit_setting). Nama model, provider, API key,
 * dan billing tier SENGAJA tidak ada di sini: tetap dari environment (salah isi bisa mematikan fitur / memicu biaya).
 * Rentang nilai adalah pengaman terhadap salah ketik; min = 0 berarti "0 = tanpa batas" (butuh konfirmasi di API).
 */
public enum AiLimitKey {
    AUTOMATION_DAILY_LIMIT(false, 0, 10_000),
    AUTOMATION_MAX_TEST_CASES(false, 1, 100),
    AUTOMATION_MAX_OUTPUT_TOKENS(false, 256, 65_536),
    AUTOMATION_TIMEOUT_SECONDS(false, 10, 600),
    TESTCASE_DAILY_LIMIT(false, 0, 10_000),
    TESTCASE_MAX_DRAFTS(false, 1, 50),
    TESTCASE_MAX_OUTPUT_TOKENS(false, 256, 65_536),
    TESTCASE_TIMEOUT_SECONDS(false, 10, 600),
    GLOBAL_DAILY_LIMIT(true, 0, 1_000_000),
    QUOTA_CIRCUIT_MINUTES(true, 1, 1_440);

    public static final String GLOBAL_SCOPE = "GLOBAL";

    private final boolean global;
    private final int min;
    private final int max;

    AiLimitKey(boolean global, int min, int max) {
        this.global = global;
        this.min = min;
        this.max = max;
    }

    public boolean isGlobal() { return global; }
    public int min() { return min; }
    public int max() { return max; }
    public boolean unlimitedAtZero() { return min == 0; }
    public boolean accepts(int value) { return value >= min && value <= max; }

    /** Kunci di tabel: "FREE.AUTOMATION_DAILY_LIMIT" / "GLOBAL.GLOBAL_DAILY_LIMIT". */
    public String storageKey(String scope) { return scope + "." + name(); }

    public static List<AiLimitKey> tierKeys() {
        return Arrays.stream(values()).filter(k -> !k.global).toList();
    }

    public static List<AiLimitKey> globalKeys() {
        return Arrays.stream(values()).filter(k -> k.global).toList();
    }

    /** Tier yang punya batas sendiri (VIP lama tidak termasuk: dibaca sebagai VIP_MONTHLY). */
    public static List<AiTier> limitTiers() {
        return List.of(AiTier.FREE, AiTier.VIP_MONTHLY, AiTier.VIP_YEARLY);
    }

    /** "FREE.X" / "GLOBAL.X" -> kunci, atau kosong bila bukan kunci yang dikenal. */
    public static Optional<AiLimitKey> fromStorageKey(String storageKey) {
        if (storageKey == null) return Optional.empty();
        int dot = storageKey.indexOf('.');
        if (dot <= 0) return Optional.empty();
        String scope = storageKey.substring(0, dot);
        AiLimitKey key;
        try {
            key = valueOf(storageKey.substring(dot + 1));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
        boolean scopeOk = key.global
                ? GLOBAL_SCOPE.equals(scope)
                : limitTiers().stream().anyMatch(t -> t.name().equals(scope));
        return scopeOk ? Optional.of(key) : Optional.empty();
    }

    public int read(AiTierProperties p) {
        return switch (this) {
            case AUTOMATION_DAILY_LIMIT -> p.getDailyLimit();
            case AUTOMATION_MAX_TEST_CASES -> p.getMaxTestCases();
            case AUTOMATION_MAX_OUTPUT_TOKENS -> p.getMaxOutputTokens();
            case AUTOMATION_TIMEOUT_SECONDS -> p.getTimeoutSeconds();
            case TESTCASE_DAILY_LIMIT -> p.getTestcaseDailyLimit();
            case TESTCASE_MAX_DRAFTS -> p.getTestcaseMaxDrafts();
            case TESTCASE_MAX_OUTPUT_TOKENS -> p.getTestcaseMaxOutputTokens();
            case TESTCASE_TIMEOUT_SECONDS -> p.getTestcaseTimeoutSeconds();
            default -> throw new IllegalStateException(name() + " bukan batas per tier");
        };
    }

    public void write(AiTierProperties p, int value) {
        switch (this) {
            case AUTOMATION_DAILY_LIMIT -> p.setDailyLimit(value);
            case AUTOMATION_MAX_TEST_CASES -> p.setMaxTestCases(value);
            case AUTOMATION_MAX_OUTPUT_TOKENS -> p.setMaxOutputTokens(value);
            case AUTOMATION_TIMEOUT_SECONDS -> p.setTimeoutSeconds(value);
            case TESTCASE_DAILY_LIMIT -> p.setTestcaseDailyLimit(value);
            case TESTCASE_MAX_DRAFTS -> p.setTestcaseMaxDrafts(value);
            case TESTCASE_MAX_OUTPUT_TOKENS -> p.setTestcaseMaxOutputTokens(value);
            case TESTCASE_TIMEOUT_SECONDS -> p.setTestcaseTimeoutSeconds(value);
            default -> throw new IllegalStateException(name() + " bukan batas per tier");
        }
    }
}
