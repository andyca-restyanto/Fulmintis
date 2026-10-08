// filepath: /backend/src/main/java/com/example/app/modules/testcase/dto/ImportColumnMappingDTO.java
package com.example.app.modules.testcase.dto;

import lombok.Builder;
import lombok.Getter;

/** Satu pemetaan kolom Excel -> field sistem. Match dgn ImportColumnMapping (FE). */
@Getter
@Builder
public class ImportColumnMappingDTO {
    // Teks header persis seperti tertulis di file Excel user.
    private String excelColumn;
    // Nama field sistem: title, priority, type, scenarioType, description,
    // objective, precondition, testStep, expectedResult.
    private String field;
}
