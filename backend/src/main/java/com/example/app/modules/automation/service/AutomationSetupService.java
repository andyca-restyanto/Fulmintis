// filepath: /backend/src/main/java/com/example/app/modules/automation/service/AutomationSetupService.java
package com.example.app.modules.automation.service;

import com.example.app.modules.automation.dto.AutomationOptionsResponseDTO;
import com.example.app.modules.automation.dto.AutomationSetupRequestDTO;
import com.example.app.modules.automation.dto.AutomationSetupResponseDTO;

import java.util.UUID;

public interface AutomationSetupService {

    /** Semua member. */
    AutomationOptionsResponseDTO getOptions(String userEmail, UUID projectId);

    /** Semua member. Belum diatur -> configured=false. */
    AutomationSetupResponseDTO getSetup(String userEmail, UUID projectId);

    /** OWNER saja (memengaruhi semua anggota). Upsert: maksimal 1 setup per project. */
    AutomationSetupResponseDTO saveSetup(String userEmail, UUID projectId, AutomationSetupRequestDTO request);

    /** OWNER saja. Idempoten (tidak ada setup = tidak error). */
    void deleteSetup(String userEmail, UUID projectId);
}
