// filepath: /backend/src/main/java/com/example/app/modules/testcase/dto/ImportPreviewRowDTO.java
package com.example.app.modules.testcase.dto;

import com.example.app.modules.testcase.TestCasePriority;
import com.example.app.modules.testcase.TestCaseScenarioType;
import com.example.app.modules.testcase.TestCaseType;
import lombok.Builder;
import lombok.Getter;

/** Satu baris valid pada pratinjau. Match dgn ImportPreviewRow (FE). */
@Getter
@Builder
public class ImportPreviewRowDTO {
    private int row;
    private String title;
    private TestCasePriority priority;
    private TestCaseType type;
    // null kalau kolom Scenario type kosong.
    private TestCaseScenarioType scenarioType;
    private int stepCount;
}
