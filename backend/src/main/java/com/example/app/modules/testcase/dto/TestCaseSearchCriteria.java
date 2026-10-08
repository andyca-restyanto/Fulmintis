// backend/src/main/java/com/example/app/modules/testcase/dto/TestCaseSearchCriteria.java
package com.example.app.modules.testcase.dto;

import com.example.app.modules.testcase.TestCasePriority;
import com.example.app.modules.testcase.TestCaseScenarioType;
import com.example.app.modules.testcase.TestCaseType;

import java.util.UUID;

/**
 * Kumpulan parameter pencarian/filter test case (requirement #6). SEMUA
 * field opsional (boleh null) -- lihat TestCaseSpecifications utk gimana
 * tiap field di-translate jadi filter query, otomatis di-skip kalau null.
 *
 * @param folderId     filter ke 1 folder tertentu (mis. saat user lagi
 *                     buka 1 folder di Test Repository)
 * @param keyword      cari di title (partial, case-insensitive) ATAU di id
 *                     (exact match, kalau keyword-nya valid UUID) --
 *                     requirement Test Run #3
 * @param priority     filter priority (HIGHEST/HIGH/MEDIUM/LOW)
 * @param type         filter type (MANUAL/AUTOMATION)
 * @param scenarioType filter scenario type (POSITIVE/NEGATIVE)
 */
public record TestCaseSearchCriteria(
        UUID folderId,
        String keyword,
        TestCasePriority priority,
        TestCaseType type,
        TestCaseScenarioType scenarioType
) {
}
