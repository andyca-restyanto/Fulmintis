// filepath: /backend/src/main/java/com/example/app/modules/testcase/dto/TestCaseAiCommitRequestDTO.java
package com.example.app.modules.testcase.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.UUID;

/** POST /test-cases/ai-generation/{id}/commit. Match dgn CommitTestCasesRequest (FE). */
@Getter
@Setter
public class TestCaseAiCommitRequestDTO {
    private UUID folderId;
    private List<TestCaseAiCommitItemDTO> testCases;
}
