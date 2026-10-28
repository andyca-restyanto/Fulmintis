// filepath: /backend/src/main/java/com/example/app/modules/testcase/dto/TestCaseAiCommitItemDTO.java
package com.example.app.modules.testcase.dto;

import com.example.app.modules.testcase.TestCasePriority;
import com.example.app.modules.testcase.TestCaseScenarioType;
import com.example.app.modules.testcase.TestCaseType;
import lombok.Getter;
import lombok.Setter;

/**
 * Satu draft hasil review yang akan disimpan. Bentuknya sama dgn CreateTestCaseRequest tanpa folderId; testStep/expectedResult sudah
 * berupa teks bernomor ("1. ...\n2. ..."). Divalidasi MANUAL di service (agar error per item membawa indeks), bukan lewat anotasi.
 * Match dgn CommitTestCaseItem (FE).
 */
@Getter
@Setter
public class TestCaseAiCommitItemDTO {
    private String title;
    private TestCasePriority priority;
    private TestCaseType type;
    private TestCaseScenarioType scenarioType;
    private String description;
    private String objective;
    private String precondition;
    private String testStep;
    private String expectedResult;
}
