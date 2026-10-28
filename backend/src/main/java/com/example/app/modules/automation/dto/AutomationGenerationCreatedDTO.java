// filepath: /backend/src/main/java/com/example/app/modules/automation/dto/AutomationGenerationCreatedDTO.java
package com.example.app.modules.automation.dto;

import com.example.app.modules.automation.AutomationGenerationStatus;
import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

/** Respons 202 POST /automation/generations. Match dgn AutomationGenerationCreated (FE). */
@Getter
@Builder
public class AutomationGenerationCreatedDTO {
    private UUID id;
    private AutomationGenerationStatus status;
}
