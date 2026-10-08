// backend/src/main/java/com/example/app/modules/report/dto/ReportOverviewResponseDTO.java
package com.example.app.modules.report.dto;

import lombok.Builder;
import lombok.Getter;

/**
 * Response GET /api/projects/{projectId}/report/overview -- data tab
 * "Overview" di menu Report (requirement #1-3).
 * <p>
 * Semua angka dihitung dari SEMUA test run yang ada di project ini (tidak
 * difilter per test run/tanggal -- itu baru masuk scope tab "Run Details",
 * requirement #4, next phase).
 * <p>
 * Definisi tiap field:
 * <ul>
 *   <li>{@code totalTestRuns} -- jumlah test run di project ini, semua status.</li>
 *   <li>{@code totalRunningTestRuns} -- jumlah test run berstatus RUNNING
 *       (test run yang "sudah berjalan" / sedang dikerjakan; TIDAK termasuk
 *       PENDING yang belum disentuh sama sekali maupun FINISHED yang sudah
 *       selesai). Lihat javadoc {@link com.example.app.modules.testrun.TestRunStatus}.</li>
 *   <li>{@code totalPassed} / {@code totalFailed} / {@code totalBlocked} --
 *       jumlah HASIL EKSEKUSI test case (baris test_result) berstatus
 *       PASSED/FAILED/BLOCKED, digabung dari SEMUA test run di project ini.
 *       Test case yang sama tapi di-mapping ke 2 test run berbeda dihitung
 *       2x (1x per run), karena yang dihitung adalah hasil eksekusinya,
 *       bukan test case uniknya -- konsisten dgn cara TestRunExecuteView
 *       menghitung progress per test run.</li>
 *   <li>{@code passRatePercentage} -- (totalPassed / total test case) x 100,
 *       dibulatkan ke bilangan bulat terdekat. "Total test case" =
 *       {@code totalExecutions} (semua hasil eksekusi, semua status termasuk
 *       NEW/PENDING/BLOCKED). 0 kalau belum ada test case.</li>
 *   <li>{@code executionRatePercentage} -- ((totalPassed + totalFailed) /
 *       total test case) x 100, dibulatkan ke bilangan bulat terdekat:
 *       seberapa banyak test case yang sudah dieksekusi sampai ada hasil
 *       lulus/gagal. BLOCKED dan NEW/PENDING tidak dihitung sebagai
 *       "sudah dieksekusi". 0 kalau belum ada test case.</li>
 * </ul>
 */
@Getter
@Builder
public class ReportOverviewResponseDTO {
    private long totalTestRuns;
    private long totalRunningTestRuns;
    // Total baris hasil eksekusi (test_result) dari SEMUA test run di
    // project ini, semua status (NEW/PENDING/PASSED/FAILED/BLOCKED) --
    // "berapa banyak test case yang sudah di-mapping utk dieksekusi",
    // BUKAN jumlah test case unik (test case yg sama di 2 run beda
    // dihitung 2x, sama seperti totalPassed/totalFailed/totalBlocked).
    private long totalExecutions;
    private long totalPassed;
    private long totalFailed;
    private long totalBlocked;
    // NEW + PENDING digabung jadi 1 angka "belum ada kesimpulan" --
    // beda NEW vs PENDING (baru di-mapping vs sudah disentuh tapi belum
    // ada kesimpulan) belum perlu dibedakan di tab Overview.
    private long totalPending;
    private int passRatePercentage;
    private int executionRatePercentage;
}
