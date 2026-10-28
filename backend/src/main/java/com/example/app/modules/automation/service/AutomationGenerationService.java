// filepath: /backend/src/main/java/com/example/app/modules/automation/service/AutomationGenerationService.java
package com.example.app.modules.automation.service;

import com.example.app.modules.automation.dto.AutomationGenerationCreatedDTO;
import com.example.app.modules.automation.dto.AutomationGenerationResponseDTO;
import com.example.app.modules.automation.dto.AutomationGenerationSummaryDTO;
import com.example.app.modules.automation.dto.AutomationUsageResponseDTO;
import com.example.app.modules.automation.dto.GenerateAutomationRequestDTO;

import java.util.List;
import java.util.UUID;

public interface AutomationGenerationService {

    /**
     * Semua member. Memvalidasi lalu membuat job QUEUED dan MENGEMBALIKAN SEGERA (proses AI berjalan di latar
     * belakang; klien polling {@link #getGeneration}). Penolakan: setup belum diatur (409), AI tidak aktif /
     * circuit terbuka (503 umum), terlalu banyak test case (400), batas harian (429), sudah ada job berjalan (409).
     */
    AutomationGenerationCreatedDTO submit(String userEmail, UUID projectId, GenerateAutomationRequestDTO request);

    /** Riwayat project (terbaru dulu, maks 20), tanpa isi berkas. Semua member. */
    List<AutomationGenerationSummaryDTO> listGenerations(String userEmail, UUID projectId);

    AutomationGenerationResponseDTO getGeneration(String userEmail, UUID projectId, UUID generationId);

    /** Zip hasil generate (hanya SUCCEEDED; selain itu 409). */
    AutomationDownload download(String userEmail, UUID projectId, UUID generationId);

    AutomationUsageResponseDTO getUsage(String userEmail, UUID projectId);

    record AutomationDownload(String fileName, byte[] content) {
    }
}
