// backend/src/main/java/com/example/app/modules/testrun/TestRunStatus.java
package com.example.app.modules.testrun;

/**
 * Status lifecycle test run (requirement #4).
 * <p>
 * PENDING  -- default saat test run baru dibuat, belum ada test result yang
 *             di-update sama sekali.
 * RUNNING  -- otomatis di-set TestRunServiceImpl begitu ADA minimal 1 test
 *             result di dalam run ini yang statusnya diubah dari NEW (lihat
 *             updateTestResult()) -- bukan field yang di-set manual lewat API.
 * FINISHED -- otomatis di-set begitu SEMUA test result di dalam run ini
 *             berstatus PASSED (BUKAN sekadar "sudah dieksekusi/bukan
 *             NEW" lagi) -- kalau ada minimal 1 hasil selain PASSED (mis.
 *             FAILED, BLOCKED, PENDING, atau bahkan masih NEW), status
 *             TETAP RUNNING, tidak dianggap selesai.
 */
public enum TestRunStatus {
    PENDING,
    RUNNING,
    FINISHED
}
