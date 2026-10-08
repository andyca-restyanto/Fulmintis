// backend/src/main/java/com/example/app/modules/testrun/exception/TestResultNotFoundException.java
package com.example.app.modules.testrun.exception;

/**
 * Dilempar (404) kalau testResultId TIDAK VALID utk test run ini -- tidak
 * ada, atau ada tapi milik test run LAIN. Juga dilempar kalau evidence-nya
 * belum pernah di-upload sama sekali saat GET .../evidence.
 */
public class TestResultNotFoundException extends RuntimeException {
    public TestResultNotFoundException() {
        super("Test result tidak ditemukan.");
    }

    public TestResultNotFoundException(String message) {
        super(message);
    }
}
