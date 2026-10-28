// filepath: /backend/src/main/java/com/example/app/modules/admin/controller/AdminManagementController.java
package com.example.app.modules.admin.controller;

import com.example.app.modules.admin.dto.AdminInviteRequestDTO;
import com.example.app.modules.admin.dto.AdminInviteResponseDTO;
import com.example.app.modules.admin.dto.AdminListItemResponseDTO;
import com.example.app.modules.admin.dto.AdminListResponseDTO;
import com.example.app.modules.admin.dto.AdminProfileResponseDTO;
import com.example.app.modules.admin.service.AdminAuthService;
import com.example.app.modules.admin.service.AdminManagementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

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

    /** Satu halaman daftar admin (terbaru dulu). page mulai 0; size 1..50, default 10. */
    @GetMapping("/admins")
    public AdminListResponseDTO list(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "10") int size,
            Authentication authentication
    ) {
        return adminManagementService.listAdmins(authentication.getName(), page, size);
    }

    @PostMapping("/admins/{id}/deactivate")
    public AdminListItemResponseDTO deactivate(@PathVariable("id") UUID id, Authentication authentication) {
        return adminManagementService.deactivate(authentication.getName(), id);
    }

    @PostMapping("/admins/{id}/activate")
    public AdminListItemResponseDTO activate(@PathVariable("id") UUID id, Authentication authentication) {
        return adminManagementService.activate(authentication.getName(), id);
    }
}
