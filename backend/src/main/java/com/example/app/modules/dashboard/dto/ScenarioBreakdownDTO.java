// filepath: /backend/src/main/java/com/example/app/modules/dashboard/dto/ScenarioBreakdownDTO.java
package com.example.app.modules.dashboard.dto;

import lombok.Builder;
import lombok.Getter;

/** Jumlah test case AKTIF per tipe skenario. Match dgn ScenarioBreakdown (FE). */
@Getter
@Builder
public class ScenarioBreakdownDTO {
    private long positive;
    private long negative;
}
