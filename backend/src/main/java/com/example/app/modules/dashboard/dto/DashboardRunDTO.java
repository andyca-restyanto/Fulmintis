// filepath: /backend/src/main/java/com/example/app/modules/dashboard/dto/DashboardRunDTO.java
package com.example.app.modules.dashboard.dto;

import com.example.app.modules.testrun.TestRunStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

/** Ringkasan satu test run utk dashboard. Match dgn DashboardRun (FE). */
@Getter
@Builder
public class DashboardRunDTO {
    private UUID id;
    private String title;
    private TestRunStatus status;
    private long testCaseCount;
    private long passedCount;
    private long failedCount;
    private long blockedCount;
    // (passed + failed + blocked) / testCaseCount * 100, dibulatkan; 0 kalau run kosong.
    private int progressPercentage;
    private LocalDateTime createdAt;
}
