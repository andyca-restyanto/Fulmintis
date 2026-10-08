// filepath: /backend/src/main/java/com/example/app/modules/admin/controller/UserManagementController.java
package com.example.app.modules.admin.controller;

import com.example.app.modules.admin.dto.UpdateUserTierRequestDTO;
import com.example.app.modules.admin.dto.UserListItemResponseDTO;
import com.example.app.modules.admin.dto.UserListResponseDTO;
import com.example.app.modules.admin.service.UserManagementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Menu User di dashboard admin. SecurityConfig: /api/admin/** hanya ROLE_ADMIN. */
@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
public class UserManagementController {

    private final UserManagementService userManagementService;

    /**
     * Daftar user non-admin (terbaru dulu). page mulai 0; size 1..50, default 10.
     * q = cari email/nama (opsional, maks 100 karakter); tier = FREE | VIP_MONTHLY | VIP_YEARLY (opsional).
     */
    @GetMapping
    public UserListResponseDTO list(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "10") int size,
            @RequestParam(name = "q", required = false) String q,
            @RequestParam(name = "tier", required = false) String tier
    ) {
        return userManagementService.listUsers(page, size, q, tier);
    }

    @PatchMapping("/{id}/tier")
    public UserListItemResponseDTO updateTier(
            @PathVariable("id") UUID id,
            @Valid @RequestBody UpdateUserTierRequestDTO request,
            Authentication authentication
    ) {
        return userManagementService.updateTier(authentication.getName(), id, request.getUserType());
    }
}
