// filepath: /backend/src/main/java/com/example/app/modules/admin/controller/AdminAuthController.java
package com.example.app.modules.admin.controller;

import com.example.app.modules.admin.dto.AdminAcceptInvitationRequestDTO;
import com.example.app.modules.admin.dto.AdminInvitationValidationResponseDTO;
import com.example.app.modules.admin.dto.AdminRegisterRequestDTO;
import com.example.app.modules.admin.dto.AdminRegisterResponseDTO;
import com.example.app.modules.admin.dto.AdminRegistrationStatusResponseDTO;
import com.example.app.modules.admin.service.AdminAuthService;
import com.example.app.modules.auth.dto.LoginRequestDTO;
import com.example.app.modules.auth.dto.LoginResponseDTO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Endpoint PUBLIK autentikasi admin (didaftarkan permitAll di SecurityConfig). */
@RestController
@RequestMapping("/api/admin/auth")
@RequiredArgsConstructor
public class AdminAuthController {

    private final AdminAuthService adminAuthService;

    @GetMapping("/registration-status")
    public AdminRegistrationStatusResponseDTO registrationStatus() {
        return adminAuthService.getRegistrationStatus();
    }

    @PostMapping("/register")
    public ResponseEntity<AdminRegisterResponseDTO> register(@Valid @RequestBody AdminRegisterRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(adminAuthService.register(request));
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponseDTO> login(@Valid @RequestBody LoginRequestDTO request) {
        return ResponseEntity.ok(adminAuthService.login(request));
    }

    @GetMapping("/invitation/validate")
    public AdminInvitationValidationResponseDTO validateInvitation(@RequestParam("token") String token) {
        return adminAuthService.validateInvitation(token);
    }

    @PostMapping("/accept-invitation")
    public ResponseEntity<Map<String, String>> acceptInvitation(
            @Valid @RequestBody AdminAcceptInvitationRequestDTO request
    ) {
        adminAuthService.acceptInvitation(request);
        return ResponseEntity.ok(Map.of("message", "Password berhasil dibuat. Silakan login sebagai admin."));
    }
}
