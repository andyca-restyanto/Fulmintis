// filepath: /backend/src/main/java/com/example/app/modules/testrun/service/ResultStatusTotals.java
package com.example.app.modules.testrun.service;

import com.example.app.modules.testrun.TestResultStatus;
import com.example.app.modules.testrun.repository.TestResultRepository;

import java.util.Collection;

/**
 * Satu-satunya tempat aturan "berapa hasil eksekusi per status" di project --
 * dipakai OLEH Report (tab Overview) DAN Dashboard (Current Project Health)
 * supaya angkanya tidak bisa berbeda antar halaman.
 * <p>
 * Aturan (sama persis dengan yang dulu ada di ReportServiceImpl):
 * <ul>
 *   <li>Yang dihitung adalah setiap HASIL EKSEKUSI (baris test_result) dari
 *       SEMUA test run di project. Test case yang sama di 3 run dihitung 3x.</li>
 *   <li>{@code pending} = NEW + PENDING digabung ("belum ada kesimpulan":
 *       baru di-mapping ke run, atau sudah disentuh tapi belum ada hasil).</li>
 *   <li>Test case yang belum dimasukkan ke test run mana pun TIDAK dihitung.</li>
 *   <li>Test case yang di-archive setelah masuk run tetap terhitung (query
 *       tidak memfilter status test case).</li>
 * </ul>
 */
public record ResultStatusTotals(long passed, long failed, long blocked, long pending) {

    /** Semua hasil eksekusi (= passed + failed + blocked + pending). */
    public long total() {
        return passed + failed + blocked + pending;
    }

    /**
     * @param rows hasil {@code TestResultRepository.countGroupedByStatus}. Status
     *             yang belum punya hasil sama sekali tidak ada barisnya -> dianggap 0.
     */
    public static ResultStatusTotals from(Collection<? extends TestResultRepository.StatusCount> rows) {
        long passed = 0;
        long failed = 0;
        long blocked = 0;
        long pending = 0;
        for (TestResultRepository.StatusCount row : rows) {
            long count = row.getTotal() == null ? 0L : row.getTotal();
            TestResultStatus status = row.getStatus();
            if (status == null) {
                continue;
            }
            switch (status) {
                case PASSED -> passed += count;
                case FAILED -> failed += count;
                case BLOCKED -> blocked += count;
                case NEW, PENDING -> pending += count;
            }
        }
        return new ResultStatusTotals(passed, failed, blocked, pending);
    }
}
