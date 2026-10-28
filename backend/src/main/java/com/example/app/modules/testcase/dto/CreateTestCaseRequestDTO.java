// backend/src/main/java/com/example/app/modules/testcase/dto/CreateTestCaseRequestDTO.java
package com.example.app.modules.testcase.dto;

import com.example.app.modules.testcase.TestCasePriority;
import com.example.app.modules.testcase.TestCaseScenarioType;
import com.example.app.modules.testcase.TestCaseType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class CreateTestCaseRequestDTO {

    // WAJIB -- requirement #2: test case cuma bisa dibuat di dalam folder
    // yang sudah ada.
    @NotNull(message = "Folder wajib dipilih")
    private UUID folderId;

    @NotBlank(message = "Title wajib diisi")
    @Size(max = 255, message = "Title maksimal 255 karakter")
    private String title;

    @NotNull(message = "Priority wajib dipilih")
    private TestCasePriority priority;

    @NotNull(message = "Type wajib dipilih")
    private TestCaseType type;

    // Field opsional -- bisa dilengkapi belakangan setelah test case
    // dibuat. scenarioType fixed-choice: POSITIVE | NEGATIVE.
    private TestCaseScenarioType scenarioType;
    private String description;
    private String objective;
    private String precondition;
    private String testStep;
    private String expectedResult;
}
