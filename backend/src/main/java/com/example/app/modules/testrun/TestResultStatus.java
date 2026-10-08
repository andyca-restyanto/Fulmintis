// backend/src/main/java/com/example/app/modules/testrun/TestResultStatus.java
package com.example.app.modules.testrun;

/**
 * Status hasil eksekusi 1 test case di dalam 1 test run (requirement #5).
 * <p>
 * NEW     -- default begitu test case baru dipetakan/di-mapping ke test run
 *            (requirement #2), belum pernah dieksekusi/di-update sama sekali.
 * PENDING -- sudah mulai dikerjakan tapi belum ada kesimpulan (mis. masih
 *            nunggu env/data), beda dari NEW yang artinya "belum disentuh
 *            sama sekali".
 * PASSED / FAILED / BLOCKED -- hasil akhir eksekusi.
 */
public enum TestResultStatus {
    NEW,
    PASSED,
    FAILED,
    PENDING,
    BLOCKED
}
