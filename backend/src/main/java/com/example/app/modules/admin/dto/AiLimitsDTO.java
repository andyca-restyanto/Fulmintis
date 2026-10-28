// filepath: /backend/src/main/java/com/example/app/modules/admin/dto/AiLimitsDTO.java
package com.example.app.modules.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Map;

/**
 * Respons GET/PUT/DELETE /api/admin/ai-limits. Match: AiLimits (FE, modules/admin/types).
 * Nilai = batas EFEKTIF (database ditimpa di atas bawaan konfigurasi). source: kunci "FREE.AUTOMATION_DAILY_LIMIT"
 * -> "DATABASE" | "DEFAULT". defaults: nilai bawaan (untuk tombol reset & petunjuk). 0 pada batas harian = tanpa batas.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiLimitsDTO {

    private GlobalLimits global;
    /** Kunci: FREE | VIP_MONTHLY | VIP_YEARLY. */
    private Map<String, TierLimits> tiers;
    private Map<String, String> source;
    private AiLimitsDefaults defaults;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class GlobalLimits {
        private Integer dailyLimit;
        private Integer quotaCircuitMinutes;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class TierLimits {
        private AutomationLimits automation;
        private TestcaseLimits testcase;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class AutomationLimits {
        private Integer dailyLimit;
        private Integer maxTestCases;
        private Integer maxOutputTokens;
        private Integer timeoutSeconds;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class TestcaseLimits {
        private Integer dailyLimit;
        private Integer maxDrafts;
        private Integer maxOutputTokens;
        private Integer timeoutSeconds;
    }

    /** Bentuk sama dengan bagian nilai (global + tiers), tanpa source/defaults. */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class AiLimitsDefaults {
        private GlobalLimits global;
        private Map<String, TierLimits> tiers;
    }
}
