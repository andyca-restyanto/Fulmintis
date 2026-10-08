// filepath: /backend/src/main/java/com/example/app/modules/admin/dto/AdminRegisterResponseDTO.java
package com.example.app.modules.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

/** Match: AdminRegisterResponse (FE). */
@Getter
@Builder
@AllArgsConstructor
public class AdminRegisterResponseDTO {
    private UUID id;
    private String email;
    private String name;
    private LocalDateTime createdAt;
    private String message;
}
