// filepath: /backend/src/main/java/com/example/app/modules/dashboard/controller/ProjectDashboardController.java
package com.example.app.modules.dashboard.controller;

import com.example.app.modules.dashboard.dto.ProjectDashboardResponseDTO;
import com.example.app.modules.dashboard.service.ProjectDashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Dashboard di DALAM satu project (beda dgn {@link DashboardController} yang
 * ringkasan user di halaman daftar project). PROTECTED -- member project
 * (OWNER maupun COLLABORATOR); non-member dibalas 404.
 */
@RestController
@RequestMapping("/api/projects/{projectId}/dashboard")
@RequiredArgsConstructor
public class ProjectDashboardController {

    private final ProjectDashboardService projectDashboardService;

    @GetMapping
    public ProjectDashboardResponseDTO getProjectDashboard(
            @PathVariable UUID projectId, Authentication authentication
    ) {
        return projectDashboardService.getProjectDashboard(authentication.getName(), projectId);
    }
}
