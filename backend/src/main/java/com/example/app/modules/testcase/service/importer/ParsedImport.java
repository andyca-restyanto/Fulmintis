// filepath: /backend/src/main/java/com/example/app/modules/testcase/service/importer/ParsedImport.java
package com.example.app.modules.testcase.service.importer;

import com.example.app.modules.testcase.TestCasePriority;
import com.example.app.modules.testcase.TestCaseScenarioType;
import com.example.app.modules.testcase.TestCaseType;
import com.example.app.modules.testcase.dto.ImportColumnMappingDTO;
import com.example.app.modules.testcase.dto.ImportIssueDTO;

import java.util.List;

/** Hasil membaca file Excel: baris valid + semua temuan (belum disimpan ke DB). */
public record ParsedImport(
        List<Row> validRows,
        int totalRows,
        List<ImportColumnMappingDTO> columnMapping,
        List<String> unmappedColumns,
        List<ImportIssueDTO> errors,
        List<ImportIssueDTO> warnings
) {

    /** Satu baris test case yang lolos validasi (nilai sudah dinormalisasi). */
    public record Row(
            int rowNumber,
            String title,
            TestCasePriority priority,
            TestCaseType type,
            TestCaseScenarioType scenarioType,
            String description,
            String objective,
            String precondition,
            String testStep,
            String expectedResult,
            int stepCount
    ) {
    }
}
