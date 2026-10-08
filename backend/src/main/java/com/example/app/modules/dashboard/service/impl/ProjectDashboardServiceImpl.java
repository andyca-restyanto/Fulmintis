// filepath: /backend/src/main/java/com/example/app/modules/dashboard/service/impl/ProjectDashboardServiceImpl.java
package com.example.app.modules.dashboard.service.impl;

import com.example.app.modules.dashboard.dto.DashboardRunDTO;
import com.example.app.modules.dashboard.dto.PriorityBreakdownDTO;
import com.example.app.modules.dashboard.dto.ProjectDashboardResponseDTO;
import com.example.app.modules.dashboard.dto.ScenarioBreakdownDTO;
import com.example.app.modules.dashboard.service.DashboardCalculator;
import com.example.app.modules.dashboard.service.DashboardCalculator.TimelineEntry;
import com.example.app.modules.dashboard.service.ProjectDashboardService;
import com.example.app.modules.project.service.ProjectAccessService;
import com.example.app.modules.testcase.TestCaseScenarioType;
import com.example.app.modules.testcase.TestCaseStatus;
import com.example.app.modules.testcase.repository.TestCaseRepository;
import com.example.app.modules.testrun.TestResultStatus;
import com.example.app.modules.testrun.TestRunStatus;
import com.example.app.modules.testrun.entity.TestRun;
import com.example.app.modules.testrun.repository.TestResultRepository;
import com.example.app.modules.testrun.repository.TestRunRepository;
import com.example.app.modules.testrun.service.ResultStatusTotals;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProjectDashboardServiceImpl implements ProjectDashboardService {

    private static final int TIMELINE_DAYS = 30;
    private static final int MAX_ACTIVE_RUNS = 5;

    private final ProjectAccessService projectAccessService;
    private final TestCaseRepository testCaseRepository;
    private final TestRunRepository testRunRepository;
    private final TestResultRepository testResultRepository;

    @Override
    @Transactional(readOnly = true)
    public ProjectDashboardResponseDTO getProjectDashboard(String userEmail, UUID projectId) {
        projectAccessService.requireMember(userEmail, projectId);

        // ---- Test case (hanya ACTIVE; arsip dihitung terpisah) ----
        long totalTestCases = testCaseRepository.countByProjectIdAndStatus(projectId, TestCaseStatus.ACTIVE);
        long archivedTestCases = testCaseRepository.countByProjectIdAndStatus(projectId, TestCaseStatus.ARCHIVED);

        long positive = 0;
        long negative = 0;
        for (var row : testCaseRepository.countByScenario(projectId, TestCaseStatus.ACTIVE)) {
            if (row.getCategory() == TestCaseScenarioType.POSITIVE) {
                positive = row.getTotal();
            } else if (row.getCategory() == TestCaseScenarioType.NEGATIVE) {
                negative = row.getTotal();
            }
            // category null (data lama tanpa scenario_type) -> tidak masuk kedua sisi
        }

        long highest = 0;
        long high = 0;
        long medium = 0;
        long low = 0;
        for (var row : testCaseRepository.countByPriority(projectId, TestCaseStatus.ACTIVE)) {
            if (row.getCategory() == null) {
                continue;
            }
            switch (row.getCategory()) {
                case HIGHEST -> highest = row.getTotal();
                case HIGH -> high = row.getTotal();
                case MEDIUM -> medium = row.getTotal();
                case LOW -> low = row.getTotal();
            }
        }

        // ---- Health: dihitung SAMA dengan Report (ResultStatusTotals) ----
        // Semua hasil eksekusi di semua test run; Pending = NEW + PENDING.
        ResultStatusTotals statusTotals = ResultStatusTotals.from(testResultRepository.countGroupedByStatus(projectId));

        // ---- Run: terbaru + yang masih terbuka ----
        Optional<TestRun> latestRun = testRunRepository.findFirstByProjectIdOrderByCreatedAtDesc(projectId);
        List<TestRun> openRuns =
                testRunRepository.findByProjectIdAndStatusNotOrderByCreatedAtDesc(projectId, TestRunStatus.FINISHED);

        Set<UUID> runIds = new LinkedHashSet<>();
        latestRun.ifPresent(run -> runIds.add(run.getId()));
        openRuns.forEach(run -> runIds.add(run.getId()));
        Map<UUID, Map<TestResultStatus, Long>> countsByRun = loadCountsByRun(runIds);

        DashboardRunDTO latestRunDto = latestRun.map(run -> toRunDTO(run, countsByRun)).orElse(null);

        List<DashboardRunDTO> activeRuns = new ArrayList<>();
        for (TestRun run : openRuns) {
            DashboardRunDTO dto = toRunDTO(run, countsByRun);
            long executed = dto.getPassedCount() + dto.getFailedCount() + dto.getBlockedCount();
            if (DashboardCalculator.isActive(run.getStatus(), executed, dto.getTestCaseCount())) {
                activeRuns.add(dto);
                if (activeRuns.size() == MAX_ACTIVE_RUNS) {
                    break;
                }
            }
        }

        // ---- Timeline 30 hari (hari kosong diisi 0) ----
        LocalDate today = LocalDate.now();
        List<TimelineEntry> timelineEntries = new ArrayList<>();
        for (Object[] row : testResultRepository.countExecutionsPerDay(
                projectId, today.minusDays(TIMELINE_DAYS - 1L).atStartOfDay())) {
            timelineEntries.add(new TimelineEntry(
                    LocalDate.parse(row[0].toString()),
                    TestResultStatus.valueOf(row[1].toString()),
                    ((Number) row[2]).longValue()));
        }

        return ProjectDashboardResponseDTO.builder()
                .totalTestCases(totalTestCases)
                .archivedTestCases(archivedTestCases)
                .scenario(ScenarioBreakdownDTO.builder().positive(positive).negative(negative).build())
                .priority(PriorityBreakdownDTO.builder().highest(highest).high(high).medium(medium).low(low).build())
                .health(DashboardCalculator.buildHealth(statusTotals))
                .latestRun(latestRunDto)
                .activeRuns(activeRuns)
                .executionTimeline(DashboardCalculator.buildTimeline(today, TIMELINE_DAYS, timelineEntries))
                .build();
    }

    private Map<UUID, Map<TestResultStatus, Long>> loadCountsByRun(Set<UUID> runIds) {
        Map<UUID, Map<TestResultStatus, Long>> countsByRun = new HashMap<>();
        if (runIds.isEmpty()) {
            return countsByRun; // "in ()" dengan koleksi kosong tidak valid di sebagian dialect
        }
        for (var row : testResultRepository.countGroupedByRunAndStatusForRuns(runIds)) {
            countsByRun.computeIfAbsent(row.getRunId(), k -> new EnumMap<>(TestResultStatus.class))
                    .put(row.getStatus(), row.getTotal());
        }
        return countsByRun;
    }

    private DashboardRunDTO toRunDTO(TestRun run, Map<UUID, Map<TestResultStatus, Long>> countsByRun) {
        Map<TestResultStatus, Long> counts = countsByRun.getOrDefault(run.getId(), Map.of());
        long total = counts.values().stream().mapToLong(Long::longValue).sum();
        long passed = counts.getOrDefault(TestResultStatus.PASSED, 0L);
        long failed = counts.getOrDefault(TestResultStatus.FAILED, 0L);
        long blocked = counts.getOrDefault(TestResultStatus.BLOCKED, 0L);

        return DashboardRunDTO.builder()
                .id(run.getId())
                .title(run.getTitle())
                .status(run.getStatus())
                .testCaseCount(total)
                .passedCount(passed)
                .failedCount(failed)
                .blockedCount(blocked)
                .progressPercentage(DashboardCalculator.progressPercentage(passed + failed + blocked, total))
                .createdAt(run.getCreatedAt())
                .build();
    }
}
