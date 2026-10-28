// filepath: /backend/src/main/java/com/example/app/modules/testcase/dto/TestCaseAiUsageResponseDTO.java
package com.example.app.modules.testcase.dto;

import lombok.Builder;
import lombok.Getter;

/**
 * GET /test-cases/ai-generation/usage. Match dgn TestCaseAiUsage (FE).
 * aiEnabled=false = provider belum dikonfigurasi (tombol Generate nonaktif). sandbox=true = akun free tier penyedia: data BISA dipakai
 * penyedia utk memperbaiki produknya -> UI memperingatkan "jangan masukkan data asli". Tidak memuat nama provider/model.
 */
@Getter
@Builder
public class TestCaseAiUsageResponseDTO {
    private boolean aiEnabled;
    // FREE | VIP
    private String tier;
    private int maxDraftsPerGeneration;
    private int maxRequirementChars;
    // 0 = tanpa batas
    private int dailyLimit;
    private int usedToday;
    private boolean sandbox;
}
