// filepath: /backend/src/main/java/com/example/app/modules/testcase/dto/ImportIssueDTO.java
package com.example.app.modules.testcase.dto;

import lombok.Builder;
import lombok.Getter;

/** Satu error/peringatan import. Match dgn ImportIssue (FE). */
@Getter
@Builder
public class ImportIssueDTO {
    // Nomor baris DI EXCEL (header = baris 1) supaya user langsung menemukannya.
    private int row;
    // Nama kolom menurut template (mis. "Priority", "Test step").
    private String column;
    private String message;
}
