// filepath: /backend/src/main/java/com/example/app/modules/emailallowlist/controller/EmailAllowlistController.java
package com.example.app.modules.emailallowlist.controller;

import com.example.app.modules.emailallowlist.dto.AddAllowedEmailRequestDTO;
import com.example.app.modules.emailallowlist.dto.AllowedEmailRuleDTO;
import com.example.app.modules.emailallowlist.dto.AllowlistResponseDTO;
import com.example.app.modules.emailallowlist.dto.AllowlistSettingsRequestDTO;
import com.example.app.modules.emailallowlist.service.EmailAllowlistService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Menu admin "Email Diizinkan". SecurityConfig: /api/admin/** hanya ROLE_ADMIN. */
@RestController
@RequestMapping("/api/admin/email-allowlist")
@RequiredArgsConstructor
public class EmailAllowlistController {

    private final EmailAllowlistService service;

    @GetMapping
    public AllowlistResponseDTO get() {
        return service.get();
    }

    @PostMapping
    public ResponseEntity<AllowedEmailRuleDTO> add(@Valid @RequestBody AddAllowedEmailRequestDTO request,
                                                   Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.add(authentication.getName(), request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> remove(@PathVariable Long id, Authentication authentication) {
        service.remove(authentication.getName(), id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/settings")
    public AllowlistResponseDTO setEnabled(@Valid @RequestBody AllowlistSettingsRequestDTO request,
                                           Authentication authentication) {
        return service.setEnabled(authentication.getName(), request.getEnabled());
    }
}
