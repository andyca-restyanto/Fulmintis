// filepath: /backend/src/main/java/com/example/app/modules/testcase/service/importer/TestCaseEnumValues.java
package com.example.app.modules.testcase.service.importer;

import com.example.app.modules.testcase.TestCasePriority;
import com.example.app.modules.testcase.TestCaseScenarioType;
import com.example.app.modules.testcase.TestCaseType;

import java.util.Map;
import java.util.Optional;

/**
 * Nilai teks yang diterima utk priority / type / scenario, SATU sumber utk import Excel dan draft AI (tanpa membedakan huruf
 * besar/kecil, spasi, dan tanda baca; sinonim umum ikut diterima) -- supaya kedua jalur tidak bisa berbeda aturan.
 */
public final class TestCaseEnumValues {

    public static final Map<String, TestCasePriority> PRIORITY = Map.of(
            "highest", TestCasePriority.HIGHEST, "high", TestCasePriority.HIGH,
            "medium", TestCasePriority.MEDIUM, "low", TestCasePriority.LOW);

    public static final Map<String, TestCaseType> TYPE = Map.of(
            "manual", TestCaseType.MANUAL, "automation", TestCaseType.AUTOMATION,
            "automated", TestCaseType.AUTOMATION, "otomatis", TestCaseType.AUTOMATION);

    public static final Map<String, TestCaseScenarioType> SCENARIO = Map.of(
            "positive", TestCaseScenarioType.POSITIVE, "negative", TestCaseScenarioType.NEGATIVE,
            "positif", TestCaseScenarioType.POSITIVE, "negatif", TestCaseScenarioType.NEGATIVE);

    private TestCaseEnumValues() {
    }

    public static Optional<TestCasePriority> priority(String raw) {
        return Optional.ofNullable(PRIORITY.get(ImportColumn.normalize(raw)));
    }

    public static Optional<TestCaseType> type(String raw) {
        return Optional.ofNullable(TYPE.get(ImportColumn.normalize(raw)));
    }

    public static Optional<TestCaseScenarioType> scenario(String raw) {
        return Optional.ofNullable(SCENARIO.get(ImportColumn.normalize(raw)));
    }
}
