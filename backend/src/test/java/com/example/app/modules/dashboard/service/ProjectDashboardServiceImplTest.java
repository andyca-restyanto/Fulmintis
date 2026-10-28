// filepath: /backend/src/test/java/com/example/app/modules/dashboard/service/ProjectDashboardServiceImplTest.java
package com.example.app.modules.dashboard.service;

import com.example.app.modules.dashboard.dto.DashboardRunDTO;
import com.example.app.modules.dashboard.dto.ProjectDashboardResponseDTO;
import com.example.app.modules.dashboard.service.impl.ProjectDashboardServiceImpl;
import com.example.app.modules.project.entity.ProjectCollaboration;
import com.example.app.modules.project.service.ProjectAccessService;
import com.example.app.modules.testcase.TestCasePriority;
import com.example.app.modules.testcase.TestCaseScenarioType;
import com.example.app.modules.testcase.TestCaseStatus;
import com.example.app.modules.testcase.repository.TestCaseRepository;
import com.example.app.modules.testrun.TestResultStatus;
import com.example.app.modules.testrun.TestRunStatus;
import com.example.app.modules.testrun.entity.TestRun;
import com.example.app.modules.testrun.repository.TestResultRepository;
import com.example.app.modules.testrun.repository.TestRunRepository;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Menguji perakitan service (pemetaan hasil query -> DTO, definisi run aktif,
 * batas 5 run, notRun, timeline) dengan repository palsu berbasis java.lang.reflect.Proxy
 * -- tanpa DB dan tanpa Mockito. Query SQL/JPQL-nya sendiri diuji terhadap
 * PostgreSQL sungguhan, bukan di sini.
 */
class ProjectDashboardServiceImplTest {

    private static final UUID PROJECT_ID = UUID.randomUUID();

    @SuppressWarnings("unchecked")
    private static <T> T fake(Class<T> type, InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler);
    }

    private static <T> T value(Class<T> type, java.util.Map<String, Object> getters) {
        return fake(type, (proxy, method, args) -> getters.get(method.getName()));
    }

    private static TestRun run(String title, TestRunStatus status, int minutesAgo) {
        TestRun run = new TestRun();
        run.setId(UUID.randomUUID());
        run.setTitle(title);
        run.setStatus(status);
        run.setCreatedAt(LocalDateTime.now().minusMinutes(minutesAgo));
        return run;
    }

    private static TestResultRepository.StatusCount status(TestResultStatus status, long total) {
        return value(TestResultRepository.StatusCount.class, java.util.Map.of("getStatus", status, "getTotal", total));
    }

    private static TestResultRepository.RunStatusCount count(UUID runId, TestResultStatus status, long total) {
        return value(TestResultRepository.RunStatusCount.class,
                java.util.Map.of("getRunId", runId, "getStatus", status, "getTotal", total));
    }

    private ProjectDashboardService serviceWith(
            List<TestRun> allRunsNewestFirst,
            List<TestResultRepository.RunStatusCount> runCounts,
            List<TestResultRepository.StatusCount> statusRows,
            List<Object[]> timelineRows
    ) {
        ProjectAccessService access = new ProjectAccessService(null, null) {
            @Override
            public ProjectCollaboration requireMember(String userEmail, UUID projectId) {
                return null; // akses sudah diuji terpisah; di sini dianggap member
            }
        };

        TestCaseRepository testCases = fake(TestCaseRepository.class, (proxy, method, args) -> switch (method.getName()) {
            case "countByProjectIdAndStatus" -> args[1] == TestCaseStatus.ACTIVE ? 10L : 2L;
            case "countByScenario" -> List.of(
                    value(TestCaseRepository.ScenarioCount.class,
                            java.util.Map.of("getCategory", TestCaseScenarioType.POSITIVE, "getTotal", 6L)),
                    value(TestCaseRepository.ScenarioCount.class,
                            java.util.Map.of("getCategory", TestCaseScenarioType.NEGATIVE, "getTotal", 3L)),
                    // data lama tanpa scenario_type: category null harus diabaikan
                    value(TestCaseRepository.ScenarioCount.class, new java.util.HashMap<String, Object>() {{
                        put("getCategory", null);
                        put("getTotal", 1L);
                    }}));
            case "countByPriority" -> List.of(
                    value(TestCaseRepository.PriorityCount.class,
                            java.util.Map.of("getCategory", TestCasePriority.HIGHEST, "getTotal", 1L)),
                    value(TestCaseRepository.PriorityCount.class,
                            java.util.Map.of("getCategory", TestCasePriority.MEDIUM, "getTotal", 7L)));
            default -> throw new UnsupportedOperationException(method.getName());
        });

        TestRunRepository runs = fake(TestRunRepository.class, (proxy, method, args) -> switch (method.getName()) {
            case "findFirstByProjectIdOrderByCreatedAtDesc" ->
                    allRunsNewestFirst.isEmpty() ? Optional.empty() : Optional.of(allRunsNewestFirst.get(0));
            case "findByProjectIdAndStatusNotOrderByCreatedAtDesc" ->
                    allRunsNewestFirst.stream().filter(r -> r.getStatus() != TestRunStatus.FINISHED).toList();
            default -> throw new UnsupportedOperationException(method.getName());
        });

        TestResultRepository results = fake(TestResultRepository.class, (proxy, method, args) -> switch (method.getName()) {
            case "countGroupedByStatus" -> statusRows;
            case "countGroupedByRunAndStatusForRuns" -> {
                Collection<?> ids = (Collection<?>) args[0];
                yield runCounts.stream().filter(c -> ids.contains(c.getRunId())).toList();
            }
            case "countExecutionsPerDay" -> timelineRows;
            default -> throw new UnsupportedOperationException(method.getName());
        });

        return new ProjectDashboardServiceImpl(access, testCases, runs, results);
    }

    @Test
    void assemblesAllWidgetsFromRepositoryResults() {
        TestRun latest = run("Latest", TestRunStatus.RUNNING, 1);
        TestRun doneWithFailures = run("Done but failed", TestRunStatus.RUNNING, 2);
        TestRun empty = run("Empty", TestRunStatus.PENDING, 3);
        TestRun finished = run("Finished", TestRunStatus.FINISHED, 4);

        LocalDate today = LocalDate.now();
        ProjectDashboardService service = serviceWith(
                List.of(latest, doneWithFailures, empty, finished),
                List.of(
                        count(latest.getId(), TestResultStatus.PASSED, 3),
                        count(latest.getId(), TestResultStatus.FAILED, 1),
                        count(latest.getId(), TestResultStatus.NEW, 6),
                        count(doneWithFailures.getId(), TestResultStatus.PASSED, 2),
                        count(doneWithFailures.getId(), TestResultStatus.FAILED, 3),
                        count(finished.getId(), TestResultStatus.PASSED, 5)),
                // semua hasil eksekusi di semua run: Pending digabung dari NEW + PENDING
                List.of(status(TestResultStatus.PASSED, 4), status(TestResultStatus.FAILED, 2),
                        status(TestResultStatus.NEW, 5), status(TestResultStatus.PENDING, 4)),
                List.<Object[]>of(
                        new Object[]{today.toString(), "PASSED", 4L},
                        new Object[]{today.minusDays(2).toString(), "BLOCKED", 1L}));

        ProjectDashboardResponseDTO dto = service.getProjectDashboard("a@b.com", PROJECT_ID);

        assertEquals(10L, dto.getTotalTestCases());
        assertEquals(2L, dto.getArchivedTestCases());
        assertEquals(6L, dto.getScenario().getPositive());
        assertEquals(3L, dto.getScenario().getNegative());
        assertEquals(1L, dto.getPriority().getHighest());
        assertEquals(7L, dto.getPriority().getMedium());
        assertEquals(0L, dto.getPriority().getLow());

        // health = hitungan Report: passed 4, failed 2, blocked 0, pending = NEW 5 + PENDING 4 = 9
        assertEquals(4L, dto.getHealth().getPassed());
        assertEquals(2L, dto.getHealth().getFailed());
        assertEquals(0L, dto.getHealth().getBlocked());
        assertEquals(9L, dto.getHealth().getPending());

        // latestRun = run terbaru; progres (3+1)/10 = 40%
        assertEquals("Latest", dto.getLatestRun().getTitle());
        assertEquals(10L, dto.getLatestRun().getTestCaseCount());
        assertEquals(40, dto.getLatestRun().getProgressPercentage());

        // aktif: "Latest" dan "Empty". "Done but failed" (5/5 dieksekusi) dan FINISHED tidak ikut.
        List<String> activeTitles = dto.getActiveRuns().stream().map(DashboardRunDTO::getTitle).toList();
        assertEquals(List.of("Latest", "Empty"), activeTitles);

        assertEquals(30, dto.getExecutionTimeline().size());
        assertEquals(4L, dto.getExecutionTimeline().get(29).getPassed());
        assertEquals(1L, dto.getExecutionTimeline().get(27).getBlocked());
    }

    @Test
    void projectWithoutRunsHasNullLatestRunAndEmptyActiveRuns() {
        ProjectDashboardService service = serviceWith(List.of(), List.of(), List.of(), List.of());

        ProjectDashboardResponseDTO dto = service.getProjectDashboard("a@b.com", PROJECT_ID);

        assertNull(dto.getLatestRun());
        assertEquals(0, dto.getActiveRuns().size());
        assertEquals(30, dto.getExecutionTimeline().size());
        // belum ada test run -> tidak ada hasil eksekusi -> health kosong (test case
        // yang belum masuk run TIDAK dihitung, walau project punya 10 test case aktif)
        assertEquals(0L, dto.getHealth().getPassed() + dto.getHealth().getFailed()
                + dto.getHealth().getBlocked() + dto.getHealth().getPending());
    }

    @Test
    void activeRunsAreCappedAtFiveNewestFirst() {
        List<TestRun> runs = new ArrayList<>();
        for (int i = 1; i <= 8; i++) {
            runs.add(run("Run " + i, TestRunStatus.RUNNING, i));
        }
        ProjectDashboardService service = serviceWith(runs, List.of(), List.of(), List.of());

        List<String> titles = service.getProjectDashboard("a@b.com", PROJECT_ID).getActiveRuns().stream()
                .map(DashboardRunDTO::getTitle).toList();

        assertEquals(List.of("Run 1", "Run 2", "Run 3", "Run 4", "Run 5"), titles);
    }
}
