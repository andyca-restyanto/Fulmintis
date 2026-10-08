// filepath: /backend/src/main/java/com/example/app/modules/admin/dto/AdminInviteResponseDTO.java
package com.example.app.modules.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

/** Match: AdminInviteResponse (FE). */
@Getter
@Builder
@AllArgsConstructor
public class AdminInviteResponseDTO {
    private String email;
    private String name;
    private LocalDateTime expiresAt;
    private String message;
}
