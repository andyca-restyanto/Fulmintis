// backend/src/main/java/com/example/app/modules/testcase/exception/TestCaseNotFoundException.java
package com.example.app.modules.testcase.exception;

/**
 * Dilempar (404) kalau testCaseId TIDAK VALID untuk project ini -- bisa
 * karena: (a) memang tidak ada, (b) ada tapi milik project LAIN, ATAU
 * (c) requirement #4: test case-nya berstatus ARCHIVED dan yang request
 * BUKAN OWNER project (COLLABORATOR).
 * <p>
 * Sengaja SEMUA kasus di atas dibalas 404 yang sama (anti-enumeration,
 * pola yang sama dgn ProjectNotFoundException & TestFolderNotFoundException)
 * -- supaya COLLABORATOR tidak bisa "meraba-raba" apakah suatu test case
 * archived itu beneran ada atau tidak lewat perbedaan status code.
 */
public class TestCaseNotFoundException extends RuntimeException {
    public TestCaseNotFoundException() {
        super("Test case tidak ditemukan.");
    }
}
