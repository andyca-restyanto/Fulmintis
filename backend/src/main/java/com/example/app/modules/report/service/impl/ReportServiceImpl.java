// backend/src/main/java/com/example/app/modules/report/service/impl/ReportServiceImpl.java
package com.example.app.modules.report.service.impl;

import com.example.app.modules.project.service.ProjectAccessService;
import com.example.app.modules.report.dto.ReportOverviewResponseDTO;
import com.example.app.modules.report.service.ReportService;
import com.example.app.modules.testrun.TestRunStatus;
import com.example.app.modules.testrun.dto.TestRunDetailResponseDTO;
import com.example.app.modules.testrun.repository.TestResultRepository;
import com.example.app.modules.testrun.repository.TestRunRepository;
import com.example.app.modules.testrun.service.ResultStatusTotals;
import com.example.app.modules.testrun.service.TestRunService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ReportServiceImpl implements ReportService {

    private final ProjectAccessService projectAccessService;
    private final TestRunRepository testRunRepository;
    private final TestResultRepository testResultRepository;
    private final TestRunService testRunService;

    @Override
    public ReportOverviewResponseDTO getOverview(String userEmail, UUID projectId) {
        // OWNER maupun COLLABORATOR boleh lihat report, sama seperti listing
        // Test Run -- lihat javadoc TestRunController. 404 (bukan 403) kalau
        // bukan member, supaya keberadaan project tidak bocor ke non-member.
        projectAccessService.requireMember(userEmail, projectId);

        long totalTestRuns = testRunRepository.countByProjectId(projectId);
        long totalRunningTestRuns = testRunRepository.countByProjectIdAndStatus(projectId, TestRunStatus.RUNNING);

        // Hitungan per status dihitung oleh ResultStatusTotals -- komponen yang
        // SAMA dipakai Current Project Health di dashboard, supaya angka kedua
        // halaman selalu identik (Pending = NEW + PENDING; status tanpa baris = 0).
        ResultStatusTotals totals = ResultStatusTotals.from(testResultRepository.countGroupedByStatus(projectId));

        long totalPassed = totals.passed();
        long totalFailed = totals.failed();
        long totalBlocked = totals.blocked();
        long totalPending = totals.pending();
        long totalExecutions = totals.total();

        // Penyebut = TOTAL test case (totalExecutions = semua status, termasuk
        // NEW/PENDING/BLOCKED) -- lihat javadoc ReportOverviewResponseDTO.
        //   Pass Rate      = passed / total * 100
        //   Execution Rate = (passed + failed) / total * 100
        // 0 kalau belum ada test case sama sekali (hindari bagi nol).
        int passRatePercentage = percentage(totalPassed, totalExecutions);
        int executionRatePercentage = percentage(totalPassed + totalFailed, totalExecutions);

        return ReportOverviewResponseDTO.builder()
                .totalTestRuns(totalTestRuns)
                .totalRunningTestRuns(totalRunningTestRuns)
                .totalExecutions(totalExecutions)
                .totalPassed(totalPassed)
                .totalFailed(totalFailed)
                .totalBlocked(totalBlocked)
                .totalPending(totalPending)
                .passRatePercentage(passRatePercentage)
                .executionRatePercentage(executionRatePercentage)
                .build();
    }

    private static int percentage(long part, long total) {
        return total == 0 ? 0 : Math.round((part * 100f) / total);
    }

    @Override
    public List<TestRunDetailResponseDTO> getRunDetails(String userEmail, UUID projectId) {
        // Membership project sudah divalidasi di dalam listTestRunDetails()
        // itu sendiri (404 kalau bukan member) -- tidak perlu dicek dobel di sini.
        return testRunService.listTestRunDetails(userEmail, projectId);
    }
}
