// filepath: /backend/src/main/java/com/example/app/modules/automation/dto/AutomationSetupRequestDTO.java
package com.example.app.modules.automation.dto;

import com.example.app.modules.automation.AutomationFramework;
import com.example.app.modules.automation.AutomationLanguage;
import com.example.app.modules.automation.AutomationPattern;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** PUT /automation/setup (upsert, OWNER). Match dgn SaveAutomationSetupRequest (FE). */
@Getter
@Setter
public class AutomationSetupRequestDTO {

    @NotNull(message = "Framework wajib dipilih")
    private AutomationFramework framework;

    @NotNull(message = "Bahasa pemrograman wajib dipilih")
    private AutomationLanguage language;

    // Opsional; kosong = PAGE_OBJECT_MODEL.
    private AutomationPattern pattern;

    @Size(max = 4000, message = "Struktur maksimal 4000 karakter")
    private String structureNotes;
}
