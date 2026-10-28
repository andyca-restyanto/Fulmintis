// filepath: /backend/src/main/java/com/example/app/modules/automation/controller/AutomationController.java
package com.example.app.modules.automation.controller;

import com.example.app.modules.automation.dto.AutomationGenerationCreatedDTO;
import com.example.app.modules.automation.dto.AutomationGenerationResponseDTO;
import com.example.app.modules.automation.dto.AutomationGenerationSummaryDTO;
import com.example.app.modules.automation.dto.AutomationOptionsResponseDTO;
import com.example.app.modules.automation.dto.AutomationSetupRequestDTO;
import com.example.app.modules.automation.dto.AutomationSetupResponseDTO;
import com.example.app.modules.automation.dto.AutomationUsageResponseDTO;
import com.example.app.modules.automation.dto.GenerateAutomationRequestDTO;
import com.example.app.modules.automation.service.AutomationGenerationService;
import com.example.app.modules.automation.service.AutomationSetupService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Menu Automation: setup struktur (OWNER) dan generate kode automation dari test case dengan AI (semua member).
 * PROTECTED; non-member dibalas 404.
 */
@RestController
@RequestMapping("/api/projects/{projectId}/automation")
@RequiredArgsConstructor
public class AutomationController {

    private final AutomationSetupService setupService;
    private final AutomationGenerationService generationService;

    @GetMapping("/options")
    public AutomationOptionsResponseDTO getOptions(@PathVariable UUID projectId, Authentication authentication) {
        return setupService.getOptions(authentication.getName(), projectId);
    }

    @GetMapping("/setup")
    public AutomationSetupResponseDTO getSetup(@PathVariable UUID projectId, Authentication authentication) {
        return setupService.getSetup(authentication.getName(), projectId);
    }

    /** Upsert (OWNER): maksimal 1 setup per project. */
    @PutMapping("/setup")
    public AutomationSetupResponseDTO saveSetup(
            @PathVariable UUID projectId,
            @Valid @RequestBody AutomationSetupRequestDTO request,
            Authentication authentication
    ) {
        return setupService.saveSetup(authentication.getName(), projectId, request);
    }

    @DeleteMapping("/setup")
    public ResponseEntity<Void> deleteSetup(@PathVariable UUID projectId, Authentication authentication) {
        setupService.deleteSetup(authentication.getName(), projectId);
        return ResponseEntity.noContent().build();
    }

    /** 202: job dibuat, prosesnya di latar belakang -- klien polling GET /generations/{id}. */
    @PostMapping("/generations")
    public ResponseEntity<AutomationGenerationCreatedDTO> generate(
            @PathVariable UUID projectId,
            @Valid @RequestBody GenerateAutomationRequestDTO request,
            Authentication authentication
    ) {
        return ResponseEntity.accepted().body(generationService.submit(authentication.getName(), projectId, request));
    }

    @GetMapping("/generations")
    public List<AutomationGenerationSummaryDTO> listGenerations(@PathVariable UUID projectId, Authentication authentication) {
        return generationService.listGenerations(authentication.getName(), projectId);
    }

    @GetMapping("/generations/{generationId}")
    public AutomationGenerationResponseDTO getGeneration(
            @PathVariable UUID projectId, @PathVariable UUID generationId, Authentication authentication
    ) {
        return generationService.getGeneration(authentication.getName(), projectId, generationId);
    }

    @GetMapping("/generations/{generationId}/download")
    public ResponseEntity<byte[]> download(
            @PathVariable UUID projectId, @PathVariable UUID generationId, Authentication authentication
    ) {
        AutomationGenerationService.AutomationDownload download =
                generationService.download(authentication.getName(), projectId, generationId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(download.fileName()).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .contentType(MediaType.parseMediaType("application/zip"))
                .body(download.content());
    }

    @GetMapping("/usage")
    public AutomationUsageResponseDTO getUsage(@PathVariable UUID projectId, Authentication authentication) {
        return generationService.getUsage(authentication.getName(), projectId);
    }
}
