// filepath: /backend/src/main/java/com/example/app/modules/admin/controller/AiLimitController.java
package com.example.app.modules.admin.controller;

import com.example.app.modules.admin.dto.AiLimitsDTO;
import com.example.app.modules.admin.dto.AiLimitsUpdateRequestDTO;
import com.example.app.modules.admin.service.AiLimitService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Menu admin "Batas AI". SecurityConfig: /api/admin/** hanya ROLE_ADMIN. */
@RestController
@RequestMapping("/api/admin/ai-limits")
@RequiredArgsConstructor
public class AiLimitController {

    private final AiLimitService aiLimitService;

    @GetMapping
    public AiLimitsDTO get() {
        return aiLimitService.get();
    }

    @PutMapping
    public AiLimitsDTO update(@Valid @RequestBody AiLimitsUpdateRequestDTO request, Authentication authentication) {
        return aiLimitService.update(authentication.getName(), request);
    }

    /** Kembalikan ke bawaan. ?key=FREE.AUTOMATION_DAILY_LIMIT (boleh berulang); tanpa key = semua. */
    @DeleteMapping
    public AiLimitsDTO reset(@RequestParam(name = "key", required = false) List<String> keys, Authentication authentication) {
        return aiLimitService.reset(authentication.getName(), keys);
    }
}
