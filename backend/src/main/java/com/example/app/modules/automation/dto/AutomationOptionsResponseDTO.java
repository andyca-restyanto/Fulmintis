// filepath: /backend/src/main/java/com/example/app/modules/automation/dto/AutomationOptionsResponseDTO.java
package com.example.app.modules.automation.dto;

import com.example.app.modules.automation.AutomationFramework;
import com.example.app.modules.automation.AutomationLanguage;
import com.example.app.modules.automation.AutomationPattern;
import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.Map;

/**
 * Opsi setup + matriks kompatibilitas. SATU sumber kebenaran: frontend memakai ini utk
 * menonaktifkan kombinasi yang tidak valid, tidak menulis ulang aturannya.
 * Match dgn AutomationOptions (FE).
 */
@Getter
@Builder
public class AutomationOptionsResponseDTO {
    private List<AutomationFramework> frameworks;
    private List<AutomationLanguage> languages;
    // framework -> bahasa yang valid untuknya.
    private Map<AutomationFramework, List<AutomationLanguage>> compatibility;
    private List<AutomationPattern> patterns;
}
