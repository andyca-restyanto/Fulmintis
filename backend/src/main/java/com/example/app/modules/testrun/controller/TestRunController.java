// backend/src/main/java/com/example/app/modules/testrun/controller/TestRunController.java
package com.example.app.modules.testrun.controller;

import com.example.app.modules.testrun.dto.AddTestCasesToTestRunRequestDTO;
import com.example.app.modules.testrun.dto.CreateTestRunRequestDTO;
import com.example.app.modules.testrun.dto.SyncTestRunCasesRequestDTO;
import com.example.app.modules.testrun.dto.TestResultResponseDTO;
import com.example.app.modules.testrun.dto.TestRunDetailResponseDTO;
import com.example.app.modules.testrun.dto.TestRunResponseDTO;
import com.example.app.modules.testrun.dto.UpdateTestResultRequestDTO;
import com.example.app.modules.testrun.service.TestRunService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

/**
 * PROTECTED (wajib JWT valid) -- nested di bawah /api/projects/{projectId},
 * tidak di-permitAll() di SecurityConfig jadi otomatis kena
 * anyRequest().authenticated().
 */
@RestController
@RequestMapping("/api/projects/{projectId}/test-runs")
@RequiredArgsConstructor
public class TestRunController {

    private final TestRunService testRunService;

    /**
     * Flow FE (step 2-6): user di menu Test Run klik "Add New Test Run" ->
     * isi title & description -> klik "Select Test Case" -> pilih test case
     * yang mau di-run -> klik "Create Test Run" -- SEMUANYA dikirim dalam
     * 1x submit ini (testCaseIds ikut di body, opsional).
     * <p>
     * HANYA boleh OWNER project (requirement #6) -- balas 403 kalau bukan
     * owner, 404 kalau project tidak ada/user bukan member ATAU ada
     * testCaseIds yang tidak valid utk project ini.
     */
    @PostMapping
    public ResponseEntity<TestRunDetailResponseDTO> createTestRun(
            @PathVariable UUID projectId,
            @Valid @RequestBody CreateTestRunRequestDTO request,
            Authentication authentication
    ) {
        String email = authentication.getName();
        TestRunDetailResponseDTO response = testRunService.createTestRun(email, projectId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Daftar semua test run di project ini (menu Test Run, ringkas). Boleh
     * OWNER maupun COLLABORATOR.
     */
    @GetMapping
    public List<TestRunResponseDTO> listTestRuns(
            @PathVariable UUID projectId,
            Authentication authentication
    ) {
        String email = authentication.getName();
        return testRunService.listTestRuns(email, projectId);
    }

    /**
     * Detail 1 test run TERMASUK daftar test case yang sudah di-mapping +
     * hasil eksekusinya (requirement #2). Boleh OWNER maupun COLLABORATOR.
     */
    @GetMapping("/{testRunId}")
    public TestRunDetailResponseDTO getTestRunDetail(
            @PathVariable UUID projectId,
            @PathVariable UUID testRunId,
            Authentication authentication
    ) {
        String email = authentication.getName();
        return testRunService.getTestRunDetail(email, projectId, testRunId);
    }

    /**
     * Hapus test run beserta semua test result di dalamnya (permanen).
     * HANYA boleh OWNER project -- balas 403 kalau bukan owner.
     */
    @DeleteMapping("/{testRunId}")
    public ResponseEntity<Void> deleteTestRun(
            @PathVariable UUID projectId,
            @PathVariable UUID testRunId,
            Authentication authentication
    ) {
        String email = authentication.getName();
        testRunService.deleteTestRun(email, projectId, testRunId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Tambah test case ke test run yang SUDAH ADA (requirement #2).
     * Boleh OWNER MAUPUN COLLABORATOR (requirement #7). Utk cari test case
     * yang mau ditambahkan (requirement #3: search by name/id), FE pakai
     * endpoint yang sudah ada: GET /api/projects/{projectId}/test-cases
     * ?keyword=... (mendukung search title ATAUPUN id test case).
     */
    @PostMapping("/{testRunId}/test-cases")
    public TestRunDetailResponseDTO addTestCasesToRun(
            @PathVariable UUID projectId,
            @PathVariable UUID testRunId,
            @Valid @RequestBody AddTestCasesToTestRunRequestDTO request,
            Authentication authentication
    ) {
        String email = authentication.getName();
        return testRunService.addTestCasesToRun(email, projectId, testRunId, request);
    }

    /**
     * Simpan checklist Manage Cases: body berisi daftar AKHIR test case yang
     * harus ada di run ini. Yang baru tercentang ditambah, yang di-uncheck
     * dilepas (delete mapping), dalam 1 transaksi. Boleh OWNER MAUPUN
     * COLLABORATOR. testCaseIds boleh kosong (= kosongkan run), tapi tidak
     * boleh null.
     */
    @PutMapping("/{testRunId}/test-cases")
    public TestRunDetailResponseDTO syncTestCasesInRun(
            @PathVariable UUID projectId,
            @PathVariable UUID testRunId,
            @Valid @RequestBody SyncTestRunCasesRequestDTO request,
            Authentication authentication
    ) {
        String email = authentication.getName();
        return testRunService.syncTestCasesInRun(email, projectId, testRunId, request);
    }

    /**
     * Requirement tambahan #1 & #3: lepas 1 test case dari test run yang
     * sudah ada (kebalikan dari POST di atas) -- Boleh OWNER MAUPUN
     * COLLABORATOR (requirement tambahan #3, disamakan dgn add, BUKAN
     * owner-only seperti delete test run di requirement tambahan #2).
     * Balas 404 kalau testCaseId ini memang tidak sedang ke-mapping ke
     * test run ini.
     */
    @DeleteMapping("/{testRunId}/test-cases/{testCaseId}")
    public TestRunDetailResponseDTO removeTestCaseFromRun(
            @PathVariable UUID projectId,
            @PathVariable UUID testRunId,
            @PathVariable UUID testCaseId,
            Authentication authentication
    ) {
        String email = authentication.getName();
        return testRunService.removeTestCaseFromRun(email, projectId, testRunId, testCaseId);
    }

    /**
     * Catat hasil eksekusi 1 test case di dalam test run (status + comment,
     * requirement #5). Boleh OWNER maupun COLLABORATOR.
     */
    @PatchMapping("/{testRunId}/test-results/{testResultId}")
    public TestResultResponseDTO updateTestResult(
            @PathVariable UUID projectId,
            @PathVariable UUID testRunId,
            @PathVariable UUID testResultId,
            @Valid @RequestBody UpdateTestResultRequestDTO request,
            Authentication authentication
    ) {
        String email = authentication.getName();
        return testRunService.updateTestResult(email, projectId, testRunId, testResultId, request);
    }

    /**
     * Upload/replace evidence (image/video) utk 1 test result (requirement
     * #5). multipart/form-data, field wajib "file". Balas 400 kalau file
     * bukan image/video atau melebihi 50MB.
     */
    @PostMapping(value = "/{testRunId}/test-results/{testResultId}/evidence", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public TestResultResponseDTO uploadEvidence(
            @PathVariable UUID projectId,
            @PathVariable UUID testRunId,
            @PathVariable UUID testResultId,
            @RequestParam("file") MultipartFile file,
            Authentication authentication
    ) {
        String email = authentication.getName();
        return testRunService.uploadEvidence(email, projectId, testRunId, testResultId, file);
    }

    /**
     * Download/tampilkan file evidence 1 test result. Balas 404 kalau
     * testResultId tidak valid ATAU belum ada evidence yang di-upload.
     */
    @GetMapping("/{testRunId}/test-results/{testResultId}/evidence")
    public ResponseEntity<org.springframework.core.io.Resource> downloadEvidence(
            @PathVariable UUID projectId,
            @PathVariable UUID testRunId,
            @PathVariable UUID testResultId,
            Authentication authentication
    ) {
        String email = authentication.getName();
        var evidence = testRunService.loadEvidence(email, projectId, testRunId, testResultId);

        // "inline" HANYA untuk tipe whitelist (image/video) supaya FE bisa
        // menampilkannya; file lama di luar whitelist dipaksa "attachment".
        // nosniff + CSP sandbox = browser tidak boleh menebak tipe atau
        // menjalankan skrip walau file-nya ternyata berbahaya.
        ContentDisposition contentDisposition = (evidence.inlineSafe()
                ? ContentDisposition.inline()
                : ContentDisposition.attachment())
                .filename(evidence.storedFileName())
                .build();

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition.toString())
                .header("X-Content-Type-Options", "nosniff")
                .header("Content-Security-Policy", "default-src 'none'; sandbox")
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .contentType(MediaType.parseMediaType(evidence.contentType()))
                .body(evidence.resource());
    }
}
