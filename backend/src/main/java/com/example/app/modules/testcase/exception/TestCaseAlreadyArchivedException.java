// backend/src/main/java/com/example/app/modules/testcase/exception/TestCaseAlreadyArchivedException.java
package com.example.app.modules.testcase.exception;

/**
 * Dilempar (409 CONFLICT) saat OWNER mencoba delete (archive) test case
 * yang statusnya SUDAH ARCHIVED sebelumnya -- mencegah archivedAt/archivedBy
 * ke-overwrite tanpa sengaja dari aksi delete yang di-klik dobel di FE.
 */
public class TestCaseAlreadyArchivedException extends RuntimeException {
    public TestCaseAlreadyArchivedException() {
        super("Test case ini sudah berstatus archived sebelumnya.");
    }
}
