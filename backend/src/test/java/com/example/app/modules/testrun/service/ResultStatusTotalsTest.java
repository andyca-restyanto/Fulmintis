// filepath: /backend/src/test/java/com/example/app/modules/testrun/service/ResultStatusTotalsTest.java
package com.example.app.modules.testrun.service;

import com.example.app.modules.testrun.TestResultStatus;
import com.example.app.modules.testrun.repository.TestResultRepository;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ResultStatusTotalsTest {

    private static TestResultRepository.StatusCount row(TestResultStatus status, Long total) {
        Map<String, Object> getters = new java.util.HashMap<>();
        getters.put("getStatus", status);
        getters.put("getTotal", total);
        return (TestResultRepository.StatusCount) Proxy.newProxyInstance(
                TestResultRepository.StatusCount.class.getClassLoader(),
                new Class<?>[]{TestResultRepository.StatusCount.class},
                (proxy, method, args) -> getters.get(method.getName()));
    }

    @Test
    void pendingIsNewPlusPending() {
        ResultStatusTotals totals = ResultStatusTotals.from(List.of(
                row(TestResultStatus.PASSED, 20L),
                row(TestResultStatus.FAILED, 5L),
                row(TestResultStatus.BLOCKED, 2L),
                row(TestResultStatus.NEW, 6L),
                row(TestResultStatus.PENDING, 3L)));

        assertEquals(20L, totals.passed());
        assertEquals(5L, totals.failed());
        assertEquals(2L, totals.blocked());
        assertEquals(9L, totals.pending());
        assertEquals(36L, totals.total());
    }

    @Test
    void missingStatusesCountAsZeroAndEmptyInputIsAllZero() {
        ResultStatusTotals onlyPassed = ResultStatusTotals.from(List.of(row(TestResultStatus.PASSED, 3L)));
        assertEquals(0L, onlyPassed.failed());
        assertEquals(0L, onlyPassed.blocked());
        assertEquals(0L, onlyPassed.pending());
        assertEquals(3L, onlyPassed.total());

        assertEquals(0L, ResultStatusTotals.from(List.of()).total());
    }

    @Test
    void nullTotalOrStatusIsTreatedAsZeroNotAnError() {
        ResultStatusTotals totals = ResultStatusTotals.from(List.of(
                row(TestResultStatus.PASSED, null),
                row(null, 7L),
                row(TestResultStatus.FAILED, 2L)));

        assertEquals(0L, totals.passed());
        assertEquals(2L, totals.failed());
        assertEquals(2L, totals.total());
    }
}
