// filepath: /backend/src/main/java/com/example/app/modules/automation/dto/AutomationGenerationResponseDTO.java
package com.example.app.modules.automation.dto;

import com.example.app.modules.automation.AutomationFramework;
import com.example.app.modules.automation.AutomationGenerationStatus;
import com.example.app.modules.automation.AutomationLanguage;
import com.example.app.modules.automation.AutomationPattern;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Detail satu generate. Match dgn AutomationGeneration (FE).
 * {@code files} berisi hasil hanya kalau SUCCEEDED; selain itu daftar kosong.
 */
@Getter
@Builder
public class AutomationGenerationResponseDTO {
    private UUID id;
    private AutomationGenerationStatus status;
    private AutomationFramework framework;
    private AutomationLanguage language;
    private AutomationPattern pattern;
    private int testCaseCount;
    private String requestedBy;
    private LocalDateTime createdAt;
    private LocalDateTime finishedAt;
    private List<AutomationFileDTO> files;
    private String notes;
    private String errorCode;
    private String errorMessage;
}
