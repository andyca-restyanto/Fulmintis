// backend/src/main/java/com/example/app/modules/testrun/dto/TestRunDetailResponseDTO.java
package com.example.app.modules.testrun.dto;

import com.example.app.modules.testrun.TestRunStatus;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Response GET /api/projects/{projectId}/test-runs/{testRunId} DAN response
 * POST create/add-test-cases -- sama seperti TestRunResponseDTO TAPI
 * termasuk testResults (daftar test case yang sudah di-mapping ke run ini
 * + hasil eksekusinya masing-masing, requirement #2).
 */
@Getter
@Setter
@Builder
public class TestRunDetailResponseDTO {
    private UUID id;
    private UUID projectId;
    private String title;
    private String description;
    private TestRunStatus status;

    private List<TestResultResponseDTO> testResults;

    private LocalDateTime createdAt;
    private String createdBy;
    private LocalDateTime updatedAt;
    private String updatedBy;
}
