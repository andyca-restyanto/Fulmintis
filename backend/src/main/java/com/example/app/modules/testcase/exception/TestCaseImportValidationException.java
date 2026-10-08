// filepath: /backend/src/main/java/com/example/app/modules/testcase/exception/TestCaseImportValidationException.java
package com.example.app.modules.testcase.exception;

import com.example.app.modules.testcase.dto.TestCaseImportResultDTO;

/**
 * Import (dryRun=false) ditolak karena ada baris bermasalah. Membawa hasil
 * lengkap (daftar error per baris) yang dikirim apa adanya sebagai body 400
 * -- tidak ada test case yang tersimpan.
 */
public class TestCaseImportValidationException extends RuntimeException {

    private final transient TestCaseImportResultDTO result;

    public TestCaseImportValidationException(TestCaseImportResultDTO result) {
        super("Import dibatalkan: ada baris yang tidak valid. Tidak ada test case yang disimpan.");
        this.result = result;
    }

    public TestCaseImportResultDTO getResult() {
        return result;
    }
}
