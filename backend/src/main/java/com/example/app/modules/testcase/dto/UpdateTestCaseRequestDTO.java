// backend/src/main/java/com/example/app/modules/testcase/dto/UpdateTestCaseRequestDTO.java
package com.example.app.modules.testcase.dto;

import com.example.app.modules.testcase.TestCasePriority;
import com.example.app.modules.testcase.TestCaseScenarioType;
import com.example.app.modules.testcase.TestCaseType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/**
 * Body request utk PUT /api/projects/{projectId}/test-cases/{testCaseId}
 * (requirement #1: edit test case). Struktur SENGAJA dibuat mirror 1:1 dgn
 * CreateTestCaseRequestDTO (field wajib sama: folderId, title, priority,
 * type) supaya form "Edit Test Case" di FE bisa reuse komponen yang sama
 * dgn form "New Test Case" (lihat CreateTestCaseModal.vue).
 * <p>
 * folderId TETAP wajib diisi (bukan cuma boleh diisi saat create) --
 * memungkinkan user pindahkan test case ke folder lain saat edit, tapi
 * folder tujuan WAJIB tetap folder yang valid di project yang sama
 * (divalidasi di TestCaseServiceImpl, sama seperti saat create).
 */
@Getter
@Setter
public class UpdateTestCaseRequestDTO {

    @NotNull(message = "Folder wajib dipilih")
    private UUID folderId;

    @NotBlank(message = "Title wajib diisi")
    @Size(max = 255, message = "Title maksimal 255 karakter")
    private String title;

    @NotNull(message = "Priority wajib dipilih")
    private TestCasePriority priority;

    @NotNull(message = "Type wajib dipilih")
    private TestCaseType type;

    private TestCaseScenarioType scenarioType;
    private String description;
    private String objective;
    private String precondition;
    private String testStep;
    private String expectedResult;
}
