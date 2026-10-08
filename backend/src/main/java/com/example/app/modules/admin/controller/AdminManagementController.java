// filepath: /backend/src/main/java/com/example/app/modules/admin/controller/AdminManagementController.java
package com.example.app.modules.admin.controller;

import com.example.app.modules.admin.dto.AdminInviteRequestDTO;
import com.example.app.modules.admin.dto.AdminInviteResponseDTO;
import com.example.app.modules.admin.dto.AdminProfileResponseDTO;
import com.example.app.modules.admin.service.AdminAuthService;
import com.example.app.modules.admin.service.AdminManagementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Endpoint admin yang sudah login. SecurityConfig: /api/admin/** hanya ROLE_ADMIN. */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminManagementController {

    private final AdminAuthService adminAuthService;
    private final AdminManagementService adminManagementService;

    @GetMapping("/me")
    public AdminProfileResponseDTO me(Authentication authentication) {
        return adminAuthService.getProfile(authentication.getName());
    }

    @PostMapping("/admins")
    public ResponseEntity<AdminInviteResponseDTO> invite(
            @Valid @RequestBody AdminInviteRequestDTO request,
            Authentication authentication
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(adminManagementService.invite(authentication.getName(), request));
    }
}
