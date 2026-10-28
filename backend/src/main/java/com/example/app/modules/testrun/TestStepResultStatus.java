// backend/src/main/java/com/example/app/modules/testrun/TestStepResultStatus.java
package com.example.app.modules.testrun;

/**
 * Hasil eksekusi 1 STEP di dalam 1 test result (tombol centang/silang per
 * step di halaman Execute). Urutan step mengikuti urutan baris test step di
 * test case terkait.
 * <p>
 * NEW    -- step belum ditandai apa-apa.
 * PASSED -- step berjalan sesuai expected result.
 * FAILED -- step gagal / tidak sesuai expected result.
 */
public enum TestStepResultStatus {
    NEW,
    PASSED,
    FAILED
}
