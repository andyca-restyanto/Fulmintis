// filepath: /backend/src/main/java/com/example/app/modules/dashboard/service/ProjectDashboardService.java
package com.example.app.modules.dashboard.service;

import com.example.app.modules.dashboard.dto.ProjectDashboardResponseDTO;

import java.util.UUID;

public interface ProjectDashboardService {

    /**
     * Semua angka dashboard satu project. Boleh diakses OWNER maupun
     * COLLABORATOR; bukan member / project tidak ada -> 404
     * (ProjectNotFoundException), sama seperti endpoint project lain.
     */
    ProjectDashboardResponseDTO getProjectDashboard(String userEmail, UUID projectId);
}
