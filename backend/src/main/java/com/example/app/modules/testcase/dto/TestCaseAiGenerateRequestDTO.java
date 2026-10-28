// filepath: /backend/src/main/java/com/example/app/modules/testcase/dto/TestCaseAiGenerateRequestDTO.java
package com.example.app.modules.testcase.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/** POST /test-cases/ai-generation. Match dgn GenerateTestCasesRequest (FE). Batas panjang & jumlah dicek di service (konfigurasi per tier). */
@Getter
@Setter
public class TestCaseAiGenerateRequestDTO {

    @NotNull(message = "Folder wajib dipilih")
    private UUID folderId;

    @NotBlank(message = "Requirement wajib diisi")
    private String requirement;

    // Opsional: 1..maxDraftsPerGeneration; kosong = batas tier.
    private Integer count;

    // Opsional: kosong = true.
    private Boolean includeNegative;
}
