// filepath: /backend/src/test/java/com/example/app/modules/dashboard/service/HealthReportParityTest.java
package com.example.app.modules.dashboard.service;

import com.example.app.modules.dashboard.dto.ProjectDashboardResponseDTO;
import com.example.app.modules.dashboard.service.impl.ProjectDashboardServiceImpl;
import com.example.app.modules.project.entity.ProjectCollaboration;
import com.example.app.modules.project.service.ProjectAccessService;
import com.example.app.modules.report.dto.ReportOverviewResponseDTO;
import com.example.app.modules.report.service.impl.ReportServiceImpl;
import com.example.app.modules.testcase.repository.TestCaseRepository;
import com.example.app.modules.testrun.TestResultStatus;
import com.example.app.modules.testrun.repository.TestResultRepository;
import com.example.app.modules.testrun.repository.TestRunRepository;
import com.example.app.modules.testrun.service.TestRunService;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Menjaga janji "Current Project Health sama dengan Report": untuk data yang
 * SAMA, keempat angka health di dashboard harus identik dengan totalPassed /
 * totalFailed / totalBlocked / totalPending di Report overview.
 */
class HealthReportParityTest {

    private static final UUID PROJECT_ID = UUID.randomUUID();

    @SuppressWarnings("unchecked")
    private static <T> T fake(Class<T> type, InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler);
    }

    private static TestResultRepository.StatusCount row(TestResultStatus status, long total) {
        Map<String, Object> getters = Map.of("getStatus", status, "getTotal", total);
        return fake(TestResultRepository.StatusCount.class, (proxy, method, args) -> getters.get(method.getName()));
    }

    private static ProjectAccessService allowAll() {
        return new ProjectAccessService(null, null) {
            @Override
            public ProjectCollaboration requireMember(String userEmail, UUID projectId) {
                return null;
            }
        };
    }

    private void assertParity(List<TestResultRepository.StatusCount> statusRows) {
        TestResultRepository results = fake(TestResultRepository.class, (proxy, method, args) -> switch (method.getName()) {
            case "countGroupedByStatus" -> statusRows;
            case "countExecutionsPerDay" -> List.<Object[]>of();
            case "countGroupedByRunAndStatusForRuns" -> List.of();
            default -> throw new UnsupportedOperationException(method.getName());
        });
        TestRunRepository runs = fake(TestRunRepository.class, (proxy, method, args) -> switch (method.getName()) {
            case "countByProjectId", "countByProjectIdAndStatus" -> 0L;
            case "findFirstByProjectIdOrderByCreatedAtDesc" -> Optional.empty();
            case "findByProjectIdAndStatusNotOrderByCreatedAtDesc" -> List.of();
            default -> throw new UnsupportedOperationException(method.getName());
        });
        TestCaseRepository testCases = fake(TestCaseRepository.class, (proxy, method, args) -> switch (method.getName()) {
            case "countByProjectIdAndStatus" -> 0L;
            case "countByScenario", "countByPriority" -> List.of();
            default -> throw new UnsupportedOperationException(method.getName());
        });
        TestRunService testRunService = fake(TestRunService.class, (proxy, method, args) -> {
            throw new UnsupportedOperationException(method.getName());
        });

        ReportOverviewResponseDTO report =
                new ReportServiceImpl(allowAll(), runs, results, testRunService).getOverview("a@b.com", PROJECT_ID);
        ProjectDashboardResponseDTO dashboard =
                new ProjectDashboardServiceImpl(allowAll(), testCases, runs, results).getProjectDashboard("a@b.com", PROJECT_ID);

        assertEquals(report.getTotalPassed(), dashboard.getHealth().getPassed());
        assertEquals(report.getTotalFailed(), dashboard.getHealth().getFailed());
        assertEquals(report.getTotalBlocked(), dashboard.getHealth().getBlocked());
        assertEquals(report.getTotalPending(), dashboard.getHealth().getPending());
        assertEquals(report.getTotalExecutions(), dashboard.getHealth().getPassed() + dashboard.getHealth().getFailed()
                + dashboard.getHealth().getBlocked() + dashboard.getHealth().getPending());
    }

    @Test
    void healthMatchesReportOverviewForMixedStatuses() {
        assertParity(List.of(
                row(TestResultStatus.PASSED, 20), row(TestResultStatus.FAILED, 5), row(TestResultStatus.BLOCKED, 2),
                row(TestResultStatus.NEW, 6), row(TestResultStatus.PENDING, 3)));
    }

    @Test
    void healthMatchesReportOverviewWhenOnlySomeStatusesExist() {
        assertParity(List.of(row(TestResultStatus.NEW, 4)));
    }

    @Test
    void healthMatchesReportOverviewForProjectWithoutResults() {
        assertParity(List.of());
    }
}
