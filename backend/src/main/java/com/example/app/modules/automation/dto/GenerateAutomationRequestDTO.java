// filepath: /backend/src/main/java/com/example/app/modules/automation/dto/GenerateAutomationRequestDTO.java
package com.example.app.modules.automation.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.UUID;

/** POST /automation/generations. Match dgn GenerateAutomationRequest (FE). */
@Getter
@Setter
public class GenerateAutomationRequestDTO {

    // Batas jumlah per tier dicek di service (config), bukan di anotasi.
    @NotEmpty(message = "Pilih minimal satu test case")
    private List<UUID> testCaseIds;
}
