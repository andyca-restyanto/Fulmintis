// filepath: /backend/src/main/java/com/example/app/modules/admin/dto/AiLimitsUpdateRequestDTO.java
package com.example.app.modules.admin.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.Map;

/**
 * Body PUT /api/admin/ai-limits. Match: AiLimitsUpdateRequest (FE). Berisi SEMUA nilai (global + 3 tier).
 * Nilai 0 pada batas harian berarti TANPA BATAS dan hanya diterima bila confirmUnlimited = true.
 */
@Getter
@Setter
public class AiLimitsUpdateRequestDTO {

    @NotNull(message = "Batas global wajib diisi")
    private AiLimitsDTO.GlobalLimits global;

    @NotNull(message = "Batas per tier wajib diisi")
    private Map<String, AiLimitsDTO.TierLimits> tiers;

    private boolean confirmUnlimited;
}
