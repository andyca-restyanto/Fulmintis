// filepath: /backend/src/main/java/com/example/app/modules/testcase/dto/TestCaseDraftDTO.java
package com.example.app.modules.testcase.dto;

import com.example.app.modules.testcase.TestCasePriority;
import com.example.app.modules.testcase.TestCaseScenarioType;
import com.example.app.modules.testcase.TestCaseType;

import java.util.List;

/**
 * Satu draft test case hasil AI (BELUM tersimpan). Match dgn TestCaseDraft (FE).
 * {@code duplicateOfExisting} dihitung saat dibaca: judul sama (tanpa membedakan huruf besar/kecil) dgn test case aktif di folder tujuan.
 */
public record TestCaseDraftDTO(
        String tempId,
        String title,
        TestCasePriority priority,
        TestCaseType type,
        TestCaseScenarioType scenarioType,
        String description,
        String objective,
        String precondition,
        List<TestCaseDraftStepDTO> steps,
        boolean duplicateOfExisting
) {

    public TestCaseDraftDTO withDuplicate(boolean duplicate) {
        return new TestCaseDraftDTO(tempId, title, priority, type, scenarioType, description, objective, precondition, steps, duplicate);
    }
}
