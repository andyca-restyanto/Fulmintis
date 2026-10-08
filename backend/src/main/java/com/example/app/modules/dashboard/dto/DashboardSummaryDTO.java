// backend/src/main/java/com/example/app/modules/dashboard/dto/DashboardSummaryDTO.java
package com.example.app.modules.dashboard.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class DashboardSummaryDTO {
    private String email;
    private String name; // nullable -- lihat User.name (requirement #4)
    private String message;

    // ---- Hasil in-memory join Data A (users, db "frontline") + Data B
    // (master_data, db "master_data") -- lihat DashboardServiceImpl ----
    private String userType;       // code, contoh: "FREE"
    private String userTypeLabel;  // label, contoh: "Free"
}
