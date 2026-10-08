// backend/src/main/java/com/example/app/modules/dashboard/service/DashboardService.java
package com.example.app.modules.dashboard.service;

import com.example.app.modules.dashboard.dto.DashboardSummaryDTO;

public interface DashboardService {
    DashboardSummaryDTO getSummary(String email);
}
