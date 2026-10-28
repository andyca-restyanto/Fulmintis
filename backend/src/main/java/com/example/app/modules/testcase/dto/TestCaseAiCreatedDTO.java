// filepath: /backend/src/main/java/com/example/app/modules/testcase/dto/TestCaseAiCreatedDTO.java
package com.example.app.modules.testcase.dto;

import com.example.app.modules.testcase.TestCaseAiGenerationStatus;
import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

/** Respons 202 POST /test-cases/ai-generation. Match dgn TestCaseAiGenerationCreated (FE). */
@Getter
@Builder
public class TestCaseAiCreatedDTO {
    private UUID id;
    private TestCaseAiGenerationStatus status;
}
