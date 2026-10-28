// filepath: /backend/src/main/java/com/example/app/modules/dashboard/service/DashboardCalculator.java
package com.example.app.modules.dashboard.service;

import com.example.app.modules.dashboard.dto.HealthBreakdownDTO;
import com.example.app.modules.dashboard.dto.TimelinePointDTO;
import com.example.app.modules.testrun.TestResultStatus;
import com.example.app.modules.testrun.TestRunStatus;
import com.example.app.modules.testrun.service.ResultStatusTotals;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Logika murni dashboard (tanpa DB/Spring) supaya bisa diuji langsung:
 * pembulatan progres, definisi run "aktif", health, dan pengisian hari kosong
 * pada timeline.
 */
public final class DashboardCalculator {

    private DashboardCalculator() {
    }

    /** Satu baris hasil query timeline: tanggal, status, jumlah. */
    public record TimelineEntry(LocalDate date, TestResultStatus status, long count) {
    }

    /** (passed + failed + blocked) / total * 100, dibulatkan; 0 kalau run kosong. */
    public static int progressPercentage(long executed, long total) {
        if (total <= 0) {
            return 0;
        }
        long percentage = Math.round(executed * 100.0 / total);
        return (int) Math.max(0, Math.min(100, percentage));
    }

    /**
     * Run "aktif" = belum FINISHED DAN belum 100% dieksekusi. Perbandingan
     * memakai angka asli (bukan persentase terbulat: 199/200 = 99,5% terbulat
     * jadi 100 tapi belum selesai). Run kosong (0 test case) yang belum FINISHED
     * tetap dianggap aktif -- masih terbuka untuk diisi.
     * <p>
     * Kenapa bukan cukup status != FINISHED: run yang semuanya sudah dieksekusi
     * tapi berisi FAILED tidak pernah menjadi FINISHED (FINISHED = semua PASSED)
     * dan akan menempel selamanya di daftar aktif.
     */
    public static boolean isActive(TestRunStatus status, long executed, long total) {
        if (status == TestRunStatus.FINISHED) {
            return false;
        }
        boolean fullyExecuted = total > 0 && executed >= total;
        return !fullyExecuted;
    }

    /**
     * Health = hitungan hasil eksekusi per status yang SAMA dengan Report
     * ({@link ResultStatusTotals}). {@code pending} = NEW + PENDING; di layar
     * ditampilkan sebagai "Not Run". Jumlah keempat angka = total hasil eksekusi
     * (BUKAN jumlah test case: test case yang sama di beberapa run dihitung
     * per run, dan test case yang belum masuk run tidak dihitung).
     */
    public static HealthBreakdownDTO buildHealth(ResultStatusTotals totals) {
        return HealthBreakdownDTO.builder()
                .passed(totals.passed())
                .failed(totals.failed())
                .blocked(totals.blocked())
                .pending(totals.pending())
                .build();
    }

    /**
     * Timeline {@code days} hari berurutan dari yang terlama sampai {@code today},
     * hari tanpa eksekusi bernilai 0. Entri di luar rentang atau berstatus selain
     * PASSED/FAILED/BLOCKED diabaikan.
     */
    public static List<TimelinePointDTO> buildTimeline(LocalDate today, int days, List<TimelineEntry> entries) {
        Map<LocalDate, long[]> perDay = new HashMap<>(); // [passed, failed, blocked]
        for (TimelineEntry entry : entries) {
            int slot = switch (entry.status()) {
                case PASSED -> 0;
                case FAILED -> 1;
                case BLOCKED -> 2;
                default -> -1;
            };
            if (slot < 0) {
                continue;
            }
            perDay.computeIfAbsent(entry.date(), d -> new long[3])[slot] += entry.count();
        }

        List<TimelinePointDTO> timeline = new ArrayList<>(days);
        for (int i = days - 1; i >= 0; i--) {
            LocalDate date = today.minusDays(i);
            long[] counts = perDay.getOrDefault(date, new long[3]);
            timeline.add(TimelinePointDTO.builder()
                    .date(date)
                    .passed(counts[0])
                    .failed(counts[1])
                    .blocked(counts[2])
                    .build());
        }
        return timeline;
    }
}
