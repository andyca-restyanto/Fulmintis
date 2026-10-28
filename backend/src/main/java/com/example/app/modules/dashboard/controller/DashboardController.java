// backend/src/main/java/com/example/app/modules/dashboard/controller/DashboardController.java
package com.example.app.modules.dashboard.controller;

import com.example.app.modules.dashboard.dto.DashboardSummaryDTO;
import com.example.app.modules.dashboard.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    /**
     * Endpoint ini TIDAK di-permitAll() di SecurityConfig, jadi wajib bawa
     * header "Authorization: Bearer <token>" yang valid. Request tanpa token
     * (atau token invalid/expired) otomatis ditolak 401 oleh
     * JwtAuthenticationEntryPoint -> ini yang menegakkan requirement
     * "user tidak bisa bypass buka dashboard cuma dgn paste URL".
     */
    @GetMapping("/summary")
    public DashboardSummaryDTO getSummary(Authentication authentication) {
        String email = authentication.getName(); // di-set oleh JwtAuthenticationFilter dari subject token
        return dashboardService.getSummary(email);
    }
}
