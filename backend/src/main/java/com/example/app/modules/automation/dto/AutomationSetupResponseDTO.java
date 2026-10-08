// filepath: /backend/src/main/java/com/example/app/modules/automation/dto/AutomationSetupResponseDTO.java
package com.example.app.modules.automation.dto;

import com.example.app.modules.automation.AutomationFramework;
import com.example.app.modules.automation.AutomationLanguage;
import com.example.app.modules.automation.AutomationPattern;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/** Match dgn AutomationSetup (FE). {@code configured=false} = project belum punya setup (field lain null). */
@Getter
@Builder
public class AutomationSetupResponseDTO {
    private boolean configured;
    private AutomationFramework framework;
    private AutomationLanguage language;
    private AutomationPattern pattern;
    private String structureNotes;
    private LocalDateTime updatedAt;
    private String updatedBy;
}
