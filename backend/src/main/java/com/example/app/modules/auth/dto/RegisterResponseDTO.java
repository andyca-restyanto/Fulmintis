// backend/src/main/java/com/example/app/modules/auth/dto/RegisterResponseDTO.java
package com.example.app.modules.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Builder
@AllArgsConstructor
public class RegisterResponseDTO {
    private UUID id;
    private String email;
    private LocalDateTime createdAt;
    private String message;
}
