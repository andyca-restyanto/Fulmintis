// filepath: /backend/src/main/java/com/example/app/modules/testcase/controller/TestCaseAiController.java
package com.example.app.modules.testcase.controller;

import com.example.app.modules.testcase.dto.TestCaseAiCommitRequestDTO;
import com.example.app.modules.testcase.dto.TestCaseAiCommitResponseDTO;
import com.example.app.modules.testcase.dto.TestCaseAiCreatedDTO;
import com.example.app.modules.testcase.dto.TestCaseAiGenerateRequestDTO;
import com.example.app.modules.testcase.dto.TestCaseAiGenerationResponseDTO;
import com.example.app.modules.testcase.dto.TestCaseAiUsageResponseDTO;
import com.example.app.modules.testcase.service.TestCaseAiService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Generate test case dgn AI (menu Test Repository). PROTECTED; OWNER maupun COLLABORATOR; non-member dibalas 404.
 * Hasil generate adalah DRAFT -- test case baru tercipta hanya lewat {@code commit} setelah user mereview.
 */
@RestController
@RequestMapping("/api/projects/{projectId}/test-cases/ai-generation")
@RequiredArgsConstructor
public class TestCaseAiController {

    private final TestCaseAiService service;

    @GetMapping("/usage")
    public TestCaseAiUsageResponseDTO getUsage(@PathVariable UUID projectId, Authentication authentication) {
        return service.getUsage(authentication.getName(), projectId);
    }

    /** 202: job dibuat, prosesnya di latar belakang -- klien polling GET /{id}. */
    @PostMapping
    public ResponseEntity<TestCaseAiCreatedDTO> generate(
            @PathVariable UUID projectId,
            @Valid @RequestBody TestCaseAiGenerateRequestDTO request,
            Authentication authentication
    ) {
        return ResponseEntity.accepted().body(service.submit(authentication.getName(), projectId, request));
    }

    /** Draft terakhir yang belum disimpan (utk melanjutkan review setelah reload), atau 204. */
    @GetMapping("/pending")
    public ResponseEntity<TestCaseAiGenerationResponseDTO> getPending(@PathVariable UUID projectId, Authentication authentication) {
        return service.getPending(authentication.getName(), projectId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @GetMapping("/{generationId}")
    public TestCaseAiGenerationResponseDTO getGeneration(
            @PathVariable UUID projectId, @PathVariable UUID generationId, Authentication authentication
    ) {
        return service.getGeneration(authentication.getName(), projectId, generationId);
    }

    @PostMapping("/{generationId}/commit")
    public ResponseEntity<TestCaseAiCommitResponseDTO> commit(
            @PathVariable UUID projectId,
            @PathVariable UUID generationId,
            @RequestBody TestCaseAiCommitRequestDTO request,
            Authentication authentication
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.commit(authentication.getName(), projectId, generationId, request));
    }
}
