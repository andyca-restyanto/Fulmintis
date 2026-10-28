// filepath: /backend/src/main/java/com/example/app/modules/automation/dto/AutomationGenerationSummaryDTO.java
package com.example.app.modules.automation.dto;

import com.example.app.modules.automation.AutomationFramework;
import com.example.app.modules.automation.AutomationGenerationStatus;
import com.example.app.modules.automation.AutomationLanguage;
import com.example.app.modules.automation.AutomationPattern;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

/** Satu baris riwayat (TANPA isi berkas). Match dgn AutomationGenerationSummary (FE). */
@Getter
@Builder
public class AutomationGenerationSummaryDTO {
    private UUID id;
    private AutomationGenerationStatus status;
    private AutomationFramework framework;
    private AutomationLanguage language;
    private AutomationPattern pattern;
    private int testCaseCount;
    private String requestedBy;
    private LocalDateTime createdAt;
    private LocalDateTime finishedAt;
    // Terisi hanya kalau FAILED. Pesan sudah aman utk ditampilkan apa adanya ke user.
    private String errorCode;
    private String errorMessage;
}
