// filepath: /backend/src/test/java/com/example/app/modules/dashboard/service/DashboardCalculatorTest.java
package com.example.app.modules.dashboard.service;

import com.example.app.modules.dashboard.dto.HealthBreakdownDTO;
import com.example.app.modules.dashboard.dto.TimelinePointDTO;
import com.example.app.modules.dashboard.service.DashboardCalculator.TimelineEntry;
import com.example.app.modules.testrun.TestResultStatus;
import com.example.app.modules.testrun.TestRunStatus;
import com.example.app.modules.testrun.service.ResultStatusTotals;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DashboardCalculatorTest {

    @Test
    void progressPercentageRoundsAndHandlesEmptyRun() {
        assertEquals(0, DashboardCalculator.progressPercentage(0, 0));
        assertEquals(33, DashboardCalculator.progressPercentage(1, 3));
        assertEquals(67, DashboardCalculator.progressPercentage(2, 3));
        assertEquals(100, DashboardCalculator.progressPercentage(5, 5));
    }

    @Test
    void finishedRunIsNeverActive() {
        assertFalse(DashboardCalculator.isActive(TestRunStatus.FINISHED, 3, 10));
    }

    @Test
    void runWithRemainingTestsIsActiveEvenWhenPercentageRoundsTo100() {
        assertTrue(DashboardCalculator.isActive(TestRunStatus.RUNNING, 3, 10));
        // 199/200 = 99,5% terbulat 100, tapi masih ada 1 test belum dieksekusi
        assertEquals(100, DashboardCalculator.progressPercentage(199, 200));
        assertTrue(DashboardCalculator.isActive(TestRunStatus.RUNNING, 199, 200));
    }

    @Test
    void fullyExecutedRunWithFailuresIsNotActive() {
        // semua sudah dieksekusi tapi berisi FAILED -> status tetap RUNNING di DB, tapi bukan "aktif"
        assertFalse(DashboardCalculator.isActive(TestRunStatus.RUNNING, 5, 5));
    }

    @Test
    void emptyOpenRunIsActive() {
        assertTrue(DashboardCalculator.isActive(TestRunStatus.PENDING, 0, 0));
    }

    @Test
    void healthCopiesTheSharedReportTotals() {
        // pending = NEW + PENDING sudah digabung oleh ResultStatusTotals (komponen yang sama dgn Report)
        HealthBreakdownDTO health = DashboardCalculator.buildHealth(new ResultStatusTotals(20, 5, 2, 9));

        assertEquals(20L, health.getPassed());
        assertEquals(5L, health.getFailed());
        assertEquals(2L, health.getBlocked());
        assertEquals(9L, health.getPending());
    }

    @Test
    void healthOfProjectWithoutResultsIsAllZero() {
        HealthBreakdownDTO health = DashboardCalculator.buildHealth(new ResultStatusTotals(0, 0, 0, 0));

        assertEquals(0L, health.getPassed() + health.getFailed() + health.getBlocked() + health.getPending());
    }

    @Test
    void timelineHasExactlyThirtyOrderedDaysWithZeroFill() {
        LocalDate today = LocalDate.of(2026, 10, 2);
        List<TimelinePointDTO> timeline = DashboardCalculator.buildTimeline(today, 30, List.of(
                new TimelineEntry(today, TestResultStatus.PASSED, 4),
                new TimelineEntry(today, TestResultStatus.FAILED, 1),
                new TimelineEntry(today.minusDays(2), TestResultStatus.BLOCKED, 3)));

        assertEquals(30, timeline.size());
        assertEquals(today.minusDays(29), timeline.get(0).getDate());
        assertEquals(today, timeline.get(29).getDate());

        TimelinePointDTO last = timeline.get(29);
        assertEquals(4L, last.getPassed());
        assertEquals(1L, last.getFailed());
        assertEquals(0L, last.getBlocked());

        assertEquals(3L, timeline.get(27).getBlocked());
        assertEquals(0L, timeline.get(28).getPassed() + timeline.get(28).getFailed() + timeline.get(28).getBlocked());
    }

    @Test
    void timelineIgnoresOutOfRangeAndNonExecutionStatuses() {
        LocalDate today = LocalDate.of(2026, 10, 2);
        List<TimelinePointDTO> timeline = DashboardCalculator.buildTimeline(today, 30, List.of(
                new TimelineEntry(today.minusDays(30), TestResultStatus.PASSED, 9), // 31 hari lalu
                new TimelineEntry(today, TestResultStatus.PENDING, 9),
                new TimelineEntry(today, TestResultStatus.NEW, 9)));

        long total = timeline.stream().mapToLong(p -> p.getPassed() + p.getFailed() + p.getBlocked()).sum();
        assertEquals(0L, total);
    }
}
