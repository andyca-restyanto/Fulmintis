// filepath: /backend/src/main/java/com/example/app/modules/dashboard/dto/PriorityBreakdownDTO.java
package com.example.app.modules.dashboard.dto;

import lombok.Builder;
import lombok.Getter;

/** Jumlah test case AKTIF per prioritas. Match dgn PriorityBreakdown (FE). */
@Getter
@Builder
public class PriorityBreakdownDTO {
    private long highest;
    private long high;
    private long medium;
    private long low;
}
