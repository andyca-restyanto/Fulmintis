// backend/src/main/java/com/example/app/modules/testcase/controller/TestCaseController.java
package com.example.app.modules.testcase.controller;

import com.example.app.modules.testcase.TestCasePriority;
import com.example.app.modules.testcase.TestCaseScenarioType;
import com.example.app.modules.testcase.TestCaseType;
import com.example.app.modules.testcase.dto.CreateTestCaseRequestDTO;
import com.example.app.modules.testcase.dto.TestCaseResponseDTO;
import com.example.app.modules.testcase.dto.TestCaseSearchCriteria;
import com.example.app.modules.testcase.dto.UpdateTestCaseRequestDTO;
import com.example.app.modules.testcase.service.TestCaseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * PROTECTED (wajib JWT valid) -- nested di bawah /api/projects/{projectId},
 * tidak di-permitAll() di SecurityConfig jadi otomatis kena
 * anyRequest().authenticated().
 */
@RestController
@RequestMapping("/api/projects/{projectId}/test-cases")
@RequiredArgsConstructor
public class TestCaseController {

    private final TestCaseService testCaseService;

    /**
     * Flow FE: user buka Test Repository -> klik folder -> klik "New Test
     * Case" -> isi semua field -> Save.
     * <p>
     * Balas 404 kalau project tidak ada/user bukan member, ATAU folderId
     * yang dikirim tidak valid utk project ini (requirement #2). Boleh
     * dipanggil OWNER maupun COLLABORATOR (requirement #4).
     */
    @PostMapping
    public ResponseEntity<TestCaseResponseDTO> createTestCase(
            @PathVariable UUID projectId,
            @Valid @RequestBody CreateTestCaseRequestDTO request,
            Authentication authentication
    ) {
        String email = authentication.getName();
        TestCaseResponseDTO response = testCaseService.createTestCase(email, projectId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Search & filter test case (requirement #6) -- SEMUA query param
     * opsional, kombinasi bebas:
     * <pre>
     * GET /api/projects/{projectId}/test-cases
     * GET /api/projects/{projectId}/test-cases?folderId={folderId}
     * GET /api/projects/{projectId}/test-cases?keyword=login
     * GET /api/projects/{projectId}/test-cases?priority=HIGH&type=MANUAL
     * GET /api/projects/{projectId}/test-cases?folderId={id}&keyword=login&scenarioType=POSITIVE
     * </pre>
     * scenarioType diisi persis nama enum-nya: POSITIVE atau NEGATIVE (case-sensitive).
     * <p>
     * SELALU hanya balikin test case berstatus ACTIVE (requirement tambahan
     * #4) -- test case yang sudah di-delete/archive TIDAK PERNAH muncul di
     * endpoint ini, baik utk OWNER maupun COLLABORATOR. Lihat GET
     * {@code /archived} utk daftar test case archived (khusus OWNER).
     */
    @GetMapping
    public List<TestCaseResponseDTO> searchTestCases(
            @PathVariable UUID projectId,
            @RequestParam(required = false) UUID folderId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) TestCasePriority priority,
            @RequestParam(required = false) TestCaseType type,
            @RequestParam(required = false) TestCaseScenarioType scenarioType,
            Authentication authentication
    ) {
        String email = authentication.getName();
        TestCaseSearchCriteria criteria = new TestCaseSearchCriteria(folderId, keyword, priority, type, scenarioType);
        return testCaseService.searchTestCases(email, projectId, criteria);
    }

    /**
     * List test case yang sudah di-delete/archive dalam 1 project
     * (requirement tambahan #2 & #4). HANYA bisa diakses OWNER project --
     * COLLABORATOR yang coba akses dibalas 403 (lihat
     * ProjectAccessForbiddenException). Filter query param SAMA seperti
     * {@link #searchTestCases}, semua opsional.
     * <p>
     * SENGAJA path terpisah ({@code /archived}), bukan query param
     * {@code ?status=ARCHIVED} di endpoint GET biasa -- supaya aturan akses
     * "hanya OWNER" gampang di-enforce di 1 tempat yang jelas, dan endpoint
     * GET biasa (dipakai COLLABORATOR juga) tidak perlu cek role sama
     * sekali utk kasus normal.
     */
    @GetMapping("/archived")
    public List<TestCaseResponseDTO> listArchivedTestCases(
            @PathVariable UUID projectId,
            @RequestParam(required = false) UUID folderId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) TestCasePriority priority,
            @RequestParam(required = false) TestCaseType type,
            @RequestParam(required = false) TestCaseScenarioType scenarioType,
            Authentication authentication
    ) {
        String email = authentication.getName();
        TestCaseSearchCriteria criteria = new TestCaseSearchCriteria(folderId, keyword, priority, type, scenarioType);
        return testCaseService.listArchivedTestCases(email, projectId, criteria);
    }

    /**
     * Detail 1 test case -- dipakai a.l. utk pre-fill form "Edit Test Case"
     * sebelum PUT. Balas 404 kalau id tidak valid utk project ini ATAU
     * (requirement #4) test case-nya archived dan yang minta COLLABORATOR.
     */
    @GetMapping("/{testCaseId}")
    public TestCaseResponseDTO getTestCase(
            @PathVariable UUID projectId,
            @PathVariable UUID testCaseId,
            Authentication authentication
    ) {
        String email = authentication.getName();
        return testCaseService.getTestCaseById(email, projectId, testCaseId);
    }

    /**
     * Edit test case (requirement tambahan #1). Boleh OWNER maupun
     * COLLABORATOR (sama seperti create) -- balas 404 kalau id tidak valid
     * utk project ini / folder baru yg dikirim tidak valid, atau (requirement
     * #4) test case-nya archived dan yang minta COLLABORATOR.
     */
    @PutMapping("/{testCaseId}")
    public TestCaseResponseDTO updateTestCase(
            @PathVariable UUID projectId,
            @PathVariable UUID testCaseId,
            @Valid @RequestBody UpdateTestCaseRequestDTO request,
            Authentication authentication
    ) {
        String email = authentication.getName();
        return testCaseService.updateTestCase(email, projectId, testCaseId, request);
    }

    /**
     * "Delete" test case (requirement tambahan #1 & #2) -- SOFT DELETE:
     * flag status jadi ARCHIVED, row TIDAK dihapus dari database. HANYA
     * boleh OWNER project (requirement #3) -- COLLABORATOR yang coba akses
     * dibalas 403. 204 No Content kalau berhasil, 409 kalau test case-nya
     * SUDAH archived sebelumnya (lihat TestCaseAlreadyArchivedException).
     */
    @DeleteMapping("/{testCaseId}")
    public ResponseEntity<Void> archiveTestCase(
            @PathVariable UUID projectId,
            @PathVariable UUID testCaseId,
            Authentication authentication
    ) {
        String email = authentication.getName();
        testCaseService.archiveTestCase(email, projectId, testCaseId);
        return ResponseEntity.noContent().build();
    }
}
