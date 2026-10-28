// backend/src/main/java/com/example/app/modules/testrun/exception/TestRunNotFoundException.java
package com.example.app.modules.testrun.exception;

/**
 * Dilempar (404) kalau testRunId TIDAK VALID utk project ini -- tidak ada,
 * atau ada tapi milik project LAIN (anti-enumeration, pola sama dgn
 * ProjectNotFoundException/TestCaseNotFoundException).
 */
public class TestRunNotFoundException extends RuntimeException {
    public TestRunNotFoundException() {
        super("Test run tidak ditemukan.");
    }
}
