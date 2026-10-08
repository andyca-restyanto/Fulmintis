// backend/src/main/java/com/example/app/modules/testrun/dto/AddTestCasesToTestRunRequestDTO.java
package com.example.app.modules.testrun.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.UUID;

/**
 * Body request utk POST /api/projects/{projectId}/test-runs/{testRunId}/test-cases
 * (requirement #2 & #7) -- nambah test case ke test run yang SUDAH ADA.
 * Boleh dipanggil OWNER maupun COLLABORATOR (requirement #7, beda dgn
 * create test run yang OWNER-only).
 */
@Getter
@Setter
public class AddTestCasesToTestRunRequestDTO {

    @NotEmpty(message = "Pilih minimal 1 test case")
    private List<UUID> testCaseIds;
}
