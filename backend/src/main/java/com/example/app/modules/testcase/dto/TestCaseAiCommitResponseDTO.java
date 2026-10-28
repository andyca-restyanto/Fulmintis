// filepath: /backend/src/main/java/com/example/app/modules/testcase/dto/TestCaseAiCommitResponseDTO.java
package com.example.app.modules.testcase.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

/** Respons 201 commit. Match dgn CommitTestCasesResult (FE). */
@Getter
@Builder
public class TestCaseAiCommitResponseDTO {
    private int savedCount;
    private UUID folderId;
    private String folderName;
}
