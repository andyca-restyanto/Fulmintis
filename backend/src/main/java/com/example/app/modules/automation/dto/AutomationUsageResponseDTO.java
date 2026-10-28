// filepath: /backend/src/main/java/com/example/app/modules/automation/dto/AutomationUsageResponseDTO.java
package com.example.app.modules.automation.dto;

import lombok.Builder;
import lombok.Getter;

/**
 * GET /automation/usage. Match dgn AutomationUsage (FE).
 * {@code aiEnabled=false} = provider AI belum dikonfigurasi: tombol Generate harus nonaktif.
 * Tidak memuat nama provider/model (rahasia konfigurasi).
 */
@Getter
@Builder
public class AutomationUsageResponseDTO {
    private boolean aiEnabled;
    // FREE | VIP (VIP_MONTHLY dan VIP_YEARLY digabung)
    private String tier;
    private int maxTestCasesPerGeneration;
    // 0 = tanpa batas
    private int dailyLimit;
    private int usedToday;
}
