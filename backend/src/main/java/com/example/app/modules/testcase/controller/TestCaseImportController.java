// filepath: /backend/src/main/java/com/example/app/modules/testcase/controller/TestCaseImportController.java
package com.example.app.modules.testcase.controller;

import com.example.app.modules.testcase.dto.TestCaseImportResultDTO;
import com.example.app.modules.testcase.service.TestCaseImportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

/**
 * Import test case dari Excel. PROTECTED -- OWNER maupun COLLABORATOR (sama
 * seperti membuat test case); non-member dibalas 404.
 */
@RestController
@RequestMapping("/api/projects/{projectId}/test-cases/import")
@RequiredArgsConstructor
public class TestCaseImportController {

    private static final MediaType XLSX =
            MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final TestCaseImportService testCaseImportService;

    /** Template .xlsx (header, baris contoh, dropdown, petunjuk). */
    @GetMapping("/template")
    public ResponseEntity<byte[]> downloadTemplate(@PathVariable UUID projectId, Authentication authentication) {
        byte[] template = testCaseImportService.buildTemplate(authentication.getName(), projectId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename("test-case-import-template.xlsx").build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .contentType(XLSX)
                .body(template);
    }

    /**
     * multipart/form-data: field {@code file} (.xlsx), query {@code folderId} dan
     * {@code dryRun}. {@code dryRun} DEFAULT true (aman: kalau klien lupa mengirimnya,
     * tidak ada yang tersimpan); simpan sungguhan butuh {@code dryRun=false} eksplisit.
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public TestCaseImportResultDTO importTestCases(
            @PathVariable UUID projectId,
            @RequestParam UUID folderId,
            @RequestParam(defaultValue = "true") boolean dryRun,
            @RequestParam("file") MultipartFile file,
            Authentication authentication
    ) {
        return testCaseImportService.importTestCases(authentication.getName(), projectId, folderId, file, dryRun);
    }
}
