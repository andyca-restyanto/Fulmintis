// backend/src/main/java/com/example/app/modules/testrun/dto/TestRunResponseDTO.java
package com.example.app.modules.testrun.dto;

import com.example.app.modules.testrun.TestRunStatus;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Response GET /api/projects/{projectId}/test-runs -- daftar test run di 1
 * project (ringkas, TIDAK termasuk detail per-test-case -- lihat
 * TestRunDetailResponseDTO utk itu). Pola split list/detail ini sama
 * seperti ProjectListItem vs ProjectDetail di modul project.
 */
@Getter
@Setter
@Builder
public class TestRunResponseDTO {
    private UUID id;
    private UUID projectId;
    private String title;
    private String description;
    private TestRunStatus status;

    // Jumlah test case yang sudah di-mapping ke run ini -- dihitung dari
    // COUNT(test_result) WHERE test_run_id = ini, bukan field tersendiri.
    private long testCaseCount;

    // Breakdown status, dihitung dari test_result yang sama (bukan query
    // terpisah) -- dipakai FE utk progress bar & angka "X passed / Y
    // failed / Z blocked" di kartu listing test run, tanpa perlu fetch
    // detail penuh (GET .../test-runs/{id}) tiap kartu.
    private long passedCount;
    private long failedCount;
    private long blockedCount;

    private LocalDateTime createdAt;
    private String createdBy;
    private LocalDateTime updatedAt;
    private String updatedBy;
}
