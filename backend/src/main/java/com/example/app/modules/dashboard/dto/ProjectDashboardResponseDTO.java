// filepath: /backend/src/main/java/com/example/app/modules/dashboard/dto/ProjectDashboardResponseDTO.java
package com.example.app.modules.dashboard.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * Response GET /api/projects/{projectId}/dashboard -- semua angka dashboard
 * project dalam satu panggilan. Match 100% dgn ProjectDashboardResponse (FE).
 * <ul>
 *   <li>{@code totalTestCases}, {@code scenario}, {@code priority} hanya menghitung
 *       test case ACTIVE (arsip terpisah di {@code archivedTestCases}).
 *       {@code health} BERBEDA: dihitung sama dengan Report (hasil eksekusi di
 *       semua test run), lihat {@link HealthBreakdownDTO}.</li>
 *   <li>{@code latestRun} = null kalau project belum punya run.</li>
 *   <li>{@code activeRuns}: run yang belum FINISHED DAN belum 100% dieksekusi
 *       (maks 5, terbaru dulu). Run yang semuanya sudah dieksekusi tapi berisi
 *       FAILED tidak tampil di sini.</li>
 *   <li>{@code executionTimeline}: SELALU 30 elemen berurutan dari yang
 *       terlama sampai hari ini; hari tanpa eksekusi bernilai 0.</li>
 * </ul>
 */
@Getter
@Builder
public class ProjectDashboardResponseDTO {
    private long totalTestCases;
    private long archivedTestCases;
    private ScenarioBreakdownDTO scenario;
    private PriorityBreakdownDTO priority;
    private HealthBreakdownDTO health;
    private DashboardRunDTO latestRun;
    private List<DashboardRunDTO> activeRuns;
    private List<TimelinePointDTO> executionTimeline;
}
