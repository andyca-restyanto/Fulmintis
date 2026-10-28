// filepath: /backend/src/main/java/com/example/app/modules/dashboard/dto/HealthBreakdownDTO.java
package com.example.app.modules.dashboard.dto;

import lombok.Builder;
import lombok.Getter;

/**
 * Current Project Health -- dihitung SAMA dengan Report (tab Overview): setiap
 * HASIL EKSEKUSI dari semua test run di project, per status. Match dgn
 * HealthBreakdown (FE).
 * <ul>
 *   <li>Keempat angka sama persis dengan totalPassed / totalFailed /
 *       totalBlocked / totalPending di {@code GET /report/overview}; jumlahnya =
 *       totalExecutions di Report (BUKAN totalTestCases: test case yang sama di
 *       2 run dihitung 2x, dan test case yang belum masuk run tidak dihitung).</li>
 *   <li>{@code pending} = NEW + PENDING. Di dashboard label-nya "Not Run"
 *       (nama field di API tetap {@code pending} agar identik dengan Report).</li>
 * </ul>
 */
@Getter
@Builder
public class HealthBreakdownDTO {
    private long passed;
    private long failed;
    private long blocked;
    private long pending;
}
