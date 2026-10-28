// backend/src/main/java/com/example/app/modules/testcase/exception/TestCaseArchivedException.java
package com.example.app.modules.testcase.exception;

/**
 * Dilempar saat user coba EDIT test case yang statusnya sudah archived.
 * Bukan 404 (test case-nya beneran ada & requester punya akses lihat --
 * lolos getAccessibleTestCase()), tapi 409 karena state resource-nya
 * (sudah di-arsip) bentrok dengan operasi yang diminta (edit).
 */
public class TestCaseArchivedException extends RuntimeException {
    public TestCaseArchivedException() {
        super("Test case ini sudah diarsipkan dan tidak bisa diedit.");
    }
}
